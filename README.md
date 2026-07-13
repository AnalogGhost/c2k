# C2K — Couch to 5K & 10K

Free, open-source running trainer for Android. No Google services. No tracking. No ads.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![CI](https://github.com/AnalogGhost/c2k/actions/workflows/ci.yml/badge.svg)](https://github.com/AnalogGhost/c2k/actions/workflows/ci.yml)

## Features

- Pre-C25K — 3-week starter for absolute beginners who find C25K Week 1 too hard
- C25K — 9-week program to run 5K
- C210K — 14-week program to run 10K
- Bridge to 10K — 6-week bridge for C25K graduates
- One Hour Runner — 13-week progression to 60 minutes continuous running
- 5K Improver — 8-week speed and stamina program for runners who can already complete 5K
- Audible voice prompts with mid-run encouragement (Android built-in TTS — no internet needed)
- Voice volume and speed controls
- Music ducking during voice announcements
- Background timer with lock-screen notification and pause/stop controls
- Optional GPS tracking (distance & pace) — works without it too
- Progress tracking across sessions
- Guide screen with FAQ and glossary
- Localised in English, Spanish, Galician, German, French, and Brazilian Portuguese
- Fully offline — no internet permission
- Compatible with GrapheneOS and any de-Googled Android device (no Google Play Services)

## Building

### Requirements

- JDK 21 (install via [SDKMAN](https://sdkman.io): `sdk install java 21.0.5-tem`)
- Android SDK with platform API 36 and build-tools 36+ (install via Android Studio or `sdkmanager`)

### Clone and build (debug)

```bash
git clone https://github.com/AnalogGhost/c2k.git
cd c2k
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleFossDebug
```

APK: `app/build/outputs/apk/foss/debug/app-foss-debug.apk`

### Run unit tests

```bash
./gradlew test
```

### Install on device via ADB

```bash
adb install app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

## Release builds

### Reproducible build (for F-Droid submission)

Use the Docker build script to produce an unsigned APK in the same environment F-Droid uses:

```bash
bash docker-build.sh
```

APK: `app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk`

F-Droid builds from source and applies their own signature. Build from the tagged commit before making any further commits.

CI runs this script twice on every push and fails if the two builds don't produce byte-identical output — see [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

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

3. Build and sign:

```bash
bash docker-build.sh

$HOME/Android/Sdk/build-tools/<version>/apksigner sign \
  --ks ~/c2k-release.jks \
  --ks-key-alias c2k \
  --ks-pass pass:YOUR_STORE_PASS \
  --key-pass pass:YOUR_KEY_PASS \
  --out app/build/outputs/apk/foss/release/app-foss-release-signed.apk \
  app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk
```

## F-Droid

C2K is available on F-Droid. The app metadata file is `com.hackerapps.c2k.yml`.

To release a new version:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`
2. Add a changelog at `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
3. Commit, tag, and push — F-Droid picks up new tags automatically:

```bash
git tag v1.x.x
git push origin main v1.x.x
```

See [FDROID_PUBLISHING.md](FDROID_PUBLISHING.md) for the full initial submission guide.

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
app/src/main/kotlin/com/hackerapps/c2k/
├── data/
│   ├── model/        # Program definitions, interval types, coaching tips
│   ├── db/           # Room database — sessions and GPS route points
│   ├── repository/   # SessionRepository
│   └── prefs/        # DataStore user preferences
├── engine/
│   ├── WorkoutEngine.kt   # Coroutine tick loop, state machine
│   ├── WorkoutState.kt    # Sealed class: Idle / Active / Paused / Completed
│   └── tts/               # TextToSpeech wrapper, audio focus, announcements
├── service/
│   └── WorkoutService.kt  # ForegroundService, wake lock, notification
├── location/              # GPS abstraction (graceful fallback if unavailable)
└── ui/
    ├── screen/home/         # Program selection, recent history, streak
    ├── screen/program/      # Week/day picker with completion badges
    ├── screen/workout/      # Live timer, interval ring, distance/pace
    ├── screen/history/      # Past sessions list with CSV/GPX export
    ├── screen/settings/     # Voice, GPS, vibration, display preferences
    ├── screen/guide/        # FAQ and glossary
    └── screen/contributors/ # Contributors credits
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to contribute, including translation credits.

## License

Copyright (C) 2026 Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

See [LICENSE](LICENSE) for the full text.
