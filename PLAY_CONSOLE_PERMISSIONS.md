# DayKit — Play Console Permission Justifications

Ready-to-paste rationale for the Google Play **App content** declarations and the
**Data safety** form. Package: `com.daykit`. All data is local-first; the only
network use is user-initiated Google Drive backup of already-encrypted files.

---

## 1. Sensitive / restricted permissions

These require a written justification in the Play Console (some trigger manual
review). Paste the "Justification" text into the corresponding declaration.

### `PACKAGE_USAGE_STATS` (Usage Access) — restricted, manual review likely
**Feature:** App Lock.
**Justification:** DayKit's App Lock lets the user require a PIN/biometric before
opening apps they select. Usage Access is the only supported API to detect which
app is in the foreground so the lock challenge can be shown for the correct app.
The permission is requested only after the user enables App Lock and picks apps
to lock. DayKit does not log, transmit, or sell usage data — it is read in-memory
solely to compare the current foreground package against the user's locked list.

### `SYSTEM_ALERT_WINDOW` (Display over other apps / Overlay)
**Feature:** App Lock + Event Light.
**Justification:** Used to (a) draw the PIN lock challenge over a locked app the
moment it comes to the foreground, and (b) show a colored border overlay ("Event
Light") to illuminate the user's face during video calls. Requested only when the
user enables one of these features. No overlay is shown at any other time and no
content from other apps is read.

### `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — manual review
**Feature:** Reminders.
**Justification:** DayKit fires user-set reminders and alarm-style notifications
at an exact time chosen by the user. Inexact alarms would cause reminders to fire
late, which defeats the feature (e.g. a bill-due or medication reminder). Alarms
are scheduled only for reminders the user explicitly creates and are cancelled
when the user deletes them.

### `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — manual review
**Feature:** App Lock monitor service and Event Light service.
**Justification (App Lock):** A foreground service continuously checks the
foreground app so the lock challenge can be presented reliably; this cannot be
done from a background-limited worker. Declared subtype: "Monitors the foreground
app to show a PIN challenge for user-locked apps."
**Justification (Event Light):** A foreground service keeps the border-light
overlay visible during a call. Declared subtype: "Shows a colored border overlay
to light the user's face during video calls."
Both services run only while their feature is enabled and show an ongoing
notification. No standard foreground-service type fits these uses, hence
`specialUse`.

### Device Admin (`BIND_DEVICE_ADMIN`)
**Feature:** Uninstall protection (optional).
**Justification:** DayKit can optionally register as a Device Administrator so it
cannot be uninstalled without first authenticating, preventing someone with
physical access from removing the security app to bypass App Lock. It is strictly
opt-in, uses only the uninstall-protection capability, and requests no policies
that access personal files, messages, location, or the camera. The user can
deactivate it at any time from Settings.

### `RECEIVE_BOOT_COMPLETED`
**Feature:** App Lock persistence.
**Justification:** Restarts the App Lock monitor service after a reboot or app
update so locked apps stay locked. The receiver validates the action and does no
sensitive work on a spoofed intent.

### `USE_FULL_SCREEN_INTENT`
**Feature:** Reminder alarms.
**Justification:** Shows a full-screen reminder/alarm UI (with screen-on) for
time-critical reminders the user created, matching alarm-clock behavior.

### `USE_BIOMETRIC`, `POST_NOTIFICATIONS`, `INTERNET`
Standard runtime permissions. Biometric = optional unlock for locked tools.
Notifications = reminders/habits/app alerts (user-enabled). Internet = Google
Drive backup/restore only.

---

## 2. Data safety form — summary answers

**Data collected / shared with third parties:** None for advertising or
analytics. No advertising ID. No data sold.

**Data handling:**
- All user content (Key Store, Secure Notes, Expenses, Habits, App Lock list,
  File Vault media) is stored locally, encrypted (SQLCipher DB; File Vault files
  AES-256-GCM with per-file keys wrapped by Android Keystore).
- The only data that leaves the device is an **encrypted backup file** the user
  chooses to upload to **their own Google Drive**. It is encrypted on-device with
  a user-chosen password before upload; DayKit uploads no plaintext content.
- Google account email is stored locally to identify the Drive backup account.

**Encryption in transit:** Yes (Google Drive APIs over HTTPS).
**Encryption at rest:** Yes (see above).
**Data deletion:** User can delete data in-app, clear app data, or uninstall.
Drive backup files are deleted by the user from their Drive (or via retention,
which keeps only recent backups).

**Account / third-party:** Google Sign-In + Google Drive API (scope limited to
app-created backup files) for the optional backup feature.

---

## 3. Pre-upload checklist
- [ ] Release keystore created and `local.properties` signing vars filled in.
- [ ] `./gradlew :app:bundleRelease` (AAB) or `assembleRelease` produces a signed artifact.
- [ ] Privacy policy URL live: https://www.rosmox.com/projects/daykit/privacy-policy
- [ ] Data safety form completed per section 2.
- [ ] Permission declarations submitted per section 1 (expect manual review for
      exact alarms + special-use FGS + usage access).
- [ ] `versionCode` / `versionName` bumped as intended (currently 1 / "1.0").
