# DayKit review — 13 September 2026

Scope: source review of reminders and their notification/boot/Today integration, encrypted storage and key lifecycle, vault import/export/restore, backup composition, exported Android components, and selected settings flows. This is a targeted review, not a complete penetration test or a device-based certification. No critical (P0) issue was established.

## Follow-up fixes

All four findings below have now been addressed in the workspace:

- Published gallery copies are preserved even when source cleanup fails. Database deletion occurs before blob cleanup, and publication must succeed before any source deletion.
- Vault imports validate every canonical UUID before mutation and generate exclusive, independent storage filenames, preventing backup paths from selecting an existing blob.
- A synchronized session-key cache coordinates copies, replacement, and wiping. Generation checks also prevent a slow unlock from undoing a newer lock.
- The existing in-memory JSON format now preflights a 4 MiB total vault-file limit and checks actual streamed bytes. Missing blobs fail backup instead of becoming empty files. Both local and Drive restore reads are bounded at 16 MiB; exports exceeding that envelope size fail before save/upload. This is a bounded-format fix, not a new streaming media-backup format.
- New optional backup contributors and default-off switches cover Reminders, App Lock selections, Event Light configuration, and appearance/widget preferences. The same flags apply to local backups, manual Drive backups, and automatic Drive backups. Existing always-included tools retain their behavior.
- Reminder backups include recurrence, timezone, completion, and outstanding occurrence state. Restore validates the full reminder section, merges missing IDs without reverting existing reminders, and re-arms notifications. App Lock restores selections without transferring credentials or granting permissions. Event Light restore does not turn on an overlay. Appearance restore cannot change backup opt-ins.

## Original high severity findings (fixed above)

The following locations and failure descriptions refer to the code as originally reviewed, before these fixes.

### P1 — Restore-to-gallery error handling can delete both copies

Location: `app/src/main/java/com/daykit/feature/filelocker/data/VaultFileRepository.kt`, `restoreToGallery` (lines 304–316) and `delete` (158–161).

Trigger: the gallery copy is successfully written, then `delete(fileId)` removes the encrypted blob, but `dao.deleteByFileId` throws (for example a database write/storage failure). The outer catch deletes the gallery copy as well. The remaining database row points at a missing blob. The user loses both copies.

Reproduction for an instrumented/fake-provider test: allow the target insert, copy, publish, and blob deletion to succeed; inject an exception from `dao.deleteByFileId`; observe the catch deleting the target. This ordering is established from the source; no real user file was deleted during this review.

Suggested fix: treat publishing the restored copy as the commit point. After publication, preserve the destination regardless of source cleanup failure; report cleanup as a separate recoverable error. Use a pending-delete state or database-first cleanup with orphan recovery. Check the MediaStore update result before source deletion.

### P1 — Backup file IDs can escape their intended path and overwrite vault blobs

Location: `VaultFileRepository.kt`, `importFromBackup` (184–215); input comes from `VaultBackupContributor.importJson` without ID validation.

Trigger: import a crafted, validly encrypted backup with a supplied password and a `fileId` such as `../vault/<an-existing-file-uuid>`. The duplicate lookup checks the whole attacker-controlled ID, so it does not match the existing UUID. The derived path resolves to the existing vault blob, which `FileOutputStream` truncates. The original record's encryption metadata no longer matches the overwritten file. Other writable `.bin` paths can also be reached using traversal.

This requires the user to import the crafted backup; it is not an unauthenticated remote exploit. Encryption authenticates the backup against its supplied password, not against a trusted backup author.

Suggested fix: validate canonical UUIDs before any mutation, generate new storage filenames independently of imported IDs, require canonical paths to remain direct children of the vault directory, and create files without overwriting existing blobs. Validate the entire section before writing it.

### P1 — Sensitive-key copying races with key wiping

Location: `app/src/main/java/com/daykit/core/security/SensitiveKeyManager.kt`, `key`, `requireKey`, and `lock`.

Trigger: a background vault/note/secret operation starts copying `cachedKey` while the main lifecycle thread calls `lock()` and zeroes the same array. `@Volatile` protects publication of the array reference; it does not make copying the array and zeroing its contents mutually exclusive. A returned key can therefore be zeroed or partially copied. Decryption may fail; encryption can persist ciphertext under a key that cannot be reconstructed. Vault import can subsequently delete the original source.

Suggested fix: synchronize key copying, replacement, and wiping on one lock, or use a session-key lease whose lifetime outlasts each operation. Exercise the copy/lock interleaving with concurrent tests before release. This is a source-established race; it was not reproduced on a device during this review.

### P1 — Vault backups materialize the entire media library repeatedly in memory

Location: `VaultFileRepository.exportForBackup`, `VaultBackupContributor.exportJson`, and `core/backup/BackupCrypto.encrypt`.

Trigger: enable vault backups with videos or a collection approaching the Android process heap limit. Every file is read into a byte array, retained together in a list, converted to Base64 JSON, and serialized again before whole-payload encryption. Even one sufficiently large video can exhaust the heap and prevent the backup from completing. Streaming normal vault import does not prevent this separate backup failure.

Suggested fix: introduce a versioned streaming backup format with bounded buffers and authenticated file records. Until then, preflight total size and enforce an explicit supported limit before allocating, with a clear explanation to the user. Also fail backup when a referenced blob is missing instead of substituting an empty file (`?: ByteArray(0)`).

## Reminder fixes and additions in this change

- Daily, weekly, monthly, and yearly repeats; intervals from 1 to 365; selected weekdays for weekly rules; optional inclusive end date.
- Rules retain the original calendar/timezone anchor. Monthly dates absent from a month use that month's last day; yearly February 29 uses February 28 in non-leap years. The form explains this behavior.
- Future occurrences are persisted and armed when an alarm fires, independently of acknowledgement. Complete acknowledges the outstanding occurrence; if none is outstanding, it skips the next occurrence. Delete stops the series.
- Notification actions identify the exact occurrence so old actions cannot complete an edited or later occurrence.
- A non-destructive database migration adds nullable recurrence and outstanding-occurrence fields; existing one-time reminders remain valid.
- Pending overdue reminders now participate in reboot/startup recovery. Previously the query selected only future times, permanently dropping notifications due while powered off. Recovery alerts once for the stored missed occurrence and resumes the next future repeat rather than replaying every missed interval.
- Exact-alarm permission grants and device clock changes trigger re-arming; the scheduling permission check has a fallback if access changes during scheduling.
- Reminder date-picker values use UTC calendar dates as required by the picker, while actual alarm times use the selected schedule timezone.
- Dismissing the full-screen reminder no longer posts another full-screen notification. Single-top alarm activity updates redraw the new reminder title.
- Permission notices explain disabled notifications and unavailable precise alarms, with settings shortcuts.

## Recommended product improvements

1. Reminder backup/restore and utility opt-ins are now implemented. A future streaming backup format would remove the current vault size restriction.
2. Add explicit Snooze (5/10/30 minutes), Skip next, Pause series, and occurrence history. Currently Complete also skips an upcoming occurrence, which should become a distinct action.
3. Add an optional occurrence-count end condition, nth-weekday monthly rules, and a timezone chooser. The delivered feature covers common calendar-style recurrence, not all Google Calendar recurrence options or meeting invitations.
4. Offer notification privacy controls (hide reminder titles on lock screen) and a test-notification button, including channel-level checks.
5. Export Room schemas and maintain instrumented migration tests for every shipped database version. Replace silent boot reschedule catches with observable diagnostics.

## Validation and limits

Final validation: `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:lintDebug` all passed. The final suite passes 58 tests, including recurrence/restore, key-concurrency, vault-cleanup/path, backup-limit, and default-off toggle regressions. A SQLite migration smoke check preserved an existing one-time reminder and initialized the two new fields to NULL. Android device/Doze delivery, full-screen permission behavior across OEMs, and a real encrypted-database upgrade still need device testing. The high severity vault/security findings above are now fixed; their regression tests cover cleanup failure, malicious IDs, key-copy concurrency, and oversized input.
