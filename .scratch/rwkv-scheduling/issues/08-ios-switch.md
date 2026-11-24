# Ship the RWKV switch on iOS

Status: in-progress
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

## Answer

iOS `vendor/anki` is on the Engine fork; `scripts/build-core.sh` builds it, and the app builds for the simulator. Settings › Reviewer › Scheduling gains the RWKV section, the app downloads the weights itself (so the core's worker thread is never held), and the collection prepares after it opens. SettingsFeature tests pass; StudyFeature has 74 of 75 passing, the failure being a card-host font-loading timing test outside this change. The UI test runner timed out initialising accessibility on the loaded machine. Not done: the measurements on the owner's iPhone. On the iPhone 18 Pro simulator (iOS 27) the section shows in Chinese, turning the switch on reads the history with a progress bar and reaches Ready, and the snapshot `collection.rwkv` is written (`evidence/ios-settings-ready.png`). The StudyFeature font test fails the same way with these changes stashed, so it predates them. iPad was not checked.
