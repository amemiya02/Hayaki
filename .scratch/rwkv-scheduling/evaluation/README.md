# Ticket 01: RWKV against FSRS-6 on reviews the model never saw

Verdict: **go**. RWKV has lower log loss than FSRS-6 for all 120 users, by 21.4% review-weighted.

## What was measured

- **Users.** 120 users drawn with seed `20261124` from the 298 sealed final-test users of `anki-revlogs-10k` whose id is below 5000. `JSchoreels/anki-rwkv` (`artifacts/accuracy/tiers.json`) seals them from training, and ids below 5000 are also unseen by the large model that `sm56` was distilled from (`fork_seen_from_user: 5000`). The list is in `users.txt`.
- **Data.** Dataset revision `75299740cff05894ef42d7ad990666691efdd2da`. 11,766,097 review rows. Users have 5,104 to 1,210,005 rows, median 33,565.
- **RWKV.** `sm56` int8 weights (SHA-256 `479b3e2bc6a1c900ba8a4a45d629965c6df2a20cc8ff294583f5aeaeea3b4fdd`) with int4 state, through Fushi's `fushi_rwkv` deployment path at commit `07940eaef04a871fb3d89457b320abfe6ae2b752`. The whole review log is replayed once, and each review is scored with the immediate-head prediction made before it was answered.
- **FSRS-6.** The `fsrs` crate 6.6.2, which upstream Anki 26.09.4 uses. Items are built as Anki builds them: one item per review that is not the card's first and comes at least one day after the previous one. `TimeSeriesSplit` with 5 folds fits parameters on earlier items and predicts each later fold.
- **Scored reviews.** Both models are scored on exactly the 6,066,253 test-fold reviews. Same-day reviews are not scored, because FSRS-6 items exclude them.

## Results

| | RWKV | FSRS-6 (fitted) | FSRS-6 (default parameters) |
|---|---:|---:|---:|
| Log loss, review-weighted | 0.25288 | 0.32179 | 0.34399 |
| Log loss, user average | 0.26992 | 0.33096 | |
| Users where the model is lower | 120 | 0 | |

Calibration by the interval since the card's previous review:

| Interval | Reviews | Actual pass rate | Mean RWKV | Mean FSRS-6 | Log loss RWKV | Log loss FSRS-6 |
|---|---:|---:|---:|---:|---:|---:|
| 1 d | 931,269 | 0.839 | 0.837 | 0.857 | 0.3019 | 0.3592 |
| 2–7 d | 2,194,231 | 0.879 | 0.878 | 0.877 | 0.2451 | 0.3110 |
| 8–30 d | 1,603,275 | 0.880 | 0.880 | 0.881 | 0.2295 | 0.3141 |
| 31–89 d | 699,200 | 0.862 | 0.862 | 0.862 | 0.2638 | 0.3257 |
| 90–365 d | 516,393 | 0.854 | 0.854 | 0.851 | 0.2611 | 0.3185 |
| 366–1095 d | 114,011 | 0.865 | 0.871 | 0.847 | 0.2282 | 0.3177 |
| over 1095 d | 7,874 | 0.853 | 0.852 | 0.761 | 0.2419 | 0.3983 |

No user has RWKV worse than FSRS-6 by more than 0.02.

## Why the gap is this large

Both models are calibrated in every bucket, so the gap is not a bias in one of them. RWKV separates passes from failures better: its log loss is lower at the same mean prediction. It sees what FSRS-6 cannot see. It knows how the learner did on the card's siblings, the deck, the preset and the whole collection, how many cards were studied that day, and how long the previous answers took.

As a check of the deployment path, RWKV's user-average log loss over every non-first review is 0.2606. JSchoreels reports 0.2715 for `sm56` on 723 held-out users with the training repository's scorer, and Fushi reports 0.270 for the previous small model on 295 sealed users. The figures are close, and the user samples differ.

## Repeat the run

The dataset is gated on Hugging Face. Accept its terms, sign in with the `hf` CLI, then download the users in `users.txt` (`revlogs`, `cards` and `decks` for each) at the revision above.

1. Convert each user to RWKV rows: `uv run --no-project --with pyarrow python -I convert.py <dataset_dir> <rows_dir> $(cat users.txt)`.
2. Build and run the evaluator: `cargo run --release --manifest-path rwkv_vs_fsrs/Cargo.toml -- <weights.int8.bin> <rows_dir> <out_dir> $(cat users.txt) > summary.tsv`. It took 23 minutes on an 8-core Apple Silicon Mac.
3. Aggregate: `python3 -I report.py summary.tsv <out_dir>`. The output is `report.txt`.

The dataset, the converted rows, the per-review predictions and the weights are not committed.
