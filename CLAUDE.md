# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

DayKit (`com.daykit`) — a single-module Android app: a local-first "kit" of privacy tools (App Lock, Key Store, Secure Notes, File Vault, Habits, Reminders, Expenses, Editor, DNS, Event Light). 100% Kotlin + Jetpack Compose, minSdk 31 / targetSdk 36. The only network use is user-initiated Google Drive backup of already-encrypted blobs.

## Build & test

There is no system JVM on this machine. **Every Gradle invocation must set JAVA_HOME first:**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # all JVM unit tests
./gradlew :app:testDebugUnitTest --tests "com.daykit.core.backup.BackupFileNamesTest"   # single test class
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (device/emulator needed)
./gradlew :app:assembleRelease        # minified + shrunk; needs signing config
```

Release signing reads `storeFile` / `storePassword` / `keyAlias` / `keyPassword` from `local.properties` (gitignored). If absent, the release build still assembles but unsigned — don't add fallback values.

Dependencies are declared exclusively through the version catalog at [gradle/libs.versions.toml](gradle/libs.versions.toml); never hardcode a coordinate in [app/build.gradle.kts](app/build.gradle.kts).

## Architecture

### Manual DI via AppContainer

There is no Hilt/Koin. [AppContainer.kt](app/src/main/java/com/daykit/AppContainer.kt) constructs every repository, cipher, and service client as `by lazy` properties; [DayKitApplication](app/src/main/java/com/daykit/DayKitApplication.kt) owns the single instance and warms up the DB + installed-app list on startup.

Anything outside a composable reaches it via `(application as DayKitApplication).container` (or `context.applicationContext as ...` in receivers/widgets). Composables receive `container: AppContainer` as a **parameter** threaded down from [DayKitNavHost](app/src/main/java/com/daykit/navigation/DayKitNavHost.kt). Adding a repository means adding a lazy property here — nothing else.

### No ViewModels

Screen state lives in `remember { mutableStateOf(...) }` inside the top-level `@Composable` of each screen, fed by `LaunchedEffect { repository.observeX().collect { ... } }`. Screens are large single files (`feature/<name>/ui/<Name>Screen.kt`, some 1000–2000 lines) holding state, sheets, and dialogs together. Follow that shape rather than introducing a ViewModel layer for one feature.

### Feature layout

```
core/{backup,data,designsystem,permissions,security,session,util}
feature/<name>/{data,domain,ui,service,notification,reminder}
navigation/{Routes,DayKitNavHost,RootScaffold}
```

`Routes` are flat, parameter-less strings; three bottom-nav tabs (Home/Today/Settings) plus `tool/*` and `settings/*` destinations. Nav transitions are deliberately `None`.

## Security model (read before touching anything under `core/security`)

Three layers, deliberately distinct:

1. **SQLCipher** — the whole Room DB (`daykit_secure.db`) is encrypted; the passphrase comes from `DatabasePassphraseProvider` (Android Keystore-wrapped).
2. **`SensitiveValueCipher`** — app-layer AES-GCM keyed by an *always-available* Android Keystore key. Used for settings, which must be readable in the background.
3. **`SessionValueCipher`** — AES-GCM keyed by the PIN-derived **MSK** in [SensitiveKeyManager](app/src/main/java/com/daykit/core/security/SensitiveKeyManager.kt). Used by the File Vault, Key Store, and Secure Notes so that data is undecryptable without the PIN even on a rooted device.

Both ciphers implement `ValueCipher` with the same `CipherPayload` shape, so a repository can be pointed at either. Every call passes an `aad` string (usually the key/column name) — keep it stable or existing rows fail to decrypt.

Key invariants:

- The MSK is a random 256-bit key **wrapped** by an Argon2id-derived key, so a PIN change re-wraps rather than re-encrypts data. Never route the MSK through the Android Keystore — that is the exact threat this design closes.
- `SessionValueCipher` reads the key fresh per call and throws `SensitiveDataLockedException` when locked. Repositories observing sensitive data must `.catch { if (it is SensitiveDataLockedException) emit(emptyList()) else throw it }` — the DB can re-query in the instant between the key being wiped and the unlock gate recomposing (see `KeyStoreRepository.observeEntries`).
- The key is wiped when the app is backgrounded, after `LOCK_GRACE_MILLIS` (2s) in [MainActivity](app/src/main/java/com/daykit/MainActivity.kt). **Before launching any picker/chooser/permission dialog, set `container.sensitiveKeyManager.expectingActivityResult = true`**, otherwise the result callback runs with the vault locked and the import/export fails.
- `MainActivity` and the lock activities set `FLAG_SECURE`; screenshot protection is a user setting that toggles it.

`AppLockSessionManager` is a separate, in-memory, 5-minute-TTL grant map for *third-party* apps the user has locked — unrelated to the MSK.

## Persistence

Room + KSP, database version **11**, `exportSchema = false`. Every schema change needs a hand-written `Migration` added to the list in [DayKitDatabase.create](app/src/main/java/com/daykit/core/data/DayKitDatabase.kt) — there is no destructive fallback, so a missing migration crashes on upgrade. Note the existing downgrade migration `MIGRATION_7_6`.

Non-secret plumbing lives in plain SharedPreferences mirrors so startup never blocks on Keystore + SQLCipher: `SettingFlagCache` (boolean settings), `LockedPackageCache` (locked package list, read by the monitor service), `FocusBlockStore`. The encrypted DB stays the source of truth; these are caches that must be refreshed on every write.

All settings keys are `const val KEY_*` on `SecureSettingRepository.Companion` — add new ones there, not as loose strings.

## Backup

`BackupContributor` (`toolKey` + `schemaVersion` + JSON export/import) is the extension point; contributors are registered in the `backupService` block of `AppContainer`. `DayKitBackupService` wraps them into one password-encrypted payload (`PAYLOAD_VERSION`), and import silently skips a section whose `schemaVersion` doesn't match the current contributor — bump `schemaVersion` when you change a payload shape. Drive upload runs through `DriveBackupWorker` (WorkManager) scheduled by `DriveBackupScheduler`. Vault files are excluded from backup unless the user opts in.

## Design system

Use `core/designsystem/components/*` (`AppCard`, `AppTextField`, `PrimaryButton`, `AppBottomSheet`, `ToolUnlockScreen`, `EmptyState`, `LoadingIndicator`, …) and `Spacing` / `MaterialTheme.extendedColors` rather than raw Material3 widgets and hardcoded dp/colors. `ExtendedColors` is a semantic layer (card, accents, `isDark`) supplied via `LocalExtendedColors` in `DayKitTheme`.

## Background components

App Lock runs `AppMonitorService`, a `specialUse` foreground service that polls UsageStats (adaptive 250ms → 1s cadence) and raises `LockActivity` / an overlay via `LockOverlayController`. It reads locked packages from the plain-prefs cache so it works before the DB is unlocked. `EventLightService` is a second `specialUse` FGS drawing a border overlay. Reminders use `AlarmManager` exact alarms plus `ReminderAlarmActivity`; habit reminders use WorkManager. Widgets (`feature/widget/`) are classic `RemoteViews` AppWidgetProviders refreshed through `WidgetUpdater`.

Permissions that gate these (Usage Access, overlay, notifications, exact alarms) are checked by `AppLockPermissionChecker` and surfaced through the onboarding `PermissionGrantScreen`. [PLAY_CONSOLE_PERMISSIONS.md](PLAY_CONSOLE_PERMISSIONS.md) holds the Play Console justification text for each restricted permission — keep it in sync when adding or removing a permission from the manifest.
