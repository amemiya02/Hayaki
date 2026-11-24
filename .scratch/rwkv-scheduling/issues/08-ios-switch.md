# Ship the RWKV switch on iOS

Status: ready-for-human
Type: HITL
Blocked by: 05
User stories: 1, 2, 3, 4, 5

## Parent

[Opt-in RWKV scheduling specification](../spec.md)

## What to build

Point Hayaki for iOS at the Engine fork, regenerate its calls, and add the switch and status row to Settings. The behaviour matches desktop. The human part is measuring on the owner's iPhone with the owner's collection: first replay time, memory while studying, and the cost of refreshing scores after an answer. The switch ships only if those numbers are acceptable to the owner.

## Acceptance criteria

- [ ] Every existing iOS test passes on the fork.
- [ ] The switch, status and progress work on iPhone and iPad.
- [ ] First replay time, peak memory and per-answer refresh time on the owner's iPhone are recorded in the ticket.
- [ ] The owner accepts the measured numbers, or the ticket records what must change.
