# My Control Panel

<img src="docs/play-icon-512.png" width="120" align="right" alt="App icon: a cosy house with a sleeping calico cat, half day and half night"/>

A calm personal command centre for the Samsung Galaxy Tab S6 Lite and for phones (portrait or landscape). It shows the time, which phase of the day you're in, and what to do next, and it guides you through routines one step at a time.

**v1.1.1** — the cosy redesign

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

## v1.1.1
- The breather is now a little cluster of see-through soap bubbles (rainbow sheen, wobble) drifting slowly around the dashboard and setup; on busier screens they float in the bottom corner, and every scrolling screen leaves room so nothing stays underneath.
- Layout audit on phone and tablet, portrait and landscape, normal and large text: Focus fits any screen, the "Today's 3" links wrap instead of squeezing, the phone action bar stays on one line, messages show at the top so they never cover buttons.

## v1.1.0
- **Every phase of the day has its own world**, drawn from the app icon: a peach sunrise with the sun rising (Morning), a clear sky (Work), a lavender-and-coral sunset (Evening), a navy night with a crescent moon and twinkling stars (Bedtime), soft greens with drifting leaves (Day Off). Colours, buttons and the status bar follow the phase.
- **Mochi's cosy house** sits beside the clock: sage roof, lamp-lit room, books, a to-do board, a heart rising from the chimney, and Mochi on her cushion (sleepy with a nightcap at bedtime, focused, happy when you're caught up).
- **Splash screen**: the icon on cream, then a short hello with Mochi's house on today's sky.
- **Breather bubble** floats on every screen. One tap opens a calm screen: a big breathing bubble with Mochi (4 in · 4 hold · 4 out · 4 rest), optional rain, and the guided calm-down routine.
- **Setup is 5 screens** (hello + name, this device, what helps + music, your day, all set) and ends with where to change each thing later.
- **Rotation follows your device's auto-rotate setting.** Phones get calmer type sizes; phones on their side get a two-column layout.
- The release APK is checked in CI to be signed with the panel key before it's published.

## v1.0.0
- **Music, your way:** built-in calm sounds (rain, ocean, fan, deep/soft hush, white noise) generated on the device, free and offline, with a fade-out sleep timer; or loop any sound file; or any music app (YouTube Music, JioSaavn, Spotify free…) with play/pause/skip; or the full Spotify connection. Chosen per device, during setup or in Settings → Music.
- **Spotify Client ID is set in the app** (Settings → Music → Spotify, connected), with step-by-step instructions. It's optional; nothing is built in.
- **Mochi**, a little calico cat drawn in code, lives on the clock and changes with your day: a sun in the morning, focus brows, a nightcap and Zzz at bedtime, and happy eyes when you're caught up.
- Soft mode doodles behind the clock, a greeting with your name, a petal-and-heart shower when you finish something (off with Reduce motion), and friendlier empty states.
- New app icon (`docs/play-icon-512.png`).
- Phones and tablets, portrait and landscape; onboarding asks what you need, including music; choose anything from super simple to everything.

## Firebase setup
- `app/google-services.json` is the project config (not a secret).
- **Security rules:** paste `firestore.rules` into Firebase console → Firestore → Rules → Publish.

## Not yet
Weather, shared routines (spaces share tasks for now).

## Code map
- `app/src/main/java/app/mycontrolpanel/core/`: plain Kotlin with no Android code. It holds the models, scheduler, routine runner, the reducer (the single source of truth), defaults and the JSON codec. Tests are in `app/src/test/.../CoreSuite.kt`.
- `data/`: `Store` (dispatch → reduce → persist) and `Repository` (file storage)
- `ui/`: Jetpack Compose screens. They draw the current state and send every change back to the Store as an action.
- `system/`: routine reminder alarms, boot rescheduling
- `app/src/androidTest/`: emulator smoke tests (Quick Routine flow, back/exit, editors, persistence, reset, override)
