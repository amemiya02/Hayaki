# Build the Android Engine from the fork

Status: ready-for-agent
Type: AFK
Blocked by: 02
User stories: 10

## Parent

[Opt-in RWKV scheduling specification](../spec.md)

## What to build

Fork Anki-Android-Backend. The default location is `amemiya02/Anki-Android-Backend`. Confirm with the owner before creating the repository. Point its `anki` submodule at the Hayaki Engine fork, build it, and publish a Hayaki Engine artifact. Hayaki for Android uses that artifact in debug and release builds instead of the Maven artifact. The app adds no switch in this ticket, and its behaviour does not change. This work can run while desktop tickets 03 to 06 are in progress.

## Acceptance criteria

- [ ] A local build with `local_backend=true` and a build from the published artifact both pass the existing Android unit tests.
- [ ] Release builds resolve the Engine from the Hayaki artifact, and CI does not need a local Rust checkout.
- [ ] The artifact version records the Upstream Anki tag and the fork commit.
- [ ] Opening, studying and syncing an existing collection behave as with the Maven artifact.
- [ ] The steps to rebuild the artifact after an Engine rebase are written down.
