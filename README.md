# My Control Panel

An always-on room control panel for the Samsung Galaxy Tab S6 Lite (landscape). It shows the time, which phase of the day you're in, and what to do next, and it guides you through routines one step at a time.

**v0.3.0** — core engine + ADHD-friendly redesign + accounts, sync & sharing

## Install on the tablet
1. On the tablet, open this repo's **Releases** page (`github.com/SakshiGupta0/MyControlPanel/releases`) and tap the latest `MyControlPanel-v…apk`.
2. If Android asks, allow **Install unknown apps** for your browser.
3. Open **My Control Panel**. New builds install over the old one and keep your settings, because every build is signed with the same key in `keystore/`.

Builds are published only after the core logic tests and the emulator smoke tests pass (see the Actions tab).

## What's in this build
- Dashboard: large clock, current mode, next change, featured routine, Quick Routines, water and meals, and mode-specific widgets
- Modes: five defaults that cover the full day, plus your own. Schedules can cross midnight. Overlaps are flagged and must be resolved: change the times, choose which mode wins, or make the other mode manual-only. You can switch modes by hand for a set time or until you switch back.
- Routines: 11 starters, a full editor (steps, timers, auto-advance, sound, mode assignment, Quick Routine, schedule), and a full-screen player with DONE, Back, and hold-to-exit
- Scheduled routines: a gentle card on the dashboard, plus an optional quiet notification when the panel isn't on screen
- Always-on: keeps the screen on, full screen, night dimming with a dark palette during Bedtime, and it picks up again after sleep
- Settings: Modes, Routines, Widgets, Tasks & Calendar, Music, Appearance, Device, Reset, About
- Everything is stored on the tablet only, in one JSON file written safely in a single step

## v0.2–v0.3 additions
- **Right now** card (one thing at a time), time-left bar, heads-up + soft chime before mode changes
- Built-in tasks: brain-dump inbox (voice too), today's three, pick-one-for-me, mode tags, focus timer
- Calendar from the tablet's synced calendars; music controls + what's playing
- Sign in with Google (Firebase), sync across devices, shared task spaces with invite codes, Google Tasks two-way sync
- New look: Fraunces + DM Sans (OFL, see `licenses/`), warm palette, per-mode colours

## Firebase setup
- `app/google-services.json` is the project config (not a secret).
- **Security rules:** paste `firestore.rules` into Firebase console → Firestore → Rules → Publish.

## Not yet
Weather, portrait layout, shared routines (spaces share tasks for now).

## Code map
- `app/src/main/java/app/mycontrolpanel/core/`: plain Kotlin with no Android code. It holds the models, scheduler, routine runner, the reducer (the single source of truth), defaults and the JSON codec. Tests are in `app/src/test/.../CoreSuite.kt`.
- `data/`: `Store` (dispatch → reduce → persist) and `Repository` (file storage)
- `ui/`: Jetpack Compose screens. They draw the current state and send every change back to the Store as an action.
- `system/`: routine reminder alarms, boot rescheduling
- `app/src/androidTest/`: emulator smoke tests (Quick Routine flow, back/exit, editors, persistence, reset, override)
