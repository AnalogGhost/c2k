#!/usr/bin/env bash
set -e

ANDROID_SDK="$HOME/Android/Sdk"
AVD_NAME="Pixel_10"
ANDROID_HOME_FLATPAK="$HOME/.var/app/com.google.AndroidStudio/config/.android"

echo "Starting emulator ($AVD_NAME)..."
ANDROID_AVD_HOME="$ANDROID_HOME_FLATPAK/avd" \
    "$ANDROID_SDK/emulator/emulator" -avd "$AVD_NAME" -no-snapshot-load &

echo "Waiting for device to boot..."
"$ANDROID_SDK/platform-tools/adb" wait-for-device
"$ANDROID_SDK/platform-tools/adb" shell 'while [[ "$(getprop sys.boot_completed)" != "1" ]]; do sleep 2; done'

echo "Building and installing app..."
./gradlew installFossDebug

echo "Done. Launch the app from the emulator."
