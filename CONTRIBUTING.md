# Contributing to C2K

Thanks for taking the time to contribute! C2K is a small, focused FOSS app — contributions of all kinds are welcome.

## Ways to contribute

- **Translations** — add or improve string resources under `app/src/main/res/values-*/strings.xml` and matching Fastlane metadata under `fastlane/metadata/android/<locale>/`
- **Bug reports** — open an issue with steps to reproduce, Android version, and device/ROM
- **Bug fixes** — open a PR; link the issue it closes
- **New programs** — additional training plans go in `app/src/main/kotlin/org/c2k/data/model/`

## Building locally

See the [README](README.md) for build requirements and instructions.

## Pull request guidelines

- Keep PRs focused — one logical change per PR
- For translations, include both `strings.xml` and Fastlane store metadata if possible
- No new dependencies without discussion first
- The app must remain fully offline (`INTERNET` permission is intentionally absent)

## Contributors

| Contributor | Contribution |
|---|---|
| [xmgz](https://github.com/xmgz) | Spanish (es) and Galician (gl) translations |
