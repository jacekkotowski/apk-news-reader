#!/bin/bash
# Runs inside the emulator job: installs the APK, starts it, looks at the result.
# Pictures: smoke.png (feed), smoke_open.png (a card opened), smoke_reader1.png to smoke_reader3.png (after "Read full article" on three cards),
# smoke2.png to smoke4.png (Sources tab). logcat.txt is the log.
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

# Try reader mode on the first three cards (Done after each, so the next card moves up).
scroll_top() { for i in 1 2 3 4; do adb shell input swipe $((W / 2)) $((H * 30 / 100)) $((W / 2)) $((H * 85 / 100)) 200; done; sleep 1; }
for n in 1 2 3; do
  scroll_top
  adb shell input tap $((W / 2)) $((H * 22 / 100))
  sleep 2
  [ $n = 1 ] && adb exec-out screencap -p > smoke_open.png
  P=""
  for i in 1 2 3; do
    P=$(find_text "Read full article")
    [ -n "$P" ] && break
    adb shell input swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 35 / 100)) 300
    sleep 1
  done
  if [ -z "$P" ]; then echo "Card $n: could not find the Read full article button"; continue; fi
  adb shell input tap $P
  sleep 15
  adb shell input swipe $((W / 2)) $((H * 35 / 100)) $((W / 2)) $((H * 75 / 100)) 300
  sleep 1
  adb exec-out screencap -p > smoke_reader$n.png
  D=""
  for i in 1 2 3; do
    D=$(find_text "Done")
    [ -n "$D" ] && break
    adb shell input swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 35 / 100)) 300
    sleep 1
  done
  [ -n "$D" ] && adb shell input tap $D
  sleep 1
done

# Sources tab with the status of every source.
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
