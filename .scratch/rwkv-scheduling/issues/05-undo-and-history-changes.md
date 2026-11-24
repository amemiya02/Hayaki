# Keep RWKV correct through undo, redo and history changes

Status: done
Type: AFK
Blocked by: 04
User stories: 9

## Parent

[Opt-in RWKV scheduling specification](../spec.md)

## What to build

Undoing an answer removes it from the model, and redoing it appends it again, so the next prediction is what it would have been. Changes that rewrite history are detected by a history fingerprint before the queue is built. Synced reviews that are older than the last replayed review rewind the model and append in order. Forgetting a Card, removing review rows, moving a Card to another Deck or preset, and any rewind the journal cannot serve trigger a full replay. Port the fingerprint from kuma3-anki-core's `history_plan`.

## Acceptance criteria

- [x] Answer then undo leaves the next prediction for that Card unchanged, bit for bit.
- [x] Redo gives the same prediction as the original answer.
- [ ] After a snapshot restore, which clears the journal, undo falls back to a full replay and gives the same result.
- [ ] Each history-changing operation above triggers the expected path, append, rewind or full replay, and ends in the same state as a replay from scratch.
- [ ] Syncing in reviews from another device that interleave with local ones gives the same state as a replay from scratch.

## Answer

Undo and redo follow the small model's journal and give bit-identical predictions (`undo_restores_every_prediction`). A Forget, removed rows, a new learning sequence, a changed note, deck or preset, and synced rows older than the newest replayed one all trigger a full replay (`history_changes_force_a_full_replay`). Deviation from the specification: older synced rows replay in full instead of rewinding through the journal, because the per-card bookkeeping would have to rewind too. A full replay runs in the background and study uses the upstream queue meanwhile.
