# SPDX-License-Identifier: GPL-3.0-or-later

"""Capture or inspect the Hayaki Android release performance baseline."""

import argparse
import csv
import gzip
import hashlib
import json
import math
import os
import platform
import re
import shlex
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


SCROLLS = 10
STUDY_CARDS = 5
FACE_PIXEL = {
    "question": bytes.fromhex("00ee99ff") * 16,
    "answer": bytes.fromhex("ee0099ff") * 16,
}
CARD_LABEL = re.compile(r"Browser fixture \d{5}")
CARD_COUNT = re.compile(r"\b100[ ,\u202f]?000\s+cards\b", re.IGNORECASE)


def frame_rows(raw):
    for block in raw.split("---PROFILEDATA---")[1::2]:
        for row in csv.DictReader(block.strip().splitlines()):
            if row["Flags"] != "0":
                continue
            start = int(row["IntendedVsync"])
            deadline = int(row["FrameDeadline"])
            end = int(row["FrameCompleted"])
            if start > 0 and deadline > start and start <= end < 2**63 - 1:
                yield (start, deadline, end)


def percentiles(values):
    ordered = sorted(values)
    if not ordered:
        return {}
    return {f"p{p}": round(ordered[math.ceil(len(ordered) * p / 100) - 1], 2) for p in (50, 95, 99)}


def frame_summary(frames, refresh_rate):
    interval = 1_000_000_000 / refresh_rate
    overruns = Counter(max(0, math.ceil((end - deadline) / interval)) for _, deadline, end in frames)
    return {
        "frames": len(frames),
        "missed_deadlines": len(frames) - overruns[0],
        "overrun_refresh_intervals": dict(sorted(overruns.items())),
        "render_ms": percentiles([(end - start) / 1_000_000 for start, _, end in frames]),
    }


def wait_until_ready(read):
    deadline = time.monotonic() + 30
    while True:
        try:
            return read()
        except ValueError:
            if time.monotonic() >= deadline:
                raise
            time.sleep(0.25)


class Device:
    def __init__(self, serial, package):
        self.adb = ["adb", "-s", serial]
        self.package = package
        self.path = "/data/local/tmp/hayaki-perf-window.xml"

    def shell(self, *args, timeout=90):
        return subprocess.check_output([*self.adb, "shell", shlex.join(args)], text=True, timeout=timeout).strip()

    def tap(self, point):
        self.shell("input", "tap", *map(str, point))

    def nodes(self):
        last_error = ""
        for _ in range(3):
            try:
                last_error = self.shell("uiautomator", "dump", self.path, timeout=15)
                if "dumped to" in last_error:
                    return ET.fromstring(self.shell("cat", self.path, timeout=5)).iter("node")
            except (subprocess.SubprocessError, ET.ParseError) as error:
                last_error = str(error)
        raise ValueError(f"Could not read the Android UI hierarchy: {last_error}")

    def control(self, label):
        for node in self.nodes():
            if node.get("package") != self.package:
                continue
            labels = (node.get("text", ""), node.get("content-desc", ""))
            if any(label.casefold() == line.casefold() for text in labels for line in text.splitlines()):
                left, top, right, bottom = map(int, re.findall(r"\d+", node.attrib["bounds"]))
                return ((left + right) // 2, (top + bottom) // 2)
        raise ValueError(f"'{label}' is missing from the app's UI hierarchy")

    def browsing(self, *, verify_count=False):
        labels = [
            node.get(key, "")
            for node in self.nodes() if node.get("package") == self.package
            for key in ("text", "content-desc")
        ]
        if verify_count and not any(CARD_COUNT.search(label) for label in labels):
            if "No cards match" in labels and "Clear" in labels:
                self.tap(self.control("Clear"))
            raise ValueError(f"Browse did not report 100,000 Cards: {labels}")
        rows = sorted(set(CARD_LABEL.findall("\n".join(labels))))
        if not rows:
            raise ValueError("Browse did not render any fixture rows")
        return rows

    def screenshot(self):
        capture = subprocess.check_output([*self.adb, "exec-out", "screencap | gzip -1"], timeout=15)
        image = gzip.decompress(capture)
        if len(image) < 16:
            raise ValueError("Android returned an incomplete screenshot")
        width, height, pixel_format = struct.unpack_from("<3I", image)
        if pixel_format != 1 or len(image) - width * height * 4 not in (12, 16):
            raise ValueError("Expected an Android RGBA8888 screenshot")
        return image

    def wait_for_face(self, face, action_start):
        last_negative_start = action_start
        capture_count = 0
        while time.monotonic() - action_start < 45:
            capture_start = time.monotonic()
            screen = self.screenshot()
            capture_end = time.monotonic()
            capture_count += 1
            if FACE_PIXEL[face] in screen and sum(marker in screen for marker in FACE_PIXEL.values()) == 1:
                return {
                    "visible_lower_ms": round((last_negative_start - action_start) * 1000, 2),
                    "visible_upper_ms": round((capture_end - action_start) * 1000, 2),
                    "screenshot_samples": capture_count,
                    "last_screenshot_ms": round((capture_end - capture_start) * 1000, 2),
                }
            last_negative_start = capture_start
        raise ValueError(f"'{face}' face did not become visible")

    def gfxinfo(self):
        raw = self.shell("dumpsys", "gfxinfo", self.package, "framestats")
        if "---PROFILEDATA---" not in raw:
            raise ValueError(f"Android returned no frame data: {raw}")
        return raw


def capture(args):
    device = Device(args.serial, args.package)
    apk_path = device.shell("pm", "path", args.package).removeprefix("package:")
    if "\n" in apk_path or not apk_path.endswith(".apk"):
        raise ValueError("Install a single release APK before running the capture")
    apk_info = device.shell("dumpsys", "package", args.package)
    if any("DEBUGGABLE" in line for line in apk_info.splitlines() if "flags=[" in line):
        raise ValueError("Install a release APK before running the capture")
    display = device.shell("dumpsys", "display")
    refresh = re.search(r"renderFrameRate\s+(\d+(?:\.\d+)?)", display)
    if not refresh:
        raise ValueError("Cannot determine the display's refresh rate")
    refresh_rate = round(float(refresh.group(1)), 2)
    component = device.shell("cmd", "package", "resolve-activity", "--brief", args.package).splitlines()[-1]
    if not component.startswith(f"{args.package}/"):
        raise ValueError(f"Could not resolve launcher activity: {component}")
    args.output.mkdir(parents=True, exist_ok=False)
    (args.output / "display.txt").write_text(display + "\n")
    compile_output = device.shell("cmd", "package", "compile", "-m", "speed", args.package, timeout=300)
    if compile_output != "Success":
        raise ValueError(f"ART speed compilation failed: {compile_output}")
    dexopt = device.shell("dumpsys", "package", "dexopt")
    (args.output / "dexopt.txt").write_text(dexopt + "\n")
    (args.output / "package.txt").write_text(apk_info + "\n")
    report = {
        "timestamp_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "revision": args.revision,
        "journey": args.journey,
        "apk_sha256": device.shell("sha256sum", apk_path).split()[0],
        "fixture_sha256": hashlib.sha256(args.fixture.read_bytes()).hexdigest(),
        "package": args.package,
        "device": device.shell("getprop", "ro.product.model"),
        "fingerprint": device.shell("getprop", "ro.build.fingerprint"),
        "avd": device.shell("getprop", "ro.boot.qemu.avd_name"),
        "display_size": device.shell("wm", "size"),
        "display_density": device.shell("wm", "density"),
        "refresh_rate_hz": refresh_rate,
        "font_scale": device.shell("settings", "get", "system", "font_scale"),
        "webview": device.shell("dumpsys", "webviewupdate"),
        "art_compilation": "speed",
        "renderer": next(
            (line for line in device.shell("dumpsys", "SurfaceFlinger").splitlines() if line.startswith("GLES:")),
            "unreported",
        ),
        "host": platform.platform(),
        "run_conditions": args.conditions,
        "samples": [],
    }
    width, height = map(int, report["display_size"].split()[-1].split("x"))
    report_path = args.output / "report.json"

    def save():
        temporary_report = report_path.with_suffix(".tmp")
        temporary_report.write_text(json.dumps(report, indent=2) + "\n")
        temporary_report.replace(report_path)

    save()
    collected = {}

    def sample(action, before=0, **metrics):
        raw = device.gfxinfo()
        index = len(report["samples"]) + 1
        name = f"{index:03d}-{action}.gfxinfo.txt"
        (args.output / name).write_text(raw + "\n")
        frames = [row for row in frame_rows(raw) if row[0] > before]
        if not frames:
            raise ValueError(f"'{action}' recorded no valid new app frames: {name}")
        collected.setdefault(action, []).extend(frames)
        report["samples"].append({
            "action": action, "frame_capture": name, "after_vsync_ns": before,
            "host_load_1m": round(os.getloadavg()[0], 2),
            **metrics, **frame_summary(frames, refresh_rate),
        })
        save()
        return max(row[0] for row in frames)

    def cold_launch(label, *, measured=False):
        device.shell("am", "force-stop", args.package)
        output = device.shell(
            "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-n", component,
        )
        (args.output / f"launch-{label}.txt").write_text(output + "\n")
        elapsed = re.search(r"^TotalTime: (\d+)$", output, re.MULTILINE)
        if measured and ("Status: ok" not in output or "LaunchState: COLD" not in output or not elapsed):
            raise ValueError(f"Android did not report a successful cold launch: {output}")
        wait_until_ready(lambda: device.control("Browser fixture"))
        return int(elapsed.group(1)) if elapsed else None

    def scroll():
        device.shell("input", "swipe", str(width // 2), str(height * 76 // 100),
                     str(width // 2), str(height * 33 // 100), "300")
        time.sleep(0.7)

    def tap_face(point, face):
        start = time.monotonic()
        device.tap(point)
        timing = device.wait_for_face(face, start)
        time.sleep(0.35)
        return timing

    # Start from the same Deck list for every launch and every group of study or Browse samples.
    cold_launch("warmup")
    iterations = range(1, args.runs + 1)
    for iteration in iterations if args.journey in ("all", "cold_start") else ():
        print(f"Cold start {iteration}/{args.runs}", flush=True)
        elapsed = cold_launch(iteration, measured=True)
        sample("cold_start", run=iteration, total_time_ms=elapsed)

    for iteration in iterations if args.journey in ("all", "study") else ():
        print(f"Study {iteration}/{args.runs}", flush=True)
        cold_launch(f"study-{iteration}")
        device.tap(device.control("Browser fixture"))
        device.wait_for_face("question", time.monotonic())
        show_answer = wait_until_ready(lambda: device.control("Show Answer"))
        tap_face(show_answer, "answer")
        easy = wait_until_ready(lambda: device.control("Easy"))
        tap_face(easy, "question")
        latest = max(row[0] for row in frame_rows(device.gfxinfo()))
        for card in range(1, STUDY_CARDS + 1):
            timing = tap_face(show_answer, "answer")
            latest = sample("reveal", latest, run=iteration, card=card, **timing)
            timing = tap_face(easy, "question")
            latest = sample("grade", latest, run=iteration, card=card, **timing)

    for iteration in iterations if args.journey in ("all", "browse") else ():
        print(f"Browse {iteration}/{args.runs}", flush=True)
        cold_launch(f"browse-{iteration}")
        device.tap(device.control("Browse"))
        initial = wait_until_ready(lambda: device.browsing(verify_count=True))
        for _ in range(SCROLLS):
            scroll()
        warmed = device.browsing()
        latest = max(row[0] for row in frame_rows(device.gfxinfo()))
        for swipe in range(1, SCROLLS + 1):
            scroll()
            latest = sample("scroll", latest, run=iteration, swipe=swipe)
        final = device.browsing()
        if initial == warmed or warmed == final:
            raise ValueError(f"Browse swipes did not advance through fixture rows: {initial}, {warmed}, {final}")
        report.setdefault("browse_progress", []).append({"run": iteration, "initial": initial, "warmed": warmed, "final": final})
        save()

    report["summary"] = {
        action: frame_summary(frames, refresh_rate) for action, frames in collected.items()
    }
    for action, fields in {
        "cold_start": ("total_time_ms",),
        "reveal": ("visible_lower_ms", "visible_upper_ms"),
        "grade": ("visible_lower_ms", "visible_upper_ms"),
    }.items():
        if action not in report["summary"]:
            continue
        for field in fields:
            report["summary"][action][field] = percentiles([
                sample[field] for sample in report["samples"] if sample["action"] == action
            ])
    save()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    analyze = commands.add_parser("frames", help="summarize raw Android gfxinfo framestats")
    analyze.add_argument("file", type=Path)
    analyze.add_argument("--after", type=int, default=0, help="exclusive IntendedVsync timestamp in ns")
    analyze.add_argument("--refresh-rate", type=float, required=True)
    run = commands.add_parser("capture", help="capture a run on a prepared Android device")
    run.add_argument("--serial", required=True)
    run.add_argument(
        "--package", default="io.github.amemiya02.hayaki.perf",
        help="package whose study progress will change on the disposable device",
    )
    run.add_argument("--revision", required=True, help="source commit of the installed APK")
    run.add_argument("--fixture", type=Path, required=True, help="imported Anki package (.apkg)")
    run.add_argument("--conditions", required=True, help="renderer, host activity and other runtime settings")
    run.add_argument("--output", type=Path, required=True, help="new directory for report and raw data")
    run.add_argument("--runs", type=int, default=3)
    run.add_argument("--journey", choices=("all", "cold_start", "study", "browse"), default="all")
    args = parser.parse_args()
    if args.command == "frames":
        if args.refresh_rate <= 0:
            parser.error("refresh rate must be positive")
        frames = [row for row in frame_rows(args.file.read_text()) if row[0] > args.after]
        print(json.dumps(frame_summary(frames, args.refresh_rate), indent=2))
    else:
        if args.runs not in (1, 2, 3):
            parser.error("choose 1–3 runs with the default daily limit")
        capture(args)


if __name__ == "__main__":
    main()
