# Ship the RWKV switch on Android and check devices agree

Status: in-progress
Type: HITL
Blocked by: 05, 07
User stories: 1, 2, 3, 4, 5

## Parent

[Opt-in RWKV scheduling specification](../spec.md)

## What to build

Add the switch and status row to the Compose settings screen. The behaviour matches desktop. The owner measures on their Android phone with their collection, as on iOS. Then replay the same collection on an x86_64 desktop, an Apple Silicon Mac and an arm64 phone, and report how far the predictions differ.

## Acceptance criteria

- [ ] The switch, status and progress work on the phone, in Light, Dark and Black.
- [ ] First replay time, peak memory and per-answer refresh time on the owner's phone are recorded in the ticket.
- [ ] The largest difference in predicted recall across the three devices is recorded, with the number of Cards whose due decision differs today.
- [ ] The owner accepts the measured numbers, or the ticket records what must change.

## Answer

Studying › Scheduling gains the switch and a status row; on an arm64 emulator (API 36) turning it on downloaded the weights, checked them and reached Ready (`evidence/android-settings-ready.png`). ktlint passes. Not done: measurements on the owner's phone and the cross-device agreement run.
