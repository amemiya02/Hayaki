# Opt-in RWKV scheduling on Android, iOS and desktop

Status: in progress

Decision record: [ADR-0009](../../docs/adr/0009-opt-in-rwkv-scheduling.md).

## Problem Statement

FSRS predicts each Card from that Card's own history. It cannot see that a learner has been failing the Cards of one note or one Deck all week. The RWKV model that Fushi ships reads the whole review log, keeps state for each Card, note, Deck, preset and the collection, and predicts the chance of recall now. A Daily Reviewer who wants that prediction to decide what to study today has no way to get it in Hayaki. AnkiDroid forks that add it change the Deck options format and break when the preset is saved in Anki.

## Solution

Add one switch, **RWKV scheduling**, to Settings in all three apps. With the switch off, nothing changes. With the switch on, the Engine queues a review Card when RWKV predicts its chance of recall has fallen to the preset's desired retention, lowest first. New, learning and relearning Cards are queued as before. FSRS or SM-2 still writes every due date, so turning the switch off, or opening the collection in Anki, shows ordinary Upstream scheduling.

## User Stories

1. As a Daily Reviewer, I want to turn RWKV scheduling on with one switch, so that I can try it without changing any Deck options.
2. As a Daily Reviewer, I want to turn it off and get my usual FSRS queue back at once, so that trying it costs me nothing.
3. As a Daily Reviewer with three devices, I want the switch to follow my collection through sync, so that my phone and my desktop show the same queue.
4. As a Daily Reviewer, I want to keep studying while the model downloads and reads my history, so that turning it on never blocks a session.
5. As a Daily Reviewer, I want to see whether RWKV is ready, preparing or failed, so that I know which scheduler chose today's Cards.
6. As a Daily Reviewer, I want the due count on Decks to match the Cards the session shows, so that I can trust the number.
7. As a Daily Reviewer, I want my daily review limit and desired retention to apply under RWKV, so that I keep control of my workload.
8. As a Daily Reviewer who also opens Anki on a desktop, I want my collection to stay valid there, so that I can switch apps without repair.
9. As a Daily Reviewer, I want Undo to restore the RWKV prediction as well as the Card, so that an undone answer leaves no trace.
10. As a maintainer, I want one Engine patch shared by all three apps, so that an Upstream update is rebased once.

## Implementation Decisions

### Data

| Item | Where it lives | Synced |
|---|---|---|
| Switch | Collection config key `hayakiRwkvScheduling`, a JSON boolean, absent means off | Yes. Upstream sends the whole config map from the newer collection (`rslib/src/sync/collection/changes.rs`) and the receiver replaces its own (`rslib/src/storage/config/mod.rs`). The last device to change any config wins. |
| Target | The home Deck's effective desired retention (`Deck::effective_desired_retention` in `rslib/src/decks/mod.rs`), which honours a Deck override of the preset | Yes, as today |
| Due date, interval, memory state | Card and revlog rows, written by FSRS or SM-2 on every answer | Yes, as today |
| Model weights | App data folder, outside the profile. Pinned URL and SHA-256. | No |
| RWKV state snapshot | Profile folder, beside `collection.anki2`. Keyed by the weights fingerprint and the replayed-history fingerprint. | No |

Hayaki never adds fields to the `DeckConfig` protobuf. kuma3-anki-core adds fields between 67 and 85 for its RWKV options. Upstream stores deck options as a protobuf blob (`rslib/src/storage/deckconfig/mod.rs`), and prost drops fields it does not know. An unpatched app that saves a preset it received through a full sync or a package file erases them. Incremental sync carries deck options as JSON and keeps unknown keys, so the loss depends on the path the preset took.

### Engine patch

- Fork `ankitects/anki` at tag `26.09.4` into a Hayaki-owned repository. Each app points its Engine source at the fork: `vendor/anki` on desktop and iOS, and the `anki` submodule of a Hayaki fork of Anki-Android-Backend on Android.
- Add the `fushi_rwkv` crate as a path or git dependency of `rslib`, pinned to a Fushi commit. Use its Rust API, not its C ABI.
- Add a module `rslib/src/scheduler/rwkv/` that owns the RWKV runtime for the open collection. It loads weights, restores the snapshot, replays the review log, answers queries and writes the snapshot back.
- In `rslib/src/scheduler/queue/builder/gathering.rs`, `gather_cards` gathers review Cards through the RWKV rule when the switch is on and the runtime is ready. The rule applies only to review Cards in their home Deck. `gather_due_cards` also serves filtered Decks, and those keep Upstream's rule. Every other branch stays Upstream's.
- `rslib/src/decks/counts.rs` `due_counts` reports review counts by the same rule, so Decks and the session agree.
- In `rslib/src/ops.rs`, `requires_study_queue_rebuild` returns true when an `Op::UpdateConfig` changes `hayakiRwkvScheduling`. Upstream leaves the queue in place for that op. The Engine also clears the study queues when the RWKV status changes between preparing, ready and failed.
- After `answer_card` in `rslib/src/scheduler/answering/mod.rs` commits, append the new revlog row to the runtime. Undo of an answer calls `unappend` with the same row, and redo appends it again. When `unappend` returns 0, the next queue build replays in full through the history fingerprint. Fushi measured a full replay of 1.21 million reviews at 28 seconds for the small model on desktop.
- Add two methods in a new Hayaki service. `rslib/proto/rust.rs` sorts proto files by path and numbers services in that order, so the file must sort after `tags.proto`, for example `zz_hayaki.proto`. Follow Upstream's pattern of a `Backend…` service with its plain counterpart, which `proto_gen` requires. `RwkvStatus` returns off, preparing with progress, ready or failed with a message. `PrepareRwkv` downloads weights if needed and replays in the background. The switch itself uses Upstream's existing `SetConfigJson`, so no new method is needed for it.

### Queue rule

Port the rule from Fushi's RWKV-Instant queue. The README of `native/fushi_rwkv` (section 排队) gives the rule, and `packages/fushi_engine/lib/srs/srs_rwkv_scheduling.dart` gives the query time for Cards not answered today. kuma3-anki-core differs on that time. It counts days up to the next day and seconds up to now (`rslib/src/scheduler/rwkv.rs`).

- A review Card not answered today is due when its predicted recall at the end of today (one millisecond before the next day starts) is at or below the target.
- A review Card answered today is due again when its predicted recall now is at or below the target, at least 30 seconds have passed since that answer, and at least 5 other Cards have been answered since.
- Due review Cards are shown in ascending order of predicted recall. The Deck's review limit still applies.
- New, learning and relearning Cards follow Upstream.
- After every answer the queue is checked again, because the answer changes the predictions for its note, Deck, preset and collection. Keep scores in the runtime's score table (`scores_update`, `scores_lookup`). An answer invalidates the scores of the answered Card and its note siblings. Refresh the rest with `scores_requery` when the session starts and every 20 answers. Twenty is a starting value to tune in phase 1. kuma3-anki-core requeries after every answer by default. Fushi measured a query of 14,715 Cards at 215 ms on desktop. Phone cost is unmeasured.

Use the model's immediate head, not its curve head. Fushi measured both on 20 held-out users. For users who pass 95% or more of reviews, at intervals of 90 to 365 days, the curve head predicted 0.948, the immediate head 0.860, and the actual pass rate was 0.854.

### Replay

- Replay revlog rows in id order across the whole collection, with the exclusions Fushi documents in `native/fushi_rwkv/README.md` (section 回放哪些复习). These exclusions drop manual rows, filtered rows that do not reschedule, rows before a Forget, rows before a Card's latest learning sequence, and rows of deleted Cards.
- On each queue build, compare the stored history fingerprint with the collection. Append when the only change is newer revlog rows. Undo removes the newest journal entries. Synced rows older than the last replayed row replay in full, because the per-card bookkeeping would have to rewind with the journal (the first implementation chose this over a rewind). Replay in full when rows were removed, a Card was forgotten, or a Card moved to another Deck or preset. Port kuma3-anki-core `rslib/src/scheduler/rwkv/offline.rs` `history_plan` for the fingerprint.
- Any RWKV error sets the status to failed and the queue falls back to Upstream's rule for that build.

### Model

Use the small model `sm56` (331,511 parameters) with int4 state on all three apps. Fushi measured about 2.9 KB of state per Card with one Card per note, and about 50,000 replayed reviews per second on a 16-thread desktop. On 295 held-out users it scored a log loss of 0.270 against 0.282 for the large model (2,762,884 parameters, about 25 KB per Card at int8). Hayaki does not offer the large model.

Download `sm56/weights.int8.bin` from the author's pinned commit when the learner first turns the switch on, and check its size and SHA-256 (`479b3e2b…`, the full value is in Fushi's `srs_rwkv_models.dart`). Do not bundle it and do not host a copy. `WEIGHTS-NOTICE.md` in `JSchoreels/anki-rwkv` limits the weights to personal and research use until redistribution is settled.

### Per app

- **Desktop.** Point `vendor/anki` at the fork. Regenerate `src-tauri/codegen`. Add the switch and status row to the Scheduling settings page.
- **iOS.** Point `vendor/anki` at the fork. Regenerate the Swift calls from the core's descriptor set. Add the switch and status row to Settings.
- **Android.** Fork Anki-Android-Backend and point its `anki` submodule at the fork. Build with `local_backend=true` (`AnkiDroid/build.gradle.kts`), then publish a Hayaki Engine artifact so release builds do not need a local Rust checkout. Add the switch and status row to the Compose settings screen.

## Testing Decisions

- **Off means Upstream.** For fixture collections, the queue and the Deck counts with the switch off equal those of unpatched `26.09.4`. This test runs in the fork's CI on every rebase.
- **Prediction parity.** Replay Fushi's fixtures (`native/fushi_rwkv/tests/fixtures`) through the Engine module and match Fushi's golden values within its tolerances, 1e-5 for f32 and 1e-3 for int8.
- **Queue rule.** Seed a collection with scored Cards on both sides of the target and assert which Cards are queued, in what order, and how the guard treats a Card answered today.
- **Counts agree.** With the switch on and sibling burying off, the review count from `due_counts` equals the number of review Cards the session shows. Upstream counts already ignore burying.
- **Switch rebuilds the queue.** Build a queue, flip the switch, and assert that the next Card comes from the other rule without reopening the collection.
- **Filtered Decks.** With the switch on, a filtered Deck shows the same Cards as with the switch off.
- **Undo and redo.** Answer, undo, and assert that the next prediction for that Card is unchanged. Redo and assert that it matches the prediction after the first answer. Repeat after a snapshot restore, which clears the journal, to exercise the full replay.
- **Switch through sync.** Turn the switch on, on device A, and sync. On device B, change another config value, then sync. Record whether the switch survives. The result belongs in the user-facing help text.
- **Sync round trip.** Turn the switch on, sync to a self-hosted server, open the collection in unpatched Anki `26.09.4`, edit and save a preset, sync back, and assert that the switch is still on and that no Card or revlog row changed.
- **Cross-device agreement.** Replay the same collection on x86_64 desktop, an Apple Silicon Mac and an arm64 phone, and report the largest difference in predicted recall.

## Out of Scope

- FSRS-7 and the other kuma3-anki-core changes outside the RWKV queue.
- Workload simulation and future-due forecasts under RWKV. Statistics keep showing FSRS forecasts.
- Browser search `is:due` under RWKV. It keeps the stored due date in this release.
- Per-preset or per-Deck switches.
- Syncing RWKV state between devices.

## Phases

Each phase ends in a check. A phase starts only when the one before it passes. The tickets in [issues/](issues/) carry out these phases. Phase 0 is ticket 01, phase 1 is tickets 02 to 06, phase 2 is ticket 08, and phase 3 is tickets 07 and 09.

1. **Phase 0, measure.** Replay users of `open-spaced-repetition/anki-revlogs-10k` that the model never saw (the held-out cohort in `JSchoreels/anki-rwkv`) through `fushi_rwkv`. Compare log loss and calibration with FSRS-6 parameters fitted to each user on the same reviews. Stop the project if RWKV is not better.
2. **Phase 1, Engine and desktop.** Build the fork, the RWKV module, the queue rule, counts, undo and the two methods. Ship the switch on desktop. Check every test in Testing Decisions except cross-device agreement.
3. **Phase 2, iOS.** Point iOS at the fork and add the switch. Check replay time and memory on the owner's iPhone with the owner's collection.
4. **Phase 3, Android.** Build and publish the Engine artifact and add the switch. Check replay time and memory on the owner's Android phone, then run the cross-device agreement test.

## Further Notes

- **Weights terms.** The owner accepted the personal and research use terms of `WEIGHTS-NOTICE.md` on 2026-11-24. Revisit them if Hayaki is distributed beyond the owner. The upstream discussion is `open-spaced-repetition/srs-benchmark` issue 353.
- **Dataset access.** `anki-revlogs-10k` is gated on Hugging Face. Its terms allow individuals to use it for their own research. Someone must accept the terms with a Hugging Face account before phase 0 can download it.
- **Open decision.** Choose the GitHub owner and name for the Engine fork and the Anki-Android-Backend fork.
- **Unmeasured.** Replay time and memory on phones. Fushi measured on desktop only, and its README notes that phone speed differs.
- **Sources.** Fushi `native/fushi_rwkv/README.md` and `UPSTREAM.md` at commit `07940eaef04a871fb3d89457b320abfe6ae2b752`. kuma3-anki-core branch `rwkv` at `c639bcec696f2317f9bb9a736d433b9d6c0d9c56`, whose `.version` is `26.09.4+fsrs7`.
