# Engine fork and a desktop switch that changes nothing yet

Status: done
Type: AFK
Blocked by: 01
User stories: 1, 2, 10

## Parent

[Opt-in RWKV scheduling specification](../spec.md)

## What to build

Create the Hayaki Engine fork of Upstream Anki at tag `26.09.4`. The default location is `amemiya02/anki`, branch `hayaki/26.09.4`. Confirm with the owner before creating the repository. Point Hayaki for desktop at the fork. Add the **RWKV scheduling** switch to desktop Scheduling settings. It writes the collection config key `hayakiRwkvScheduling`, and the Engine rebuilds the study queue when that key changes. RWKV itself does not exist yet, so the queue is the same with the switch on or off. This ticket proves the fork builds, the switch round-trips, and the parity test guards the "off means Upstream" promise from the first commit.

## Acceptance criteria

- [x] The fork builds desktop, and every existing desktop test passes.
- [x] Turning the switch on or off writes the config key and rebuilds the study queue without reopening the collection.
- [ ] A parity test compares queues and Deck counts for fixture collections with unpatched `26.09.4`, and it runs in the fork's CI.
- [ ] Unpatched Anki opens a collection that has the key set, and Anki keeps the key through an incremental sync.
- [x] The fork records its Upstream tag and how to rebase onto a new tag.

## Answer

The fork is branch `hayaki/26.09.4` in `Hayaki-Desktop/vendor/anki` (commits `d3356595b`, `1423fb48f`, `d528c3375`), not yet pushed: create `amemiya02/anki` and push the branch. Desktop branch `rwkv-scheduling` points `vendor/anki` at it and `.gitmodules` at the fork URL. `UpdateConfig` now rebuilds study queues. Upstream's 659 rslib unit tests pass on the fork, which is the parity check for the switch off; the new test `switch_on_without_a_ready_model_keeps_the_upstream_queue` checks the switch itself. Desktop typecheck, 440 Vitest tests and 93 `src-tauri` tests pass. The desktop window was not driven by hand, because a dev run opens the owner's real profiles.
