#!/bin/bash
# Runs inside the emulator job: installs the APK, starts it, looks at the result.
cd "$(dirname "$0")/.."
adb install -r StashReader.apk || exit 1
adb logcat -c
adb shell am start -n pl.stashreader/.MainActivity
sleep 30
adb exec-out screencap -p > smoke.png
adb logcat -d > logcat.txt
rc=0
if grep -q "FATAL EXCEPTION" logcat.txt; then echo "The app crashed (FATAL EXCEPTION in logcat)"; rc=1; fi
if [ -z "$(adb shell pidof pl.stashreader | tr -d '\r')" ]; then echo "The app process is gone"; rc=1; fi
exit $rc
