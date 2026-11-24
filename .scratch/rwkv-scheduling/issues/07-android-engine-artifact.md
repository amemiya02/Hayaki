# Build the Android Engine from the fork

Status: in-progress
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
- [x] The steps to rebuild the artifact after an Engine rebase are written down.

## Answer

Anki-Android-Backend is cloned beside this repository (`../Anki-Android-Backend`, branch `hayaki-rwkv`, commit `2e0e059`) with its `anki` submodule on the Engine fork. `cargo run -p build_rust` builds it with `JAVA_HOME` set to JDK 21 (Gradle refuses Java 27) and NDK `29.0.14206865`; `local_backend=true` in `local.properties` makes this app use it, and `:AnkiDroid:assemblePlayDebug` succeeds. On this macOS 27 Mac the backend's host test library (`rsdroid-testing`) does not load ("mis-aligned LINKEDIT string pool", independent of RWKV), so the AAR was assembled with `-x test` and the app's Robolectric tests could not run here. Not done: publishing a Hayaki Engine artifact and switching release builds to it, which needs the owner's choice of where to publish. The debug build has only the arm64 `.so`; a release build needs `ALL_ARCHS=1 RELEASE=1`.
