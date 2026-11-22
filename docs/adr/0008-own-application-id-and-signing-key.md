---
status: accepted
---

# Hayaki installs as io.github.amemiya02.hayaki and is signed with a private key

Hayaki first installed as `com.ichi2.anki.redesign`, a name from when the Fork was called the Redesign. It sits under AnkiDroid's `com.ichi2` namespace, which Hayaki does not own. Releases up to 0.2.1 were also signed with the fallback keystore committed to this repository, so anyone could sign an APK that Android would accept as an update to Hayaki. Hayaki now installs as `io.github.amemiya02.hayaki`, a name derived from the maintainer's GitHub account, and its releases are signed with a private key kept outside the repository.

## Consequences

- Android treats the new application ID as a different app. Existing installs of `com.ichi2.anki.redesign` do not upgrade in place: a learner exports a collection package from the old app and imports it into the new one. The two can stay installed side by side until the import is done.
- The application ID is set once in `ForkIdentity` (buildSrc). The public API's authority and permission follow it. Debug builds append `.debug`.
- A release build reads the private key from `KEYSTOREPATH`, `KEYALIAS`, `KEYSTOREPWD` and `KEYPWD`. Without them, Gradle falls back to the public test keystore. An APK signed that way cannot update a release install, so set the variables before building a release. Losing the private key means existing installs can never receive another update, so the keystore and its passwords are kept backed up.
