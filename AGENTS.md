# Repository guidelines

## Project structure

C2K is an offline Android running trainer built with Kotlin, Jetpack Compose, Room, and DataStore. Application code is under `app/src/main/kotlin/com/hackerapps/c2k/`, unit tests are under `app/src/test/`, and device tests are under `app/src/androidTest/`. Store metadata and release automation live in `fastlane/`.

## Development commands

- `./gradlew assembleFossDebug` builds the FOSS debug APK.
- `./gradlew test` runs unit tests.
- `bash run-emulator.sh` starts the configured emulator and installs a debug build.
- `bash docker-build.sh` performs the reproducible F-Droid release build.

Use JDK 21 and an Android SDK with API 36 and current build tools. Keep machine paths and signing credentials in the ignored `local.properties` or Fastlane environment files.

## Coding and testing

- Follow the existing package boundaries for data, engine, service, location, and Compose UI code.
- Keep workout timing and state transitions in the engine rather than UI components.
- Add unit tests for business logic and instrumentation tests for Room, services, permissions, or Compose behavior that depends on Android.
- Run the narrowest relevant tests during development, then `./gradlew test` and the applicable assemble task before committing.
- Preserve offline operation. Do not add network dependencies or permissions without explicit approval.

## Releases

Version changes require matching Fastlane changelogs. Release lanes create tags and external releases, so run them only when the user explicitly requests a release. Do not commit keystores, credentials, APKs, AABs, or generated build output.
