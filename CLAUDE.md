# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

DayKit (`com.daykit`) — a single-module Android app: a local-first "kit" of privacy tools (App Lock, Key Store, Secure Notes, File Vault, Focus, Habits, Reminders, Expenses, Editor, DNS, Event Light). 100% Kotlin + Jetpack Compose, minSdk 31 / targetSdk 36. The only network use is user-initiated Google Drive backup of already-encrypted blobs.

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

- The MSK is a random 256-bit key **wrapped** by an Argon2id-derived key, so a PIN change re-wraps rather than re-encrypts data. The primary copy must remain PIN-wrapped.
- The Argon2id output (for both the MSK wrap and the `CredentialRepository` verifier) is then passed through `PinHardwareBinding`, an HMAC under a non-exportable Keystore key (StrongBox when available). This makes every PIN guess run on this device, so a 6-digit PIN can't be brute-forced offline. It hardens the PIN derivation; it is not a Keystore wrap of the MSK. Both stores carry a version (`wrap_version` / `pin_hash_version`): v1 is Argon2id only and upgrades to v2 on the next successful unlock/verify. Verification paths must never create the binding key (`createIfMissing = false`), or a lost key would silently derive different bytes.
- The master credential is a numeric PIN or an alphanumeric password (`CredentialKind`, stored in `CredentialRepository`). Every entry surface must respect it: `LockChallengeContent` shows a pad or a password field, and `AppMonitorService` never uses the overlay for a password (it can't reliably take keyboard input). Use `CredentialRepository.sanitize` / `newCredentialError` for input rules.
- The backup password lives in `BackupPasswordStore`, encrypted with `SessionValueCipher`, never in `SecureSettingRepository`. Backups contain Key Store and Notes plaintext, so a Keystore-only copy would let root read everything without the PIN. The optional biometric path may keep a second wrapped copy only through `BiometricUnlockManager`: its Android Keystore key requires a fresh strong biometric for every use and is invalidated by biometric enrollment changes. Never wrap the MSK with an always-available Keystore key.
- `SessionValueCipher` reads the key fresh per call and throws `SensitiveDataLockedException` when locked. Repositories observing sensitive data must `.catch { if (it is SensitiveDataLockedException) emit(emptyList()) else throw it }` — the DB can re-query in the instant between the key being wiped and the unlock gate recomposing (see `KeyStoreRepository.observeEntries`).
- The key is wiped after a user-configurable grace window once the app is backgrounded (`LockGracePeriod`, default 10s, max 1 min, `KEY_LOCK_GRACE_SECONDS` mirrored in `SettingFlagCache` so `ON_STOP` reads it synchronously), including when DayKit launches a picker, chooser, or permission screen. `SensitiveKeyManager.onBackgrounded` posts the delayed wipe and `onForegrounded` (on `ON_START`, before results are delivered) re-checks the deadline against `elapsedRealtime`, because a frozen process may never run the delayed wipe. Screen-off (`DayKitApplication`) and task removal still wipe immediately. Lengthening the window requires the PIN. **Before launching an external activity, set `container.sensitiveKeyManager.expectingActivityResult = true`** so the current screen can receive its result behind the unlock gate. Any callback that needs the MSK must use `runWhenUnlocked`; its work resumes only after a fresh PIN or biometric unlock.
- `MainActivity` and the lock activities set `FLAG_SECURE`; screenshot protection is a user setting that toggles it.
- Every surface that takes the master credential or the backup password calls `HideOverlayWindows()` (ref-counted `Window.setHideOverlayWindows`), so another app's overlay can't draw a fake keypad over it. Add it to any new credential prompt.

`AppLockSessionManager` is a separate, in-memory grant map for *third-party* apps the user has locked — unrelated to the MSK. Its expiry follows the user's `AppLockRelock` choice (default `OnLeave`: evicted on app switch, 5-minute TTL; relaxed modes stamp `leftAtMillis` and expire after the away time). `DayKitApplication` mirrors the pref into `AppLockSessionManager.relock`; screen-off always clears every grant.

Key Store copies go through `SensitiveClipboard`: flagged `EXTRA_IS_SENSITIVE` and cleared by a WorkManager job (the process may be frozen by then), never a plain `setText`.

## Persistence

Room + KSP, database version **7**, `exportSchema = false`. There are real installs: every schema change needs a hand-written `Migration` added in [DayKitDatabase.create](app/src/main/java/com/daykit/core/data/DayKitDatabase.kt) plus a version bump. There is deliberately **no** `fallbackToDestructiveMigration`, so a missing migration must crash on upgrade rather than silently wipe data.

**Recently deleted.** Secure Notes, Key Store and File Vault deletes only stamp `deletedAtMillis`; the DAOs' live queries (`observeAll`, `getAll`, `observeAllOnce`) filter trashed rows out, so they're also left out of backups. `RecentlyDeleted` holds the 30-day rule. `DayKitApplication.warmUp` purges expired items on startup (row and blob deletes need no MSK). Repositories expose `delete…` (to the bin), `restore…`, `…Forever`, `emptyTrash`, `observeTrash`; screens use the shared `RecentlyDeletedSheet` + `showUndo` snackbar. Vault's `restoreFromTrash` is unrelated to `restoreToGallery`.

Non-secret plumbing lives in plain SharedPreferences mirrors so startup never blocks on Keystore + SQLCipher: `SettingFlagCache` (boolean settings), `LockedPackageCache` (locked package list, read by the monitor service), `FocusBlockStore`. The encrypted DB stays the source of truth; these are caches that must be refreshed on every write.

`FocusBlockStore` is the exception: it is not a cache of anything, it *is* the source of truth for one-off focus blocks (plain prefs so `AppMonitorService` can read it before the DB is unlocked).

## Focus (`feature/focus/`)

UI vocabulary: **Lock now** (one-off block), **Daily limit** (app limits), **Routine** (schedule), **App sets** (groups). Each mode has one icon + accent in `FocusMode` (`FocusModeVisuals.kt`); use it rather than picking colors ad hoc. Code names (`FocusGroup`, `FocusSchedule`) are unchanged.

Three layers, and the split matters:

- **One-off blocks** — `FocusBlockStore` (plain prefs) behind `FocusRepository`. Always Strict: no cancel API exists, and the hold-to-lock button (`HoldToConfirmButton`, ~2s) in `FocusBlockSheet` is there because the action is irreversible. Don't add a `stopBlock`.
- **Groups + schedules** — Room (`focus_groups`, `focus_schedules`) behind `FocusGroupRepository` / `FocusScheduleRepository`. Non-secret (package names the user picked), so they use the plain DAO path, **not** `SessionValueCipher` — enforcement has to act on them while the vault is locked.
- **`FocusScheduleCache`** — a plain-prefs *projection* of the next occurrence of every enabled schedule, rewritten on every change. `AppMonitorService` and `FocusScheduleReceiver` read only this, never the repositories: the service seeds its blocked map synchronously in `onCreate`, and on a cold start SQLCipher may not be unlocked. Active windows are derived from the armed list, so a Doze-deferred end alarm can't strand an app.

`FocusRecurrence` holds the weekday bitmask (**bit 0 = Monday**) and next-occurrence math. It's pure and unit-tested — put scheduling logic there, not in a composable.

Strictness is the safety valve: a **Normal** scheduled session can be ended early with the PIN, a **Strict** one cannot. `FocusScheduleRepository.endSessionEarly` rejects Strict at the data layer rather than trusting the UI to hide the button. A focus block must never be openable by PIN or biometric; three separate guards enforce that (the `shouldLock` OR in `AppMonitorService`, the grant skip in `LockActivity`, and the `isFocusBlocked` lambda in `LockOverlayController`) — keep all three.

Schedules use exact alarms (`FocusScheduleScheduler`, modelled on `ReminderScheduler`) and **re-arm themselves** in `FocusScheduleReceiver` — AlarmManager has no weekday recurrence, so never `setRepeating`. `AppLockBootReceiver` re-projects and re-arms after boot. Exact-alarm permission is checked via `AppLockPermissionChecker.canScheduleExactAlarms` and deliberately excluded from `AppLockPermissionState.allGranted` — App Lock works without it; only schedules need it, and the Focus screen warns when it's missing rather than silently drifting.

All encrypted settings keys are `const val KEY_*` on `SecureSettingRepository.Companion` — add new ones there, not as loose strings.

## Settings

The Settings tab is a hub (`SettingsScreen`) linking to sub-pages: Security & Privacy, General, Appearance, Home Screen, Notifications & Permissions, Backup & Restore, Data & Storage. Sub-pages use `SettingsSubPage`, `OptionSheet` and `rememberPinGatedChange` from `SettingsComponents.kt`. Any change that *relaxes* a security setting (longer auto-lock, clipboard kept longer, App Lock re-locking later, plaintext notes export) must go through `rememberPinGatedChange`; tightening applies immediately.

Non-secret preferences live in `AppPreferences` (plain prefs, same file as theme/haptics) so formatters, receivers and the monitor service read them synchronously. Use its helpers rather than hardcoding: `Money` (currency), `WeekDays` (first day of week), `TimeFormat` (12/24h, also pass `is24Hour` to Material time pickers). Before `AppPreferences.init` (JVM tests) getters return defaults.

## Backup

`BackupContributor` (`toolKey` + `schemaVersion` + JSON export/import) is the extension point; contributors are registered in the `backupService` block of `AppContainer`. `DayKitBackupService` wraps them into one password-encrypted payload (`PAYLOAD_VERSION`), and import silently skips a section whose `schemaVersion` doesn't match the current contributor — bump `schemaVersion` when you change a payload shape. Automatic Drive backup runs through `DriveBackupRunner.launchIfDue()`, called from `MainActivity` each time the app is unlocked. There is deliberately no background worker: Key Store and Secure Notes need the MSK, which exists only while unlocked. Vault files are excluded from backup unless the user opts in.

## Design system

Use `core/designsystem/components/*` (`AppCard`, `AppTextField`, `PrimaryButton`, `AppBottomSheet`, `ToolUnlockScreen`, `EmptyState`, `LoadingIndicator`, …) and `Spacing` / `MaterialTheme.extendedColors` rather than raw Material3 widgets and hardcoded dp/colors. `ExtendedColors` is a semantic layer (card, accents, `isDark`) supplied via `LocalExtendedColors` in `DayKitTheme`.

No wide, faint shadows or gradients: on an 8-bit display they band, and the panel's dithering turns the bands into visible grain. Use the dithered helpers instead (`Modifier.ditheredGlow`, `MeshBlob`'s paint), never `Brush.radialGradient` for large soft areas.

Appearance lives in `core/designsystem/background/`: `CardStyle` (Flat / Clay / Liquid glass, one at a time, default Flat) and `PageBackgroundKind` (Plain; three soft meshes; three graphic designs drawn by `GeneratedArt` at screen size; or the user's photo in `CustomWallpaper`), both plain-prefs `EnumPref`s provided as `LocalCardStyle` / `LocalPageBackground` by `DayKitTheme`. Backgrounds draw in **window coordinates**: `opaqueComposable` paints `Modifier.pageBackground()`, and glass cards and the top/bottom bars use `Modifier.frostedBackdrop`, which samples the frosted background at the element's window position (no live blur). Over a wallpaper, pages' `colorScheme.background` is overridden to transparent, so never use `colorScheme.background` as an opaque fill inside a screen — use `extendedColors.card` or `LocalPageBackground.current.solid`. Plain + Flat renders exactly as before. Liquid glass is one recipe in `components/Glass.kt` (frosted backdrop + tint + sheen + rim): `liquidGlassCard`, `glassControl` (shared buttons/chips/FAB), and `glassGroupItem` (rows joined into one panel, used by App Lock); `ExtendedColors.forLiquidGlass()` makes `inputField`/`actionFill`/`divider` translucent so custom controls follow. Text-field boxes use `fieldFill`, which stays solid in every mode. `SolidSurface` opts a subtree out of wallpaper and glass (bottom sheets always; `SolidPage` for typing screens: the note editor and the Editor tool).

## Background components

App Lock runs `AppMonitorService`, a `specialUse` foreground service that polls UsageStats (adaptive 250ms → 1s cadence) and raises `LockActivity` / an overlay via `LockOverlayController`. It reads locked packages from the plain-prefs cache so it works before the DB is unlocked. `EventLightService` is a second `specialUse` FGS drawing a border overlay. Reminders use `AlarmManager` exact alarms plus `ReminderAlarmActivity`; habit reminders use WorkManager. Widgets (`feature/widget/`) are classic `RemoteViews` AppWidgetProviders refreshed through `WidgetUpdater`.

Permissions that gate these (Usage Access, overlay, notifications, exact alarms) are checked by `AppLockPermissionChecker` and surfaced through the onboarding `PermissionGrantScreen`. [PLAY_CONSOLE_PERMISSIONS.md](PLAY_CONSOLE_PERMISSIONS.md) holds the Play Console justification text for each restricted permission — keep it in sync when adding or removing a permission from the manifest.
