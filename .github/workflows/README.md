# Workflows

GitHub Actions is used to automate parts of our development workflow.

See https://docs.github.com/en/actions for more information.

## Quality Checks

Every push to `main` and every pull request runs these two workflows. Run the same commands locally before pushing.

| **Job** | **Command** | **Comments** |
|---|---|---|
| [Lint (Kotlin)](lint.yml) | `./gradlew lintAll ktlintCheck --daemon` and `tools/validate-version-code.sh` | Android lint, formatting and the versionCode check |
| [Unit Tests](tests_unit.yml) | `./gradlew jacocoUnitTestReport --daemon` | Unit tests for every module, on Ubuntu |

**Emulator Tests** run only when started by hand: see Release checks below.

## Release checks

On the Hayaki fork, manually run **Emulator Tests** with `release_device_only=true` against the candidate ref. Leave `release_artifacts_only` off for this run.

```sh
gh workflow run tests_emulator.yml --repo amemiya02/Hayaki --ref main -f release_device_only=true
```

The job installs the x86_64 Play release and its instrumentation APK on one Android emulator. It runs the [shared Core Loop scenario](../../AnkiDroid/src/androidTest/assets/hayaki-release/README.md) on an English phone and a wide display at 200% text in Simplified Chinese, Japanese, and Arabic. It also runs WebView tests for Card Template backgrounds, Anki Pages, and MathJax. Download the `hayaki-release-device-<commit>` artifact for screenshots, device and build metadata, and individual instrumentation logs.
