fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android screenshots

```sh
[bundle exec] fastlane android screenshots
```

Build the debug APK + test APK and capture store-listing screenshots in every locale

### android playstore

```sh
[bundle exec] fastlane android playstore
```

Build the Play release AAB and upload it plus store listing metadata to Google Play

Defaults to a dry run (validate_only) against the internal track — pass track:production

and validate_only:false to actually publish.

### android release

```sh
[bundle exec] fastlane android release
```

Tag, reproducibly build, sign, and publish a GitHub release for F-Droid.

Run this only after the version bump + changelog commit is already made and pushed-

worthy — see project release checklist. Pauses for confirmation before anything public

(push, GitHub release) happens; the docker build and signing run first so you can see

the real SHA-256 before deciding.

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
