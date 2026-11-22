#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail
shopt -s nullglob

package=io.github.amemiya02.hayaki
runner=com.ichi2.anki.tests/com.ichi2.testutils.NewCollectionPathTestRunner
core_loop=com.ichi2.anki.HayakiReleaseCoreLoopTest
webview_tests='com.ichi2.anki.ReviewerFragmentTest#templatesKeepTheirCanvasAndPlainCardsFollowAppearanceWhenReviewerReopens,com.ichi2.anki.pages.AnkiPageAppearanceTest,com.ichi2.anki.CardViewerMathJaxTest'
evidence=hayaki-release-evidence
device_screens="/sdcard/Android/data/$package/files/hayaki-release-evidence"
fixture=AnkiDroid/src/androidTest/assets/hayaki-release/collection.colpkg
app_apks=(AnkiDroid/build/outputs/apk/play/release/*-x86_64-release.apk)
test_apks=(AnkiDroid/build/outputs/apk/androidTest/play/release/*.apk)
mkdir -p "$evidence"
trap 'adb logcat -d > "$evidence/logcat.txt" || true' EXIT

if (( ${#app_apks[@]} != 1 || ${#test_apks[@]} != 1 )); then
    echo "Expected one x86_64 Play release APK and one matching instrumentation APK."
    exit 1
fi
adb install -r -t "${app_apks[0]}"
adb install -r -t "${test_apks[0]}"
adb shell settings put global hide_error_dialogs 1
adb shell cmd uimode night no
printf '%s  %s\n' 63ba3eba14a180e13bcb50300009e0e9d2961e8cee93a193af40facf95bf1612 "$fixture" | sha256sum --check
bash .github/disable_art_metrics.sh

{
    git rev-parse HEAD
    sha256sum "$fixture" "${app_apks[@]}" "${test_apks[@]}"
    adb shell date -u
    adb shell getprop persist.sys.timezone
    adb shell getprop ro.build.fingerprint
    adb shell dumpsys package "$package" | grep -E 'version(Name|Code)='
    adb shell dumpsys webviewupdate
} > "$evidence/build-and-device.txt"

run_profile() {
    local profile=$1 locale=$2 font_scale=$3 tests=$4
    local directory="$evidence/$profile"
    local status=0
    mkdir -p "$directory"
    adb shell pm clear "$package" || return 1
    adb shell cmd locale set-app-locales "$package" --user 0 --locales "$locale" || return 1
    adb shell settings put system font_scale "$font_scale" || return 1
    {
        printf 'Locale: %s\n' "$locale"
        adb shell date -u
        adb shell wm size
        adb shell wm density
        adb shell settings get system font_scale
        adb shell settings get global animator_duration_scale
        adb shell cmd uimode night
        adb shell dumpsys display | grep -m 1 'DisplayInfo' || true
    } > "$directory/configuration.txt"
    adb shell am instrument -w -r \
        -e class "$tests" -e hayakiReleaseDevice true -e hayakiLocale "$locale" -e hayakiFontScale "$font_scale" "$runner" \
        | tee "$directory/instrumentation.txt" || status=$?
    adb exec-out screencap -p > "$directory/final-screen.png"
    if [[ "$tests" == "$core_loop" ]]; then
        adb pull "$device_screens/." "$directory" || status=1
    fi
    # am can return zero while the AndroidJUnitRunner reports a test failure.
    if (( status != 0 )) || ! grep -Eq '^OK \([1-9][0-9]* tests?\)' "$directory/instrumentation.txt"; then
        return 1
    fi
}

failed=0
# A cached AVD may retain the wide display override from its last run.
adb shell wm size 320x640
adb shell wm density 160
run_profile phone-en en-US 1.0 "$core_loop" || failed=1
run_profile phone-webview en-US 1.0 "$webview_tests" || failed=1
adb shell cmd uimode night yes
run_profile phone-appearance en-US 1.0 com.ichi2.anki.preferences.AppearanceSettingsDeviceTest || failed=1
adb shell cmd uimode night no

adb shell wm size 1600x1200
adb shell wm density 160
for locale in zh-CN ja-JP ar; do
    run_profile "wide-${locale}" "$locale" 2.0 "$core_loop" || failed=1
done
exit "$failed"
