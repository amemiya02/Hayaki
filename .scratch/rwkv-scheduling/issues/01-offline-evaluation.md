# Measure RWKV against FSRS on reviews the model never saw

Status: done
Type: AFK
Blocked by: none
User stories: none (this ticket gates the rest)

## Parent

[Opt-in RWKV scheduling specification](../spec.md)

## What to build

An offline evaluation that decides whether the project continues. Take the held-out user cohort that `JSchoreels/anki-rwkv` publishes for the `anki-revlogs-10k` dataset, so no user in the test was used to train the model. For each user, predict every non-first review two ways. RWKV predicts through Fushi's `fushi_rwkv` deployment path with the small model `sm56` and int4 state. FSRS predicts with FSRS-6 parameters fitted to that user, using the `fsrs` crate version the Engine pins and a time-series split, so FSRS never trains on a review it is then scored on. Report the results and a go or stop verdict.

## Acceptance criteria

- [x] The cohort, the dataset revision and the `sm56` weights SHA-256 are recorded, so the run can be repeated.
- [x] The report gives log loss for RWKV and FSRS, weighted by review and counted by user, with the number of users where each wins.
- [x] The report gives calibration (mean prediction against actual pass rate) in interval buckets, including 90 to 365 days and 1 to 3 years.
- [x] The report lists users where RWKV is clearly worse, with their review counts.
- [x] The report ends with go or stop. Stop if RWKV's review-weighted log loss is not lower than FSRS's.
- [x] The scripts and the report are committed beside the specification. The dataset and weights are not committed.

## Answer

Go. On 120 sealed users and 6,066,253 test reviews, RWKV's review-weighted log loss is 0.25288 against 0.32179 for fitted FSRS-6 (21.4% lower), and RWKV is lower for every user. Both models are calibrated in every interval bucket. The report, scripts and per-user summary are in [evaluation/](../evaluation/README.md).
