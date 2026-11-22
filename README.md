# MrLudoProMax — Ludo Sovereign

A 100% offline Android board game: Ludo (4-player cross and 6-player hex boards) plus Snakes & Ladders.
No ads, no purchases, no `INTERNET` permission, every mode and theme unlocked.

## Modules

| Module | Type | What it does |
|---|---|---|
| `:core:engine` | pure Kotlin | Rules reducer for Ludo (Classic, Quick, Rush, Team Up) with the Double-Token **Fortress** rule, Snakes & Ladders (7 layouts), board geometry, JSON codec, replayable match log |
| `:core:ai` | pure Kotlin | Paranoid Expectimax with alpha-beta + Star1 pruning, additive Zobrist transposition table, iterative deepening under an 80 ms budget. Tiers: Casual, Grandmaster (fair dice), Unbeatable (dice oracle, **not fair dice**) |
| `:core:session` | pure Kotlin | Match orchestration: bot turns hidden behind the dice animation, auto-move, undo, Rush turn timer, per-action auto-save checkpoints, stats, offline XP/ranks |
| `:core:audio` | pure Kotlin | Procedural synthesiser: 6 sound packs × 18 events, rendered on device (no audio assets) |
| Android modules | Android | Room/DataStore, SoundPool + haptics, Compose UI (in progress) |

## Build & test

```bash
./gradlew test                    # engine, AI, session, synth tests (JDK 17+, no Android SDK needed)
./gradlew :core:ai:benchmark      # AI latency percentiles and head-to-head strength
```

Android modules are included automatically when `ANDROID_HOME` or `local.properties` `sdk.dir` is present.
