# Roadmap

Prioritised by impact vs. effort. Out-of-scope items are listed at the bottom with reasons, so if someone requests them they can see the thinking.

---

## High priority — high impact, low effort

- ~~**Pre-C25K starter plan** — a gentler lead-in with 30-second run intervals for absolute beginners who find Week 1 too hard. Pure program data, no new infrastructure.~~ *(done: e675a69)*
- ~~**Spoken motivational cues mid-interval** — encouragement mid-run ("you're doing great", halfway cues per interval), not just at transitions. TTS is already wired up.~~ *(done: a55cd8b)*
- **Customisable countdown warning intervals** — let users set their own warning thresholds instead of the fixed 10s and 5s.
- ~~**Treadmill mode** — disables GPS and switches pace display to effort cues. A simple flag in settings.~~ *(done: fa8d08c)*
- ~~**Haptic pattern variety** — different vibration patterns for run vs. walk vs. workout complete, rather than a single pulse on any change.~~ *(done: 9ccdd9c)*
- **Strength and rest day suggestions** — static content recommending cross-training on rest days. Fits naturally alongside the existing Guide screen.
- **Calories burned estimate** — local calculation based on user weight, distance, and pace. No external dependencies.

---

## Medium priority — high impact, moderate effort

- **Route map view in history** — render the GPX route already stored per session. Use an OSM-based library (no API key, no third-party account required) to keep it privacy-respecting.
- **Rest day reminders** — local push notifications on scheduled rest days. No server required.
- **Watch notifications (Wear OS)** — optimise the existing workout notification for Wear OS so interval state, time remaining, and pause/resume actions display cleanly on a paired watch. A meaningful step up from the phone notification with little overhead.
- **Weekly and monthly summary** — aggregate stats from existing history data (total distance, total time, sessions completed).
- **Health Connect integration** — write completed workouts to Android Health Connect. Data stays on device; no third-party service involved. Open source friendly.
- **Personal bests and milestones** — track fastest pace, longest run, total distance badges. All local. *(partially done: per-day best-time comparison already exists on the workout-complete screen — fastest pace, longest run, and total-distance badges are still open)*
- **Adaptive progression** — detect when a user fails to complete intervals multiple times and surface a suggestion to repeat the day or try the pre-C25K plan.
- **Announce pace or distance mid-interval** — periodic spoken updates during run intervals using GPS data already being collected.

---

## Lower priority — moderate impact, higher effort

- **Wear OS companion app** — a standalone Wear OS app for users who want to leave their phone behind. Substantially more work than watch notification optimisation (separate module, separate UI, data sync).
- **Live map during workout** — show current position on a map while running. Higher battery and complexity cost than post-run map view.
- **Custom workout builder** — let users define their own interval sequences. Requires new UI and data model.
- **Running cadence metronome** — an audio tick to help users hit a target step rate. Useful for injury prevention.
- **Home screen widget** — quick-start widget showing current week/day and a start button.
- **More translations** — German, French, and Brazilian Portuguese are already shipped (`values-de`, `values-fr`, `values-pt-rBR`), alongside Spanish and Galician. Further languages are community-driven; contributions welcome.
- **Streak and progress on history screen** — currently streak only shows on the home screen.

---

## Out of scope — and why

**Strava / third-party platform export**
Strava is a proprietary service that requires an account. It conflicts with the privacy-first, no-account-needed ethos of this app. The existing GPX export already lets anyone import their route to Strava or any other platform manually — that's the right boundary.

**Weather integration**
Would require sending the user's location to an external server before every workout. Not acceptable for a privacy-respecting app.

**Pre-recorded celebrity coach voices**
Licensing is complex and the audio binaries would bloat the APK. The system TTS engine already supports multiple voices and respects the user's language settings — the right solution for a FOSS app.

**Cloud sync / account system**
Adds server infrastructure, ongoing maintenance costs, and a privacy surface. All data lives on-device by design.

**Multiple user profiles**
Low demand relative to the complexity of reworking the data model. Families can use separate Android user accounts instead.
