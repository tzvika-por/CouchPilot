#!/bin/sh
# Supplemental native-window font test. Requires installed debug/test APKs on an emulator.
set -eu
adb_bin=${COUCHPILOT_ADB:-adb}
if [ "$("$adb_bin" shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]; then
    printf '%s\n' 'This check is restricted to an Android emulator.' >&2
    exit 1
fi
previous=$("$adb_bin" shell settings get system font_scale | tr -d '\r')
results=$(mktemp -d)
cleanup() {
    if [ "$previous" = null ]; then
        "$adb_bin" shell settings delete system font_scale >/dev/null
    else
        "$adb_bin" shell settings put system font_scale "$previous" >/dev/null
    fi
    rm -rf "$results"
}
trap cleanup EXIT
"$adb_bin" shell settings put system font_scale 2.0
runner=com.myremote.app.test/androidx.test.runner.AndroidJUnitRunner
"$adb_bin" shell am instrument -w -e class com.myremote.app.ui.ContextualRemoteTest "$runner" >"$results/functional.txt"
cat "$results/functional.txt"
grep -Fq 'OK (4 tests)' "$results/functional.txt"
classes='com.myremote.app.ui.CouchPilotScreenshotTest#capture[lg-setup-font-200],com.myremote.app.ui.CouchPilotScreenshotTest#capture[xiaomi-code-font-200],com.myremote.app.ui.CouchPilotScreenshotTest#capture[samsung-setup-font-200],com.myremote.app.ui.CouchPilotScreenshotTest#capture[hebrew-font-200],com.myremote.app.ui.CouchPilotScreenshotTest#capture[font-200],com.myremote.app.ui.CouchPilotScreenshotTest#capture[hebrew-settings]'
"$adb_bin" shell am instrument -w -e class "$classes" "$runner" >"$results/screenshots.txt"
cat "$results/screenshots.txt"
grep -Fq 'OK (6 tests)' "$results/screenshots.txt"
