#!/usr/bin/env bash
# Records a short demo of the pedal screen (1080x2400, Pixel 6 emulator, app freshly started).
set -x
mkdir -p shots
adb shell am force-stop se.jonsolberg.iridium
adb shell am start -n se.jonsolberg.iridium/.MainActivity
sleep 4
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS
adb shell screenrecord --bit-rate 8000000 --time-limit 40 /sdcard/demo.mp4 &
REC=$!
sleep 2
turn() { adb shell input swipe "$1" "$2" "$1" $(( $2 - $3 )) "$4"; sleep 0.6; }
tap()  { adb shell input tap "$1" "$2"; sleep "${3:-0.8}"; }
turn 175 768 160 900        # DRIVE up
turn 906 768 -90 700        # LEVEL down
turn 418 974 140 800        # MIDDLE up
turn 661 974 110 800        # TREBLE up
turn 175 1056 -80 700       # BASS down
turn 906 1056 170 900       # ROOM up
tap 366 870                 # AMP -> punch
tap 713 870                 # CAB -> c
tap 367 763                 # AMP -> round
tap 713 816                 # CAB -> b
tap 906 1480 1.2            # ON off
tap 906 1480 1.2            # ON on
tap 175 1480 1.2            # FAV
tap 175 1480 1.2            # FAV
tap 427 396 1.2             # next preset
tap 427 396 1.2
tap 427 396 1.2
tap 120 396 1.2             # previous preset
tap 540 2232 2.5            # Presets tab
adb shell input swipe 540 1700 540 900 600; sleep 1.5
tap 180 2232 2              # back to Pedal
sleep 2
adb shell pkill -INT screenrecord; wait $REC; sleep 4
adb pull /sdcard/demo.mp4 shots/demo.mp4
