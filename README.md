# C2K — Couch to 5K & 10K

Free, open-source running trainer for Android. No Google services. No tracking. No ads.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

## Features

- **C25K** — 9-week program to run 5K
- **C210K** — 14-week program to run 10K
- Audible voice prompts (Android built-in TTS — no internet needed)
- Background timer with lock-screen notification and pause/stop controls
- Optional GPS tracking (distance & pace) — works without it too
- Progress tracking across sessions
- Fully offline — no internet permission
- Compatible with GrapheneOS and any de-Googled Android device (no Google Play Services)

## Building

### Requirements

- JDK 17 or 21 (21 recommended — install via [SDKMAN](https://sdkman.io): `sdk install java 21.0.5-tem`)
- Android SDK with platform API 36 and build-tools 36+ (install via Android Studio or `sdkmanager`)

### Clone and build (debug)

```bash
git clone https://github.com/YOUR_USERNAME/c2k.git
cd c2k
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### Run unit tests

```bash
./gradlew test
```

### Install on device via ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Release builds

### Unsigned (for F-Droid submission)

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

F-Droid builds from source and applies their own signature — no keystore needed for submission.

### Signed (for personal sideloading)

1. Generate a keystore (one-time):

```bash
keytool -genkeypair -v \
  -keystore ~/c2k-release.jks \
  -alias c2k \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

2. Add to `local.properties` (this file is gitignored — never commit it):

```properties
sdk.dir=/home/YOUR_USER/Android/Sdk
storeFile=/home/YOUR_USER/c2k-release.jks
storePassword=YOUR_STORE_PASS
keyAlias=c2k
keyPassword=YOUR_KEY_PASS
```

3. Build:

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

## F-Droid submission

1. Fork the [fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository
2. Add `metadata/org.c2k.yml` describing the build
3. Tag a release: `git tag v1.0.0 && git push --tags`
4. Open a merge request to fdroiddata

## Permissions explained

| Permission | Why |
|---|---|
| `FOREGROUND_SERVICE` | Keeps the timer running with screen off |
| `FOREGROUND_SERVICE_HEALTH` | Required service type on Android 14+ |
| `WAKE_LOCK` | Prevents CPU sleep mid-workout |
| `POST_NOTIFICATIONS` | Shows workout notification with pause/stop controls |
| `ACCESS_FINE_LOCATION` | Optional GPS for distance & pace — you can skip this |
| `ACCESS_COARSE_LOCATION` | Coarse fallback for location permission dialog |

No `INTERNET` permission — the app is fully offline.

## Project structure

```
app/src/main/kotlin/org/c2k/
├── data/
│   ├── model/        # C25K & C210K program definitions, interval types
│   ├── db/           # Room database — sessions and GPS route points
│   ├── repository/   # SessionRepository
│   └── prefs/        # DataStore user preferences
├── engine/
│   ├── WorkoutEngine.kt   # Coroutine tick loop, state machine
│   ├── WorkoutState.kt    # Sealed class: Idle / Active / Paused / Completed
│   └── tts/               # TextToSpeech wrapper and announcements
├── service/
│   └── WorkoutService.kt  # ForegroundService, wake lock, notification
├── location/              # GPS abstraction (graceful fallback if unavailable)
└── ui/
    ├── screen/home/       # Program selection, recent history
    ├── screen/program/    # Week/day picker with completion badges
    ├── screen/workout/    # Live timer, interval ring, distance/pace
    └── screen/history/    # Past sessions list
```

## License

Copyright (C) 2026 Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

See [LICENSE](LICENSE) for the full text.
