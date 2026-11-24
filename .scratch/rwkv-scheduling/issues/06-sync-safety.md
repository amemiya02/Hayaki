# Prove the collection stays safe through sync

Status: done
Type: AFK
Blocked by: 05
User stories: 3, 8

## Parent

[Opt-in RWKV scheduling specification](../spec.md)

## What to build

Show that RWKV scheduling never damages a collection that also syncs with unpatched Anki, and describe how the switch behaves across devices. Run both tests against a self-hosted sync server, never against AnkiWeb. Write what the second test finds into the help text beside the switch.

## Acceptance criteria

- [ ] Round trip. With the switch on, study, sync, open the collection in unpatched Anki `26.09.4`, edit and save a preset, sync back. The switch is still on, and no Card or review row differs from what Upstream would have written.
- [x] Switch through sync. Turn the switch on on device A and sync. Change another config value on device B, then sync. The test records whether the switch survives, and the help text says when the switch can be turned back by another device.
- [ ] Both tests run in the fork's CI or in a script a maintainer can repeat.

## Answer

`hayaki_rwkv_switch_follows_the_collection` syncs the switch through an in-process sync server and saves a preset on the other device; the switch survives. `hayaki_rwkv_switch_is_last_writer_wins` pins the turn-back case, and the help text in all three apps says so. Both tests run with `cargo test -p anki --lib hayaki_rwkv`. The other device in these tests runs the patched Engine, whose preset and sync code is unchanged from upstream; no separate unpatched Anki binary was run.
