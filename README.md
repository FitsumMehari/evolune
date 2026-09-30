# Evolune

**Every life builds a character. This one is yours.**

Evolune is a native, offline-first Android real-life RPG for personal development. It turns meaningful real-world effort into permanent character progression without pretending to score a person's worth, relationships, spirituality, health, or attractiveness.

The app is designed to the quality standard of a mature production application: compact daily use, deliberate game feedback, long-horizon progression, local persistence, accessibility, and no account or backend dependency.

## Product highlights

- Character creation with Male and Female presentation choices, alias, and life chapter.
- Twelve whole-life domains by default, with local calibration, custom domains, rename/enable/disable/reorder controls, and per-domain progression.
- Daily Missions, Side Quests, Main Quests, Campaigns, Trials, Bosses, and recurring Rituals.
- Difficulty-based XP, anti-farming reduction, permanent levels, Coins for user-defined real-world rewards, and Essence for cosmetics.
- Momentum instead of destructive all-or-nothing streaks.
- Ritual mastery stages: Seed, Developing, Stable, Strong, Mastered.
- Focus Mode with local session history.
- Achievements, titles, journey/timeline history, domain and skill progression, local statistics, weekly review, and daily advancement.
- Capacity-aware planning and a recovery experience after inactivity.
- Local notifications using WorkManager; no Firebase Cloud Messaging.
- Manual full-data export/restore using Android's document picker. Backups can be protected with a password using PBKDF2-HMAC-SHA256 + AES-GCM.
- Light, dark, and system themes; reduced-motion setting; scalable Compose UI; TalkBack-oriented semantics and 48dp-class controls.

## Screenshots

Screenshots are intentionally not fabricated. Build and run the app, then add real device/emulator screenshots here if desired.

## Architecture

Evolune uses a small, production-oriented unidirectional architecture:

- `ui/` — Jetpack Compose screens, reusable components, theme, and an `AndroidViewModel` exposing state/actions.
- `domain/` — data models plus pure progression, economy, Momentum, mastery, daily-advancement, and scheduling rules.
- `data/` — SQLite persistence, repository orchestration, DataStore preferences, and encrypted/plain local backup handling.
- `notifications/` — notification channels, WorkManager workers, and local scheduling.

All persistent mutations run on `Dispatchers.IO`. The UI consumes immutable `AppSnapshot` state through `StateFlow` and sends actions back through the view model.

### Persistence and migrations

Long-term structured data is stored in a versioned SQLite database with explicit migrations and foreign-key constraints. Preferences such as theme, feedback, presentation mode, notification settings, and return-state metadata are stored with AndroidX DataStore.

The database schema includes character/profile state, life domains, skills, quests, rituals and ritual logs, rewards and reward history, achievements, focus sessions, check-ins, seasons, cosmetics, and journey timeline events.

## Offline privacy model

**Your life stays on your device.**

Evolune has:

- no account system;
- no backend;
- no Firebase or Supabase;
- no remote analytics;
- no ads;
- no cloud sync;
- no required network access;
- no `android.permission.INTERNET` declaration.

The notification permission is requested only on Android 13+ when notifications are enabled. Local WorkManager scheduling survives normal process death and is restored by Android/WorkManager after reboot as appropriate.

Android system cloud backup is disabled for Evolune data so the explicit export/restore flow remains the controlled portability mechanism.

## Android requirements

- Native Android / Kotlin
- Jetpack Compose + Material 3
- `minSdk = 29` (Android 10)
- `targetSdk = 36`
- `compileSdk = 36`
- JDK 17
- Android Gradle Plugin 9.4.0
- Gradle 9.6.1 (CI regenerates and checksum-verifies the official wrapper before use)

## Build locally

Install JDK 17 and Android SDK Platform 36, then run:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Open the repository root in a current Android Studio version if you prefer IDE builds.

## Build with GitHub Actions

The repository includes `.github/workflows/android.yml` and requires no secrets for a debug build. On `push`, `pull_request`, or manual `workflow_dispatch`, it:

1. checks out the repository;
2. installs JDK 17;
3. verifies the Ubuntu 24.04 runner's preinstalled Android SDK Platform 36 and Build Tools 36.0.0;
4. installs Gradle 9.6.1 independently and regenerates the official Gradle wrapper with its pinned distribution checksum;
5. verifies Android dependency metadata;
6. compiles debug Kotlin sources;
7. runs JVM unit tests;
8. runs Android lint;
9. runs `assembleDebug`;
10. uploads `app-debug.apk` as the `Evolune-debug-apk` workflow artifact.

After pushing this project to your own GitHub repository, open **Actions → Evolune Android CI v6 → Run workflow** (or simply push a commit). When the run succeeds, download the APK from the workflow run's **Artifacts** section.

## Backup behavior

Use **Settings → Data** to export a backup through Android's Storage Access Framework. The user chooses the file location.

- Blank password: portable Evolune backup container containing the complete local database export and preferences.
- Password entered: the same payload is encrypted locally with AES-GCM using a PBKDF2-derived key and random salt/IV.
- Restore replaces Evolune's local data with the selected backup and restores preferences.

Keep the password safe: encrypted backups cannot be decrypted without it.

## Permissions

Declared permissions:

- `POST_NOTIFICATIONS` — Android 13+ notification permission for optional local reminders.

Not declared:

- `INTERNET`
- location
- contacts
- microphone
- camera
- advertising ID

## Project structure

```text
Evolune/
├── .github/workflows/android.yml
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/evolune/app/
│       │   │   ├── data/
│       │   │   ├── domain/
│       │   │   ├── notifications/
│       │   │   └── ui/
│       │   └── res/
│       └── test/java/com/evolune/app/domain/
├── gradle/wrapper/
├── gradlew
├── gradlew.bat
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── LICENSE
└── README.md
```

## Progression notes

XP is permanent and is never spent or removed. The level curve grows progressively rather than resetting seasonally. Coins are spendable only in the user's own real-world reward shop. Essence is intentionally rarer and limited to digital/cosmetic progression. Repeating the same low-value behavior reduces XP over time, and mastered rituals transition toward maintenance rewards.

The pure game rules are covered by JVM tests for XP, leveling, anti-farming, Momentum, recovery, mastery, daily advancement, coin protection, duplicate-completion rules, domain/skill XP deltas, multi-year progression, and scheduling calculations.

## License

MIT. See `LICENSE`.


## Dependency compatibility

The Android app intentionally pins its stable Compose/AndroidX runtime set to an API 36-compatible baseline. This avoids newer AndroidX artifacts that require API 37 while preserving Android 10+ runtime support.
