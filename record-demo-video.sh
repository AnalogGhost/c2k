#!/usr/bin/env bash
set -e
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_SDK="${ANDROID_SDK:-$HOME/Android/Sdk}"
ADB="$ANDROID_SDK/platform-tools/adb"
OUT_FILE="$PROJECT_DIR/foreground-service-demo.mp4"
APP_PACKAGE="com.hackerapps.c2k.debug"
TEST_PACKAGE="com.hackerapps.c2k.debug.test"
TEST_CLASS="com.hackerapps.c2k.demo.ForegroundServiceDemoTest"

SERIAL="$("$ADB" devices | awk '/^emulator-/{print $1; exit}')"
if [ -z "$SERIAL" ]; then
  echo "No running emulator found — start one first (e.g. bash run-emulator.sh)."
  exit 1
fi
echo "Using device: $SERIAL"

# Build and install *before* recording starts, so the ~170s recording budget goes entirely to
# the actual scripted flow instead of being burned on compilation — an earlier version of this
# script started recording before the build, and the workout timer visibly stalled on screen
# during the build/install window (CPU contention from gradle + geo-fix spam + screenrecord
# all running at once), with the GPS phase never even showing a distance readout as a result.
echo "Building debug + androidTest APKs..."
cd "$PROJECT_DIR"
ANDROID_HOME="$ANDROID_SDK" ANDROID_SDK_ROOT="$ANDROID_SDK" \
  ./gradlew assembleFossDebug assembleFossDebugAndroidTest

echo "Installing..."
"$ADB" -s "$SERIAL" install -r -t app/build/outputs/apk/foss/debug/app-foss-debug.apk
"$ADB" -s "$SERIAL" install -r -t app/build/outputs/apk/androidTest/foss/debug/app-foss-debug-androidTest.apk

# Simulated running route near Lafayette, CO, ~3.4 m/s (a jogging pace) heading east. Started
# only once the app is installed and about to run, not during the build above.
BASE_LAT=39.9958
BASE_LON=-105.0900
(
  for i in $(seq 1 60); do
    LON=$(awk -v base="$BASE_LON" -v i="$i" 'BEGIN { printf "%.6f", base + i * 0.0001 }')
    "$ADB" -s "$SERIAL" emu geo fix "$LON" "$BASE_LAT" >/dev/null 2>&1 || true
    sleep 2.5
  done
) &
GEO_PID=$!

echo "Starting screen recording..."
"$ADB" -s "$SERIAL" shell rm -f /sdcard/demo.mp4
"$ADB" -s "$SERIAL" shell screenrecord --time-limit 170 /sdcard/demo.mp4 &
RECORD_PID=$!
sleep 2

echo "Running $TEST_CLASS directly via am instrument (no rebuild)..."
"$ADB" -s "$SERIAL" shell am instrument -w \
  -e class "$TEST_CLASS" \
  "$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"

echo "Test finished — stopping recording..."
kill "$GEO_PID" 2>/dev/null || true
"$ADB" -s "$SERIAL" shell pkill -SIGINT screenrecord || true
wait "$RECORD_PID" 2>/dev/null || true
sleep 2

echo "Pulling video..."
"$ADB" -s "$SERIAL" pull /sdcard/demo.mp4 "$OUT_FILE"
"$ADB" -s "$SERIAL" shell rm -f /sdcard/demo.mp4

echo "Done: $OUT_FILE"
echo "Review it, trim/upload to YouTube (unlisted) yourself, then paste the link into Play Console."
