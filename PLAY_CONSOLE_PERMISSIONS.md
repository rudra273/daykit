# DayKit — Play Console Permission Justifications

Ready-to-paste rationale for the Google Play **App content** declarations and the
**Data safety** form. Package: `com.daykit`. All data is local-first; the only
network use is user-initiated Google Drive backup of already-encrypted files.

---

## 1. Sensitive / restricted permissions

These require a written justification in the Play Console (some trigger manual
review). Paste the "Justification" text into the corresponding declaration.

### `PACKAGE_USAGE_STATS` (Usage Access) — sensitive, expect manual review
**Features:** App Lock, Focus (daily limits), Device Manager.
**Justification:** DayKit uses Usage Access for three user-facing features, all
processed only on the device:
1. **App Lock** — detects which app is in the foreground so the PIN/password
   challenge appears for the apps the user chose to lock. Android offers no other
   supported way to do this.
2. **Focus daily limits** — measures today's time in the apps the user put under a
   daily limit, so the app can be blocked once the limit is reached, and suggests
   limits from the past week's usage.
3. **Device Manager** — shows the user their own most-used and least recently used
   apps (from the usage history Android keeps, up to ~90 days) to help them clean
   up unused apps.
Usage Access is requested from onboarding and from these screens, with an
explanation of why it is needed. Usage data is never uploaded, shared, sold, or
included in backups; DayKit stores only the lock list, limits and schedules the
user sets.

### `SYSTEM_ALERT_WINDOW` (Display over other apps / Overlay)
**Feature:** App Lock + Event Light.
**Justification:** Used to (a) draw the PIN lock challenge over a locked app the
moment it comes to the foreground, and (b) show a colored border overlay ("Event
Light") to illuminate the user's face during video calls. Requested only when the
user enables one of these features. No overlay is shown at any other time and no
content from other apps is read.

### `SCHEDULE_EXACT_ALARM` (user-granted; `USE_EXACT_ALARM` is not requested)
**Features:** Reminders and Focus routines.
**Justification:** DayKit fires user-set reminders and alarm-style notifications
at an exact time chosen by the user. Inexact alarms would cause reminders to fire
late, which defeats the feature (e.g. a bill-due or medication reminder). Focus routines use exact
alarms to start and end a scheduled block on time. Alarms are scheduled only for
reminders and routines the user creates, and are cancelled when they are deleted.
The app keeps working without this permission and warns the user instead.

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

### `ACCESS_COARSE_LOCATION` (optional, foreground-only)
**Feature:** Canvas — Glass Weather widget & live wallpaper.
**Justification:** Requested only when the user taps "Use current location" in
Canvas -> Daily Widgets to detect their city and fetch live local weather from
Open-Meteo. City-level coarse location only; never collected in the background,
never shared for ads, and users can search and pick a city manually without
granting this permission.

### Device Admin (`BIND_DEVICE_ADMIN`) — NOT in the first production release
Anti-theft protection (device admin + Settings locked behind the PIN) is switched
off for v1 (`AntiTheftProtection.AVAILABLE = false`, receiver removed from the
manifest) to avoid the likeliest Play rejection. Nothing to declare for v1. The
text below is kept for when it returns in a later update.

<details><summary>Saved justification for the later release</summary>

**Feature:** Anti-theft protection (optional, off by default).
**Justification:** DayKit's App Lock protects the user's apps from someone else
using their phone. Without anti-theft protection, that person could simply
uninstall DayKit to get around it. When the user turns this on, DayKit registers
as a Device Administrator (so it cannot be uninstalled) and locks the system
Settings app behind the DayKit PIN (so the admin cannot be deactivated by someone
else). It requests no device-admin policies (`<uses-policies />` is empty) and
accesses no personal files, messages, location, or camera.

The owner can always remove it:
- **Normally:** DayKit > Settings > Security & Privacy > Anti-theft Protection,
  confirmed with the DayKit PIN.
- **If the PIN is forgotten:** the Settings lock screen shows "Forgot PIN?", which
  verifies the phone's own screen lock (Android BiometricPrompt with
  `DEVICE_CREDENTIAL`) and then turns protection off without erasing any data.

Before activation the user sees an in-app disclosure explaining exactly what is
locked and how to turn it off, then confirms on the system device-admin screen.
It can't be enabled unless the phone has a screen lock, so the forgot-PIN path
always works.

**Reviewer notes (paste into App access):** Test PIN: `<fill in>`. To uninstall
after enabling: open DayKit > Settings > Security & Privacy > turn off
Anti-theft Protection > enter the PIN. Or open the phone's Settings, tap
"Forgot PIN?" and confirm the device screen lock.

</details>

### `RECEIVE_BOOT_COMPLETED`
**Feature:** App Lock persistence.
**Justification:** Restarts the App Lock monitor service after a reboot or app
update so locked apps stay locked. The receiver validates the action and does no
sensitive work on a spoofed intent.

### `USE_FULL_SCREEN_INTENT`
**Feature:** Reminder alarms.
**Declaration:** choose the alarm use case.
**Justification:** Shows a full-screen alarm UI (with screen-on) when a reminder
the user set as an alarm goes off, matching alarm-clock behavior. Users can turn
full-screen alerts off in Settings > Notifications. If Play does not approve
default access, Android 14+ users grant it themselves from that same screen and
reminders still arrive as normal high-priority notifications.

### `USE_BIOMETRIC`, `POST_NOTIFICATIONS`, `INTERNET`, `HIDE_OVERLAY_WINDOWS`
Standard permissions. Biometric = optional unlock for locked tools.
Notifications = reminders/habits/app alerts (user-enabled). Internet = Google
Drive backup/restore only. Hide overlay windows = while a PIN or password prompt
is on screen, DayKit hides other apps' overlays so they cannot draw a fake
keypad over it (normal permission, granted at install, no declaration needed).

---

## 2. Data safety form — summary answers

**Data collected / shared with third parties:** None for advertising or
analytics. No advertising ID. No data sold.

**Data handling:**
- All user content (Key Store, Secure Notes, Expenses, Habits, App Lock list,
  File Vault media) is stored locally, encrypted (SQLCipher DB; File Vault files
  AES-256-GCM with per-file keys; Key Store, Notes and vault keys additionally
  under a key derived from the user's PIN/password).
- The only data that leaves the device is an **encrypted backup file** the user
  chooses to upload to **their own Google Drive**. It is encrypted on-device with
  a user-chosen password before upload; DayKit uploads no plaintext content.
- Google account email is stored locally to identify the Drive backup account.

**Encryption in transit:** Yes (Google Drive APIs over HTTPS).
**Encryption at rest:** Yes (see above).
**Data deletion:** User can delete data in-app, clear app data, or uninstall.
Deleted notes, Key Store entries and vault files go to an on-device "Recently
deleted" bin (still encrypted) and are erased for good after 30 days, or
immediately if the user empties the bin.
Drive backup files are deleted by the user from their Drive (or via retention,
which keeps only recent backups).

**Account / third-party:** Google Sign-In + Google Drive API (scope limited to
app-created backup files) for the optional backup feature.

---

## 3. Pre-upload checklist
- [ ] Release keystore created and `local.properties` signing vars filled in.
- [ ] `./gradlew :app:bundleRelease` (AAB) or `assembleRelease` produces a signed artifact.
- [ ] Privacy policy URL live: https://rosmox.com/products/daykit/privacy-policy
      (website copy must match the in-app `PrivacyPolicyScreen` text)
- [ ] Data safety form completed per section 2.
- [ ] Permission declarations submitted per section 1 (expect manual review for
      special-use FGS + full-screen intent + usage access).
- [ ] Android OAuth client for the **Play app signing** SHA-1 added in Google Cloud,
      and the OAuth consent screen published to "In production".
- [ ] `versionCode` / `versionName` bumped as intended (currently 11 / "1.1.1").
