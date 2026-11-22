#!/bin/bash
# Runs inside the emulator job: installs the APK, starts it, looks at the result.
cd "$(dirname "$0")/.."
adb install -r StashReader.apk || exit 1
adb logcat -c
adb shell am start -n pl.stashreader/.MainActivity
sleep 30
adb exec-out screencap -p > smoke.png
# second picture: the Sources tab, which shows the status or error of every source
SIZE=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
adb shell input tap $((W * 5 / 6)) $((H * 96 / 100))
sleep 3
adb exec-out screencap -p > smoke2.png
adb shell input swipe $((W / 2)) $((H * 80 / 100)) $((W / 2)) $((H * 15 / 100)) 400
sleep 1
adb exec-out screencap -p > smoke3.png
adb shell input swipe $((W / 2)) $((H * 80 / 100)) $((W / 2)) $((H * 15 / 100)) 400
sleep 1
adb exec-out screencap -p > smoke4.png
adb logcat -d > logcat.txt
rc=0
if grep -q "FATAL EXCEPTION" logcat.txt; then echo "The app crashed (FATAL EXCEPTION in logcat)"; rc=1; fi
if [ -z "$(adb shell pidof pl.stashreader | tr -d '\r')" ]; then echo "The app process is gone"; rc=1; fi
exit $rc
