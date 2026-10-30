#!/bin/bash
# Captures the smartwatch WALK alert for the store's watch slide, in each locale.
#
# Needs a booted Wear OS emulator (past its setup screen) with the FOSS debug app and its test APK
# installed:
#   ./gradlew assembleFossDebug assembleFossDebugAndroidTest
#   adb -s $SERIAL install -r -g app/build/outputs/apk/foss/debug/app-foss-debug.apk
#   adb -s $SERIAL install -r -g app/build/outputs/apk/androidTest/foss/debug/app-foss-debug-androidTest.apk
#
# WatchCueDemoTest posts the real cue notification on the watch itself; the phone-to-watch bridge
# drops notifications on emulators. The script records the screen and keeps the frame with Wear's
# full-screen alert, which comes before the notification card.
#
# Usage: tools/store-screenshots/capture-watch.sh [locale ...]   (SERIAL defaults to emulator-5556)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SERIAL="${SERIAL:-emulator-5556}"
PKG=com.hackerapps.c2k.debug
LOCALES=("$@")
[ ${#LOCALES[@]} -eq 0 ] && LOCALES=(en-US de-DE es-ES fr-FR gl-ES pt-BR ru-RU tr-TR)
adb() { "${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb" -s "$SERIAL" "$@"; }
# Mean brightness (0-255) of a crop of a frame, as a whole number.
luma() {
    ffmpeg -i "$1" -vf "crop=$2,signalstats,metadata=print:key=lavfi.signalstats.YAVG" -f null - 2>&1 |
        sed -n 's/.*YAVG=\([0-9]*\).*/\1/p' | head -1
}
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Records one cue in one locale and saves the alert frame. Fails when no alert showed, which the
# emulator does now and then.
capture() {
    local locale=$1 frame alert="" best=0 face top
    # Wear OS stops alerting for an app after a few cues in a row; clearing its data resets that.
    adb shell pm clear "$PKG" > /dev/null
    adb shell cmd locale set-app-locales "$PKG" --locales "$locale"
    adb shell input keyevent KEYCODE_WAKEUP
    sleep 2
    adb shell screenrecord --time-limit 40 /sdcard/cue.mp4 &
    sleep 1
    adb shell am instrument -w -e class com.hackerapps.c2k.demo.WatchCueDemoTest \
        "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > /dev/null
    wait
    adb pull /sdcard/cue.mp4 "$TMP/$locale.mp4" > /dev/null
    rm -f "$TMP"/f_*.png
    ffmpeg -loglevel error -i "$TMP/$locale.mp4" -vf fps=10 "$TMP/f_%03d.png"
    # The watch face fills the screen; the alert is mostly black, so its PNG is far smaller. Within
    # the first run of such frames the alert fades in and out; keep the brightest, fully drawn one.
    # The notification card that follows is just as small, but has a bright button across its lower
    # half, where the alert is black.
    # The watch face is the largest of the opening frames; the screen may still be waking in the first.
    face=$(stat -f %z $(ls "$TMP"/f_0[0-1]?.png) | sort -n | tail -1)
    for frame in "$TMP"/f_*.png; do
        if [ "$(stat -f %z "$frame")" -lt $((face * 2 / 5)) ]; then
            [ "$(luma "$frame" "iw:ih*0.2:0:ih*0.62")" -gt 40 ] && break
            top=$(luma "$frame" "iw:ih*0.5:0:0")
            [ "$top" -gt 0 ] && [ "$top" -gt "$best" ] && { alert="$frame"; best=$top; }
        elif [ -n "$alert" ]; then break; fi
    done
    [ -n "$alert" ] || return 1
    mkdir -p "$HERE/watch/$locale"
    cp "$alert" "$HERE/watch/$locale/watch.png"
    echo "$locale: $(basename "$alert")"
}

for locale in "${LOCALES[@]}"; do
    for attempt in 1 2 3; do
        capture "$locale" && break
        echo "$locale: no alert on attempt $attempt" >&2
        [ "$attempt" = 3 ] && exit 1
    done
done
adb shell cmd locale set-app-locales "$PKG" --locales ""
