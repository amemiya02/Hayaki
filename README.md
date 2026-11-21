<p align="center"><img src="AnkiDroid/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="Hayaki’s smiling folded-card icon"></p>

<h1 align="center">Hayaki for Android</h1>

<p align="center"><b>Hayaki = Hayaku + Anki</b></p>

<p align="center">An offline flashcard app for your Anki collection.</p>
<p align="center">
  <a href="https://github.com/amemiya02/Hayaki/releases/latest"><img src="https://img.shields.io/github/v/release/amemiya02/Hayaki?label=Hayaki&amp;color=67539a" alt="Latest release"></a>
  <a href="https://github.com/amemiya02/Hayaki/actions/workflows/tests_unit.yml?query=branch%3Amain+event%3Apush"><img src="https://github.com/amemiya02/Hayaki/actions/workflows/tests_unit.yml/badge.svg?branch=main&amp;event=push" alt="Unit tests"></a>
  <a href="https://github.com/amemiya02/Hayaki/actions/workflows/tests_emulator.yml?query=branch%3Amain+event%3Apush"><img src="https://github.com/amemiya02/Hayaki/actions/workflows/tests_emulator.yml/badge.svg?branch=main&amp;event=push" alt="Emulator tests"></a>
  <a href="https://github.com/amemiya02/Hayaki/actions/workflows/lint.yml?query=branch%3Amain+event%3Apush"><img src="https://github.com/amemiya02/Hayaki/actions/workflows/lint.yml/badge.svg?branch=main&amp;event=push" alt="Code quality checks"></a>
  <a href="#install-or-build"><img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 7.0 or later"></a>
  <a href="gradle/libs.versions.toml"><img src="https://img.shields.io/badge/target%20SDK-36-3DDC84?logo=android&amp;logoColor=white" alt="Targets Android SDK 36"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&amp;logoColor=white" alt="Written in Kotlin"></a>
  <a href="https://developer.android.com/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&amp;logoColor=white" alt="Interface built with Jetpack Compose"></a>
  <a href="COPYING"><img src="https://img.shields.io/github/license/amemiya02/Hayaki?color=blue" alt="License: GPL-3.0"></a>
</p>

<p align="center"><a href="README.zh-CN.md">简体中文</a> · <a href="https://github.com/amemiya02/Hayaki-iOS">Hayaki for iPhone and iPad</a> · <a href="docs/releases/0.2.0.md">0.2.0 release notes</a></p>

Open Hayaki to see what’s due across your decks. Pick one deck or tap **Study All** to work through them in sequence. Reveal the answer, then rate how well you remembered it. The Anki scheduler uses that rating to decide when the card comes back.

<p align="center"><img src="docs/images/en-showcase.webp" width="820" alt="Hayaki on Android showing today’s decks with 72 cards to study, an illustrated Japanese vocabulary answer with four ratings, and a year of review statistics in dark mode"></p>

<p align="center"><sub>Screenshots of the app with a sample collection and simulated study history.</sub></p>

## Study with your own cards

- **Bring a deck.** Import an Anki deck package (`.apkg`) to add its cards to your collection, or create a deck and write your first note. Restoring a full collection package (`.colpkg`) replaces the collection on this device.
- **Look up a card.** Browse lets you search your collection, filter by deck, card state, flag or tag, and edit the underlying note. Statistics shows your review history and upcoming workload. [See the card browser](docs/images/en-card-browser.png)
- **Read comfortably.** Choose from ten colour themes, independently of System, Light, Dark or Black appearance. Cards keep their authored formatting, images, audio and maths. On phones and wide screens you can reveal and rate with TalkBack and enlarged text.
- **Move between devices.** Study offline, then sync when you choose with AnkiWeb or a self-hosted Anki sync server.

## Install or build

Hayaki runs on **Android 7.0 or later** and can be installed alongside AnkiDroid. For a first install, download the universal APK. If you previously installed an architecture-specific APK, use the same variant to upgrade in place. [Download the universal APK](https://github.com/amemiya02/Hayaki/releases/download/v0.2.0/Hayaki-0.2.0-universal.apk) · [All variants, signing details and checksums](https://github.com/amemiya02/Hayaki/releases/tag/v0.2.0)

To build 0.2.0 yourself, install JDK 21 and Android SDK Platform 37. Point `ANDROID_HOME` at the SDK, or set `sdk.dir` in `local.properties`. With USB debugging enabled on a connected device, run

```sh
git clone --branch v0.2.0 --depth 1 https://github.com/amemiya02/Hayaki.git
cd Hayaki
./gradlew :AnkiDroid:installPlayDebug
```

To produce debug APKs without installing them, run `./gradlew :AnkiDroid:assemblePlayDebug`. The files are in `AnkiDroid/build/outputs/apk/play/debug/`.

## Project and license

Hayaki is independently maintained. The Android app builds on AnkiDroid and the Anki engine, with a Kotlin, Jetpack Compose and Material 3 interface. Android and iOS share their icon, themes and public version. [AnkiDroid](https://github.com/ankidroid/Anki-Android) · [Anki](https://github.com/ankitects/anki) · [Cross-platform maintenance](docs/adr/0007-shared-hayaki-product.md)

The Android app is licensed under GPL-3.0-or-later. Engine components, the public API and bundled assets carry their respective licenses. [License](COPYING) · [License notices](LICENSES/)
