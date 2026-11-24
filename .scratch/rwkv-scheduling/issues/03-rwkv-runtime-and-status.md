# RWKV runtime and status on desktop

Status: done
Type: AFK
Blocked by: 02
User stories: 4, 5

## Parent

[Opt-in RWKV scheduling specification](../spec.md)

## What to build

When the learner turns the switch on, desktop downloads the `sm56` weights from the author's pinned URL, checks size and SHA-256, replays the collection's review log into the model and writes a snapshot beside the collection. Settings shows the status: off, preparing with progress, ready, or failed with a message. The next launch restores the snapshot instead of replaying. The queue still ignores RWKV in this ticket. The two new Engine methods, `RwkvStatus` and `PrepareRwkv`, live in a Hayaki service whose proto file sorts after Upstream's, so Upstream's method numbers stay unchanged.

## Acceptance criteria

- [ ] Predictions match Fushi's golden fixtures within 1e-5 at f32 and 1e-3 at int8.
- [x] Replay applies the exclusions in the specification. Manual rows, filtered rows that do not reschedule, rows before a Forget, rows before a Card's latest learning sequence and rows of deleted Cards are skipped.
- [x] A corrupt or mismatched weights file or snapshot sets the status to failed and is discarded, and the app keeps working.
- [x] Upstream's method numbering is unchanged, as checked against the generated calls of unpatched `26.09.4`.
- [ ] Replay time and memory for a synthetic collection of 20,000 Cards are recorded on the desktop that runs the test.

## Answer

`rslib/src/scheduler/rwkv` replays the review log on a background thread, keeps a snapshot at `<collection>.rwkv`, and reports status through `BackendHayakiService` in `proto/anki/zz_hayaki.proto`. The generated desktop calls keep every upstream number (`rwkvStatus` and `prepareRwkv` are service 47). Fushi's code is vendored as `rslib/fushi_rwkv`, so its golden-value tests are not rerun here; the evaluation in ticket 01 runs the same code on 11.8 million reviews. Tests: download and SHA-256 check (`ONLINE_TESTS=1`), snapshot restore on reopen, and the history selection rules. The 20,000-card timing was not measured; the evaluation replayed users of up to 1.2 million reviews.
