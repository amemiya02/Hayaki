# SPDX-License-Identifier: GPL-3.0-or-later

"""Check the frame report against an Android framestats example."""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from run import Device


class ReportTest(unittest.TestCase):
    def test_ui_observer_recovers_after_a_killed_dump(self):
        dump = "UI hierarchy dumped to: /data/local/tmp/window.xml"
        snapshot = '<hierarchy><node text="Decks" /></hierarchy>'
        killed = subprocess.CalledProcessError(137, ["uiautomator", "dump"])
        with patch("subprocess.check_output", side_effect=[killed, dump, snapshot]):
            nodes = list(Device("emulator-5554", "io.github.amemiya02.hayaki.perf").nodes())
        self.assertEqual([node.get("text") for node in nodes], ["Decks"])

    def test_browse_rows_remain_measurable_after_the_result_count_scrolls_away(self):
        snapshot = '<hierarchy><node package="io.github.amemiya02.hayaki.perf" text="Browser fixture 00117" /></hierarchy>'
        with patch("subprocess.check_output", side_effect=["UI hierarchy dumped to: /data/local/tmp/window.xml", snapshot]):
            rows = Device("emulator-5554", "io.github.amemiya02.hayaki.perf").browsing()
        self.assertEqual(rows, ["Browser fixture 00117"])

    def test_browse_clears_an_empty_deck_filter_before_counting(self):
        snapshot = """<hierarchy>
            <node package="io.github.amemiya02.hayaki.perf" text="No cards match" />
            <node package="io.github.amemiya02.hayaki.perf" text="Clear" bounds="[20,20][40,40]" />
        </hierarchy>"""
        dump = "UI hierarchy dumped to: /data/local/tmp/window.xml"
        with patch("subprocess.check_output", side_effect=[dump, snapshot, dump, snapshot, ""]) as adb:
            with self.assertRaisesRegex(ValueError, "Browse did not report 100,000 Cards"):
                Device("emulator-5554", "io.github.amemiya02.hayaki.perf").browsing(verify_count=True)
        adb.assert_called_with(["adb", "-s", "emulator-5554", "shell", "input tap 30 30"], text=True, timeout=90)

    def test_reports_only_new_valid_frames_and_uses_androids_deadline(self):
        with tempfile.TemporaryDirectory() as directory:
            capture = Path(directory) / "capture.txt"
            capture.write_text("""---PROFILEDATA---
Flags,FrameTimelineVsyncId,IntendedVsync,FrameDeadline,FrameCompleted,
0,100,50000000,66000000,64000000,
0,101,66000000,81000000,82000000,
0,102,82000000,98000000,97000000,
0,103,98000000,114000000,144000000,
1,104,114000000,130000000,140000000,
0,105,130000000,146000000,9223372036854775807,
---PROFILEDATA---
""")
            result = subprocess.check_output([
                sys.executable, str(Path(__file__).with_name("run.py")),
                "frames", str(capture), "--after", "50000000", "--refresh-rate", "60",
            ])
            self.assertEqual(json.loads(result), {
                "frames": 3, "missed_deadlines": 2,
                "overrun_refresh_intervals": {"0": 1, "1": 1, "2": 1},
                "render_ms": {"p50": 16.0, "p95": 46.0, "p99": 46.0},
            })


if __name__ == "__main__":
    unittest.main()
