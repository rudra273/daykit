# DayKit — Security Fixes

Security review findings for the DayKit Android app, tracked for remediation.
We fix these **one by one**, in priority order.

**App type:** Personal-security utility (secure notes, password/key store, file
locker, app-lock, expense tracker, Google Drive backup). Because it is a security
product, the bar is high — users store passwords and private files and trust the
"lock" to mean something.

**User constraints (important):**
- ❌ Do **NOT** auto-wipe / delete data on failed PIN attempts. Use lockout +
  escalating delay (backoff) instead. Data must never be destroyed on wrong PIN.

**Decisions (confirmed):**
- C1 lockout = **escalating delay** (backoff), not fixed cooldown.
- Minimum PIN length = **6 digits**.
- **No migration handling** — dev mode, existing PINs/data can be reset freely.

---

## Status legend
- ⬜ Not started
- 🟡 In progress
- ✅ Done
- ⏭️ Deferred / won't fix (with reason)

---

## CRITICAL

### C1 — Master PIN has no brute-force protection ✅ DONE
**Where:** `CredentialRepository.verify()`
(`app/src/main/java/com/daykit/core/security/CredentialRepository.kt:30`),
all unlock screens (e.g. `AppLockScreen.kt:185`), PIN min length 4
(`OnboardingScreens.kt:161`).

**Problem:** No attempt counter, no lockout, no backoff. A 4-digit PIN = 10,000
combinations. Argon2id raises per-guess cost but with no lockout the app is an
unlimited online guessing oracle. The whole security model is defeatable by
guessing on an unlocked/seized device.

**Fix (respecting "no delete" constraint):**
- Enforce minimum PIN length of 6 digits.
- Persistent failed-attempt counter (survives app restart) stored securely.
- Escalating lockout delay after N failures (e.g. 5 → 30s, 10 → 5min, etc.).
- **No auto-wipe.** Lockout only.
- Reset counter on successful unlock.

**IMPLEMENTED:**
- `CredentialRepository.verify()` now returns `PinVerifyResult`
  (`Success` / `Wrong` / `LockedOut(remainingMillis)`) instead of `Boolean`.
- Lockout enforced **inside the repository** so all 8 call sites are protected
  automatically — including auto-submit-on-keystroke screens.
- Backoff: first 4 wrong tries free; then +30s per block of 5 wrong tries,
  capped at 30 min. Counter + `locked_until` persisted in SharedPreferences
  (survives restart). Reset on success and on `saveCredential`.
- **No data ever deleted** — lockout just refuses to check the PIN until timer
  expires.
- `MIN_PIN_LENGTH = 6` enforced in onboarding (`OnboardingScreens.kt`) and
  change-PIN (`SettingsScreen.kt`). New file `PinVerifyMessages.kt` formats the
  user-facing "Try again in Xm Ys" message.
- Callers updated: LockActivity, LockOverlayController, AppLockScreen,
  SecureNotesScreen, KeyStoreScreen, SettingsScreen (×5).
- ✅ `:app:compileDebugKotlin` BUILD SUCCESSFUL.

---

### C2 — "File Locker" does not lock or encrypt anything ✅ DONE
**Where:** `FileLockerScreen.kt:503-641`, `asLockedFile()` at line 674,
disclaimer string at line 329.

**Problem:** Files are moved into `Documents/<hidden folder>/` in **shared
external MediaStore** with a `.locked` extension appended — no encryption, not in
the SQLCipher DB. Any other app with media access, a file manager, USB, or `adb`
reads them directly; renaming away `.locked` restores them. Naming this "File
Locker" with a lock icon in a security app is deceptive.

**Fix chosen:** Option A (real encryption). Production-grade vault, not
encrypt-once-decrypt-to-disk. Files are ciphertext at rest in app-private
storage; only ever decrypted to RAM (view) or to a user-chosen location (export).

**IMPLEMENTED — Phase 1 (vault core + image viewing):**
- Added **Google Tink** (`tink-android` 1.15.0) — streaming AEAD
  (`AES256_GCM_HKDF_1MB`, chunked 1 MiB segments, constant memory, seekable).
- `VaultStreamingCrypto` — Tink `AesGcmHkdfStreaming` encrypt/decrypt streams.
- **Key hierarchy:** per-file random 256-bit DEK → stream-encrypts the file →
  DEK wrapped by the Android Keystore KEK (`AndroidKeyStoreCrypto`) and stored in
  the (SQLCipher-encrypted) DB. DEK never persisted in the clear.
- `VaultFileEntity` + `VaultFileDao` + DB **migration 10→11** (`vault_files`
  table). Name/mime encrypted at app layer too.
- `VaultFileRepository`:
  - `importFile(uri)` — streams source → encrypt → `filesDir/vault/<uuid>.bin`,
    fsync, write DB row, THEN delete original (crash-safe ordering).
  - `openDecryptedStream(id)` — decrypt to RAM, nothing hits disk.
  - `exportTo(id, out)` — the only path that writes plaintext, to a
    user-chosen SAF location.
  - `delete(id)`.
- Rewired `FileLockerScreen` + `FileLockerPreviewScreen` onto the repository:
  add/encrypt files, encrypted thumbnails (decrypt-to-RAM), image preview
  (decrypt-to-RAM), multi-select delete + export. Honest security callout.
- Files now in **app-private storage** (not shared `Documents/`) — invisible to
  other apps / file managers / USB.
- ✅ `:app:assembleDebug` BUILD SUCCESSFUL (debug APK builds).

**IMPLEMENTED — Phase 3 (encrypted video playback):**
- `VaultStreamingCrypto.newSeekableDecryptingChannel()` — Tink
  `newSeekableDecryptingChannel` over the ciphertext `FileChannel`; seeking
  decrypts only the touched 1 MiB segment.
- `VaultFileRepository.openSeekableChannel(fileId)` — returns a random-access
  decrypting channel (no full-file decrypt, no temp file).
- `VaultMediaDataSource` — custom media3 `DataSource` (+ `Factory`) that plays a
  vault file straight from the encrypted blob via a `daykitvault://` URI,
  honoring position/length for seeking. `FLAG_SECURE`-friendly.
- `FileLockerPreviewScreen` video path now plays in-app through
  `ProgressiveMediaSource` + the custom source. No plaintext hits disk to play.

**IMPLEMENTED — Drive backup opt-in for vault files (default OFF, tight):**
- `VaultBackupContributor` (toolKey `vault_files`) — export/import via the
  existing contributor model; wired into `AppContainer.backupService`.
- New setting `KEY_BACKUP_INCLUDE_VAULT`, **default false**.
- Gating is an **allow-list**: the `vault_files` toolKey is added to
  `includedBackupToolKeys()` ONLY when the toggle is explicitly true — in BOTH
  the manual/Drive path (`BackupRestoreScreen`) and the automatic worker
  (`DriveBackupWorker`). If off, `DayKitBackupService` never calls the vault
  contributor's `exportJson()`, so vault bytes cannot leak into any backup.
- UI toggle added under "What's included" with an explicit size/upload warning.
- `VaultFileRepository.exportForBackup()` / `importFromBackup()` — bytes sealed
  by the backup's own AES-256-GCM envelope; re-encrypted with a fresh DEK on
  restore.
- ✅ `:app:assembleDebug` BUILD SUCCESSFUL; new classes confirmed in build output.

**STILL TODO (revisit for perf / on device):**
- Phase 2 nicety: cache encrypted thumbnails as small blobs (currently decrypt
  the full image downsampled each grid load — fine for images, heavier for many
  files).
- Not runtime-tested (no device/emulator here) — verify on device: import →
  image view → video playback/seek → export → delete; MediaStore original-delete
  prompts; large-video seek performance.

---

## HIGH

### H1 — Keystore-derived key not bound to user authentication ✅ DONE
**Where:** `AndroidKeyStoreCrypto.kt:40-48` (no `setUserAuthenticationRequired`),
`DatabasePassphraseProvider.kt:29-35` (DB passphrase in plain SharedPreferences,
wrapped only by the always-available key).

**Problem:** The PIN gates the UI, but the data-encryption key is available to the
OS whenever the app process runs. On a rooted device or with a Keystore-extraction
exploit, an attacker decrypts everything without knowing the PIN. The PIN is not
part of the data-encryption path at all. For a password manager this is the
central weakness.

**Fix:** Derive/wrap the DB & data key from the PIN via Argon2 (backup already
does this in `BackupCrypto.kt:17`), and/or set `setUserAuthenticationRequired(true)`
with a validity window so the key requires biometric/PIN unlock.
**Note:** Most invasive change — touches DB open path & key derivation. Do last.

**IMPLEMENTED — PIN-derived key for the sensitive tools (envelope design):**
- Scope (per user): **Vault, Key Store, Secure Notes** get PIN-derived
  protection. Expenses/habits/reminders + settings stay on the Keystore key so
  widgets & background features keep working. DB passphrase unchanged (SQLCipher
  stays openable in background). **No biometric** (H2 dropped — see below).
- `SensitiveKeyManager` — random 256-bit MSK wrapped by an Argon2-from-PIN key
  (`wrappedMsk = AES-GCM(WK, MSK)`), stored wrapped in prefs. Never in Keystore,
  never in the clear. Envelope means **PIN change re-wraps the same MSK** →
  data is NOT re-encrypted / orphaned (`rewrap()` called in ChangePinSheet).
- `ValueCipher` interface; `SensitiveValueCipher` (Keystore, for settings) and
  `SessionValueCipher` (MSK, throws `SensitiveDataLockedException` when locked).
- Vault/KeyStore/Notes repos repointed to `sessionValueCipher`; vault DEK now
  wrapped by MSK instead of Keystore.
- **Mandatory app-unlock gate** in MainActivity: on cold start & after returning
  from background, a PIN screen derives the MSK (C1 lockout applies via
  `verify()` first). MSK **wiped on ON_STOP** (background) → sensitive tools
  can't be decrypted until re-unlock. MSK initialized at onboarding.
- Invariant verified: nothing reaches the sensitive repos except via
  RootScaffold, which only renders after the gate. Widgets / app-lock service /
  LockActivity / reminders never touch the session key.
- Backup: automatic (background) backup **excludes** the sensitive tools (no key
  available) by design; manual foreground backup includes them. Worker degrades
  gracefully instead of crashing.
- ✅ full rebuild + `:app:assembleRelease` (R8) + unit tests all pass.

**WIRING AUDIT — follow-up fixes (all verified: debug+release compile, tests pass):**
- **ON_STOP was too aggressive.** It fires on rotation, config changes, and any
  full-screen activity WE launch (file/media/account pickers, Drive auth
  intent-sender) — each wiped the key, so the picker result callback then failed
  every import/export/backup and the user was bounced to the PIN gate. Fixed two
  ways: (1) `SensitiveKeyManager.expectingActivityResult` flag set before every
  launcher `.launch()` — that ON_STOP skips the wipe; (2) a `LOCK_GRACE_MILLIS`
  (2s) delayed lock, cancelled on quick resume, so rotations/brief glances don't
  re-prompt. A genuine departure still locks.
- **Locked-key crash race.** `observeFiles`/`observeEntries`/`observeNotes`/
  `observeImagesByNote` could re-query in the instant between wipe and the gate
  recomposing (vault's `toVaultFile()` decrypts without a per-row catch → real
  crash). Added `.catch { SensitiveDataLockedException -> emit(empty) }` to all
  four flows.
- **Key-zeroing corruption window.** `requireKey()`/`key()` returned the cached
  array by reference; a concurrent `lock()` zeroing it mid-encrypt would write a
  record under a partially-zeroed key (permanent loss). Now they return a
  defensive copy and `SessionValueCipher` zeros it in a `finally`.
- **Drive retention could delete the only sensitive backup.** Auto backups never
  contain the sensitive tools, so a manual full backup is the sole cloud copy —
  but pruning kept the newest 3 regardless of source. `DriveBackupRetention` now
  always protects the most-recent Manual backup (+ tests).
- **Worker `isUnlocked()` was misleading dead code** (fresh container → always
  locked). Made it a literal `includeSensitive = false`, and the worker now
  **skips upload+prune entirely when the backup would be empty** (both
  expenses/habits off) so it can't rotate away real backups with an empty one.
- **Change-PIN when MSK uninitialized** now creates the key under the new PIN
  instead of silently skipping (can't leave the tools permanently unusable).

**BEHAVIOR CHANGES the user must know:**
- App now asks for the PIN on every cold start and when returning from
  background (this is inherent to "data only decryptable when unlocked").
- **Forgetting the PIN = vault/keystore/notes are permanently unrecoverable.**
  That is what real encryption means; the C1 no-wipe rule protects against
  guessing, but a genuinely forgotten PIN cannot be recovered.
- Automatic Drive backup can no longer include vault/keystore/notes — only
  manual backup (while unlocked) can. Expenses/habits still auto-backup.
- Not runtime-tested here — verify on device: unlock → tools decrypt; change PIN
  → data still readable; background → re-prompt; wrong PIN → locked out.

---

### H2 — Biometric unlock is a UI-only gate, no CryptoObject ⏭️ WON'T FIX (dropped by decision)
**Where:** `BiometricAuthenticator.kt:26-28`.

**Problem:** Biometric success just flips a boolean; it never unlocks a
CryptoObject-bound key. Combined with H1, biometric success is cryptographically
meaningless.

**Fix:** Bind the biometric prompt to a Keystore key via
`BiometricPrompt.CryptoObject` so authentication is required to actually decrypt.
(Naturally pairs with H1.)

**DECISION: dropped.** User chose PIN-only. Biometric adds convenience but no
security over a strong PIN here, and biometric cannot derive the PIN-based MSK
anyway. The new app-unlock gate is PIN-only. Existing biometric on the app-lock /
tool-unlock screens is a separate UI convenience and was left as-is (it does not
gate the sensitive MSK — the app-unlock gate does).

---

### H3 — Cloud backup restore may be unauthenticated on-device ✅ DONE (restore gate)
**Where:** Restore flow + `BackupCrypto.kt`, `GoogleDriveBackupClient.kt`.

**Problem:** Backups are encrypted with a user password (good). But confirm the
**restore** flow requires the master PIN before importing/overwriting the local DB.
Also verify OAuth scope is `drive.file` (app-created files only), not full `drive`.

**Fix:** Require PIN before restore; confirm minimal OAuth scope.

**IMPLEMENTED — master PIN gate before restore (both Drive & local):**
- New `BackupSheet.RestorePin` stage + `PendingRestore` enum. After the user
  enters the backup password and taps Restore, the flow now switches to a
  `RestorePinSheet` instead of restoring immediately.
- `confirmRestorePin()` calls `credentialRepository.verify()` — so the restore
  gate automatically inherits the **C1 escalating lockout** and shows
  "Too many attempts…" messages. Only `PinVerifyResult.Success` proceeds to the
  actual restore (`requestDriveAuthorization(...Restore)` / `restoreLocalBackup`).
- Dismissing the sheet clears the pending-restore + PIN state (no stale gate).
- Decision: gate **restore only** (destructive/overwrites data). Creating a
  backup stays frictionless. UI reuses the Settings PIN-confirm pattern.
- ✅ compiles clean under `:app:compileDebugKotlin --rerun-tasks`.

**OAuth scope — VERIFIED OK:** both the worker and the screen request only
`https://www.googleapis.com/auth/drive.file` (app-created files), not full
`drive`. No change needed.

**Note:** restore already required the backup *password* (to decrypt); this adds
the master-PIN check so a person past the app lock on an unlocked device can't
overwrite/tamper with local data via restore.

---

## MEDIUM

### M1 — Release builds un-minified / un-obfuscated ✅ DONE
**Where:** `build.gradle.kts:64` (`isMinifyEnabled = false`), empty
`proguard-rules.pro`.

**Problem:** Class/method names, crypto structure, hardcoded key alias/AAD strings
readable in the APK; eases reverse engineering.

**Fix:** Enable R8 minification + obfuscation for release; add keep rules as needed.

**IMPLEMENTED:**
- `build.gradle.kts` release: `isMinifyEnabled = true`, `isShrinkResources = true`.
- Full `proguard-rules.pro` with keep rules for Room, SQLCipher (JNI), Tink,
  argon2kt (JNI), Play Services auth, media3, and our custom
  `VaultMediaDataSource`. Keeps line numbers, renames source file.
- ✅ `:app:assembleRelease` BUILD SUCCESSFUL — R8 ran clean, keep rules correct.
- Result: release APK **90 MB → 20 MB**; `mapping.txt` produced (KEEP this file
  to de-obfuscate crash reports).

---

### M2 — `AppLockBootReceiver` exported ✅ DONE
**Where:** `AndroidManifest.xml:76-83`.

**Problem:** Must be exported for `BOOT_COMPLETED`, but any app can send
`MY_PACKAGE_REPLACED`-shaped intents. Low impact (only restarts monitor after
state check), but tighten.

**Fix:** Strict action validation (already present); confirm no sensitive action on
attacker-controllable intent.

**IMPLEMENTED:** Receiver now early-returns unless the action is exactly
`BOOT_COMPLETED` or `MY_PACKAGE_REPLACED`; documented in code + manifest why it
stays exported (BOOT_COMPLETED delivery) and why a spoofed intent is harmless
(only re-checks real state; starting the monitor needs the app's own permissions).

---

### M3 — Powerful surveillance-shaped permission set; overlay app-lock is bypassable ✅ DONE
**Where:** `AndroidManifest.xml:6-13`, `LockOverlayController`.

**Problem:** `SYSTEM_ALERT_WINDOW` + `PACKAGE_USAGE_STATS` + Device Admin +
foreground monitoring. Overlay-based app-lock is a known tapjacking/anti-tamper
weak spot (dismissible via recents, permission revocation, safe mode).

**Fix:** Document limits honestly; don't market app-lock as tamper-proof.

**IMPLEMENTED (Privacy Policy screen):**
- Added an explicit "App Lock is a deterrent, not a guarantee" paragraph naming
  the real bypasses (recents, safe mode, permission revocation, launch race) and
  noting it does not encrypt locked apps' data; recommends device screen lock +
  Secure Folder.
- **Fixed now-stale claims** (C2/backup shipped): vault text updated from "not
  encrypted" → "AES-256, app-private, per-file keys"; backup text updated to note
  vault backup is optional + off by default, and restore requires the master PIN.
- No false "unbreakable/100%" marketing claims existed elsewhere (checked).

---

### M4 — Session grants never self-expire ✅ DONE
**Where:** `AppLockSessionManager.kt`.

**Problem:** Grants are timestamped but rely on external revocation; a locked app
could stay "allowed" indefinitely after backgrounding.

**Fix:** Verify/enforce grant expiry on background or after a timeout.

**IMPLEMENTED:** `AppLockSessionManager.isAllowed()` now expires a grant after a
5-minute TTL (checked against `grantedAtMillis`, which was already recorded) and
removes it. `isAllowedForWindow()` delegates to the same check. The monitor polls
`isAllowed` every cycle, so a locked app re-challenges once its grant ages out
even if the screen never turned off.

---

## LEGAL / COMPLIANCE (not legal advice)
- **C2 deceptive naming** is the sharpest exposure (FTC Act §5 / EU-UK unfair
  practices). Fix naming or encryption.
- Align store-listing "encrypted/secure" claims with actual guarantees (see
  C1–H2).
- Google Play Data Safety + Drive OAuth: declare accurately, use minimal scope
  (H3). Broad Drive scope triggers restricted-scope verification + CASA.
- No hardcoded secrets leaked; `local.properties` gitignored (SDK path only).
  OAuth client ID in `build.gradle.kts:42` is public by design (not a secret).

---

## WHAT'S ALREADY DONE WELL
AES-256-GCM w/ randomized IVs + AAD binding; Argon2id for PIN hashing & backup KDF;
SQLCipher for the DB; `allowBackup="false"`; per-field column encryption; sensitive
`CharArray`/`ByteArray` buffers zeroed in `finally`. Primitives are right — gaps are
in **key binding to the user** and **anti-brute-force**.

---

## FIX ORDER (priority)
1. **C1** — Brute-force lockout + min PIN length (no wipe). *High impact, low risk.*
2. **C2** — File Locker: rename+warn (fast) or encrypt (real fix).
3. **H3** — Require PIN before restore; confirm OAuth scope.
4. **M2 / M4** — Exported receiver hardening + session grant expiry (quick).
5. **M1** — Enable R8 minify/obfuscate for release.
6. **M3** — Honest documentation of app-lock limits.
7. **H2 + H1** — Biometric CryptoObject + PIN-derived data key (most invasive; last,
   together).
