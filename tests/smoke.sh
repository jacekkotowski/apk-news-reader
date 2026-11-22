#!/bin/bash
# Runs inside the emulator job: installs the APK, starts it, looks at the result.
# Pictures: smoke.png (feed), smoke_open.png (a card opened), smoke_reader1.png (after "Read full article"),
# smoke_stash.png (after Keep), smoke_topics.png (Topics tab), smoke2.png to smoke4.png (Sources tab). logcat.txt is the log.
cd "$(dirname "$0")/.."
adb install -r StashReader.apk || exit 1
adb logcat -c
adb shell am start -n pl.stashreader/.MainActivity
sleep 30
adb exec-out screencap -p > smoke.png
SIZE=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}

# Centre of the on-screen element with this text, as "x y"; empty when it is not visible.
find_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb shell cat /sdcard/ui.xml | sed 's/></>\n</g' | grep "text=\"$1\"" | head -1 \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' \
    | awk -v h="$H" '{ cy=($2+$4)/2; if (cy>0 && cy<h) printf "%d %d", ($1+$3)/2, cy }'
}
# Tap the button with this text if it is on screen; say so when it is not.
tap_text() {
  P=$(find_text "$1")
  if [ -n "$P" ]; then adb shell input tap $P; return 0; fi
  echo "Could not find the button: $1"; return 1
}

# Open the first card (its buttons are at the top of the opened card) and read the full article.
adb shell input tap $((W / 2)) $((H * 22 / 100))
sleep 2
adb exec-out screencap -p > smoke_open.png
if tap_text "Read full article"; then sleep 20; fi
adb exec-out screencap -p > smoke_reader1.png
# Keep it.
tap_text "Keep"
sleep 2
adb exec-out screencap -p > smoke_kept.png
sleep 2

# Stash tab (the kept item), then the Topics tab, then the Sources tab with the status of every source.
adb shell input tap $((W * 3 / 8)) $((H * 96 / 100))
sleep 2
adb exec-out screencap -p > smoke_stash.png
adb shell input tap $((W * 5 / 8)) $((H * 96 / 100))
sleep 2
adb exec-out screencap -p > smoke_topics.png
adb shell input tap $((W * 7 / 8)) $((H * 96 / 100))
sleep 3
adb exec-out screencap -p > smoke2.png
adb shell input swipe $((W / 2)) $((H * 80 / 100)) $((W / 2)) $((H * 15 / 100)) 400
sleep 1
adb exec-out screencap -p > smoke3.png
adb shell input swipe $((W / 2)) $((H * 80 / 100)) $((W / 2)) $((H * 15 / 100)) 400
sleep 1
adb exec-out screencap -p > smoke4.png
# A link shared from another app lands in the Stash.
adb shell 'am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.SUBJECT "Smoke share test" --es android.intent.extra.TEXT "https://www.tidyverse.org/blog/" -n pl.stashreader/.MainActivity' 2>&1 | tr -d '' | head -5
sleep 6
adb exec-out screencap -p > smoke_share.png
adb logcat -d > logcat.txt
rc=0
if grep -q "FATAL EXCEPTION" logcat.txt; then echo "The app crashed (FATAL EXCEPTION in logcat)"; rc=1; fi
if [ -z "$(adb shell pidof pl.stashreader | tr -d '\r')" ]; then echo "The app process is gone"; rc=1; fi
exit $rc
