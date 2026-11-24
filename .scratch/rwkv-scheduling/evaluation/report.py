"""Aggregate rwkv_vs_fsrs output into the numbers the ticket 01 report needs.

Usage: python report.py <summary.tsv> <out_dir>
"""

import csv
import math
import sys
from pathlib import Path

BUCKETS = [(1, 1), (2, 7), (8, 30), (31, 89), (90, 365), (366, 1095), (1096, 10**9)]
MODELS = ["p_rwkv", "p_fsrs", "p_fsrs_default"]


def ll(p: float, y: int) -> float:
    p = min(max(p, 1e-6), 1 - 1e-6)
    return -math.log(p) if y else -math.log(1 - p)


def bucket_name(lo: int, hi: int) -> str:
    if lo == hi:
        return f"{lo} d"
    if hi >= 10**9:
        return f">{lo - 1} d"
    return f"{lo}-{hi} d"


def main() -> None:
    summary, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    users = list(csv.DictReader(summary.open(), delimiter="\t"))
    users = [u for u in users if int(u["scored"]) > 0]

    total = {m: 0.0 for m in MODELS}
    n_total = 0
    wins = {"rwkv": 0, "fsrs": 0}
    per_user = []
    buckets = {b: {"n": 0, "y": 0, **{m: 0.0 for m in MODELS}, **{m + "_ll": 0.0 for m in MODELS}} for b in BUCKETS}

    for u in users:
        rows = csv.DictReader((out_dir / f"{u['user']}.tsv").open(), delimiter="\t")
        sums = {m: 0.0 for m in MODELS}
        n = 0
        for r in rows:
            y = int(r["label"])
            d = int(r["delta_t"])
            n += 1
            for m in MODELS:
                p = float(r[m])
                sums[m] += ll(p, y)
            for b in BUCKETS:
                if b[0] <= d <= b[1]:
                    s = buckets[b]
                    s["n"] += 1
                    s["y"] += y
                    for m in MODELS:
                        p = float(r[m])
                        s[m] += p
                        s[m + "_ll"] += ll(p, y)
                    break
        for m in MODELS:
            total[m] += sums[m]
        n_total += n
        r_ll, f_ll = sums["p_rwkv"] / n, sums["p_fsrs"] / n
        wins["rwkv" if r_ll < f_ll else "fsrs"] += 1
        per_user.append((u["user"], n, r_ll, f_ll, int(u["rows"])))

    print(f"users scored: {len(users)}")
    print(f"test reviews: {n_total}")
    for m in MODELS:
        print(f"log loss, review-weighted, {m}: {total[m] / n_total:.5f}")
    for m in ["p_rwkv", "p_fsrs"]:
        idx = 2 if m == "p_rwkv" else 3
        print(f"log loss, user average, {m}: {sum(p[idx] for p in per_user) / len(per_user):.5f}")
    print(f"users where RWKV is lower: {wins['rwkv']}; FSRS-6 lower: {wins['fsrs']}")
    rel = (total["p_rwkv"] - total["p_fsrs"]) / total["p_fsrs"] * 100
    print(f"RWKV relative to FSRS-6 (review-weighted): {rel:+.2f}%")

    print("\ninterval\treviews\tactual\tmean RWKV\tmean FSRS-6\tLL RWKV\tLL FSRS-6")
    for b in BUCKETS:
        s = buckets[b]
        if s["n"] == 0:
            continue
        n = s["n"]
        print(
            f"{bucket_name(*b)}\t{n}\t{s['y'] / n:.3f}\t{s['p_rwkv'] / n:.3f}\t{s['p_fsrs'] / n:.3f}"
            f"\t{s['p_rwkv_ll'] / n:.4f}\t{s['p_fsrs_ll'] / n:.4f}"
        )

    print("\nusers where RWKV is worse by more than 0.02 (user, test reviews, LL RWKV, LL FSRS-6, total rows):")
    for user, n, r, f, rows in sorted(per_user, key=lambda p: p[3] - p[2]):
        if r - f > 0.02:
            print(f"{user}\t{n}\t{r:.4f}\t{f:.4f}\t{rows}")


if __name__ == "__main__":
    main()
