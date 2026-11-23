# MrLudoProMax — Ludo Ultra Pro-Max

A 100% offline Android board game: Ludo (4-player cross and 6-player hex boards) plus Snakes & Ladders.
No ads, no purchases, no `INTERNET` permission, every mode and theme unlocked.

## Modules

| Module | Type | What it does |
|---|---|---|
| `:core:engine` | pure Kotlin | Rules reducer for Ludo (Classic, Quick, Rush, Team Up) with the Double-Token **Fortress** rule, Snakes & Ladders (7 layouts), board geometry, JSON codec, replayable match log |
| `:core:ai` | pure Kotlin | Paranoid Expectimax with alpha-beta + Star1 pruning, additive Zobrist transposition table, iterative deepening under an 80 ms budget. Tiers: Casual, Grandmaster (fair dice), Unbeatable (dice oracle, **not fair dice**) |
| `:core:session` | pure Kotlin | Match orchestration: bot turns hidden behind the dice animation, auto-move, undo, Rush turn timer, per-action auto-save checkpoints, stats, offline XP/ranks |
| `:core:audio` | pure Kotlin | Procedural synthesiser: 6 sound packs × 18 events, rendered on device (no audio assets) |
| Android modules | Android | Room/DataStore, SoundPool + haptics, Compose UI: home, setup, Ludo + hex + Snakes & Ladders boards, audio studio, stats/replay, settings, 9 themes, 12 languages |

## Build & test

```bash
./gradlew test                    # engine, AI, session, synth tests (JDK 17+, no Android SDK needed)
./gradlew :core:ai:benchmark      # AI latency percentiles and head-to-head strength
```

Android modules are included automatically when `ANDROID_HOME` or `local.properties` `sdk.dir` is present
(needs `platforms;android-35` and `build-tools;35.0.0`).

```bash
./gradlew :app:assembleRelease        # R8-minified APK (~1.6 MB), debug-signed so it installs for testing
./gradlew :app:testDebugUnitTest      # Robolectric smoke test: walks every screen, lets bots play,
                                      # writes screenshots to app/build/screens/; ShowcaseTest renders
                                      # set-piece boards and the home screen in every theme there too
```

Each theme carries a `UiPalette` (backdrop gradient, cards, pills, call-to-action) separate from its
board colours; `ThemeContrastTest` fails the build if any theme's menu text or buttons lose contrast.

The release build is debug-signed; swap in a real keystore in `app/build.gradle.kts` before publishing.

CI (`.github/workflows/android.yml`) runs every test and builds the APK on pull requests and `main`.
Every push to `main` (a merged PR) publishes the APK as the GitHub Release `v<versionName>` (notes in `.github/release-notes.md`).
