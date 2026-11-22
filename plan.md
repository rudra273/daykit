# DayKit UI/UX Upgrade Plan — Apple HIG & Liquid Glass Standards

> **Device Audited:** Samsung Galaxy S24 Ultra (`SM_S928B`, `1080×2340`) via ADB across **10 live screenshots** in Light Mode & Dark Mode (Home, Today, Settings, Appearance, Focus, Habits).  
> **Design References:** Apple Human Interface Guidelines ([Color](https://developer.apple.com/design/human-interface-guidelines/color), [Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass), [Icons](https://developer.apple.com/design/human-interface-guidelines/icons), [Motion](https://developer.apple.com/design/human-interface-guidelines/motion), [Dark Mode](https://developer.apple.com/design/human-interface-guidelines/dark-mode), [Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)) harmonized with DayKit's new **Hinge Coast** app icon (`#0088FF` blue · `#00C0E8` cyan · `#00C8B3` mint · `#F8F3EB` warm ivory).

---

## 1. Live UI Audit Findings (From ADB Screenshots + Codebase)

Every finding below was observed directly on the live connected device and verified in code:

### Visual & Layout Issues Observed on Device
1. **Bottom Navigation Bar Bleed-Through (Home, Today, Settings — Light & Dark)**
   - In `Settings`, `"Privacy Policy"` collides visibly behind the Home & Today tab icons; in `Today`, `"Up next"` bleeds through; in `Home`, `"Device Manager"` and `"Document Scanner"` bleed through.
   - Cause: `RootScaffold.kt` uses a full-width `92–94%` translucent solid wash (`chromeBackground()`) with no real blur/elevation separation.
2. **Dark Mode `AppSwitch` Muddy Thumb Bug (`Appearance` — Dark Mode)**
   - The active `"Haptic feedback"` switch renders a dark navy thumb (`onPrimary = #10213A`) on a medium blue track (`#216BD6`), making the thumb nearly disappear in Dark Mode.
3. **Dark Mode Flat/Muddy Contrast (`Home`, `Settings`, `Appearance`, `Focus`, `Habits` — Dark Mode)**
   - Legacy Facebook charcoal (`#18191A` base + `#252729` cards) feels washed out on OLED displays.
   - `14%` alpha accent containers on `#252729` turn orange/red/yellow tiles into muddy brown/maroon (`Light`, `Focus`, `Reminders`, `Lock now`).
   - `AppBottomSheet` & `AppAlertDialog` reuse `extendedColors.card`, so sheets blend into the underlying page instead of stepping up in elevation.
4. **Top Bar Left-Edge Misalignment (`Home`, `Today`, `Settings`)**
   - `AppTopBar` uses `padding(horizontal = Spacing.sm)` (`8.dp`), pushing `"DayKit"`, `"Today"`, and `"Settings"` `8.dp` further left than the `16.dp` (`Spacing.lg`) section headers and cards directly below them.
5. **Bottom-Heavy Home Tool Cards (`Home` — Light & Dark)**
   - `ToolCard` reserves dead blank vertical space (`reservedDescriptionHeight`) inside 2-column cards, leaving excessive empty white/charcoal padding below tiny `32.dp` pastel icon boxes.
6. **Today Hero Ring Text Clipping (`Today` — Light & Dark)**
   - `"33% of today"` touches the inner orange progress ring edges.
7. **Settings & Appearance Inset Dividers + Tall Radio Stacks (`Settings`, `Appearance`)**
   - `RowDivider(startIndent = Spacing.lg)` cuts underneath the leading icon tiles instead of aligning cleanly under the text column (`60.dp`, Apple Settings standard).
   - `Appearance` uses 6 full-height radio rows for Theme (3) + Card Style (3), pushing content off-screen where sleek Apple Segmented Controls fit in a fraction of the space.
8. **Habits & Focus Polish (`Focus`, `Habits` — Dark Mode)**
   - `LinearProgressIndicator` in `Habits` shows Material 3's stray trailing stop-indicator dot on every habit bar (`Today`, `AI/Ml`, `Gym`).
   - `Habits` top motivational quote card eats prime above-the-fold height, the `Breaking` card sits half-width with an empty right column, and the square blue `+` FAB overlaps the bottom card.
   - `Focus` shows identical-looking faded disabled switches for both `"Locked till midnight"` apps and `"Paused"` apps instead of distinct status badges/icons; `"Short video blocker"` and `"Suggest apps to block"` float as two separate cards instead of one grouped panel.
9. **Motion & Accessibility Gaps (Codebase Audit)**
   - Zero `spring()` physics anywhere in the app; `DayKitNavHost` uses `EnterTransition.None` / `ExitTransition.None` for all tool & settings navigation.
   - Zero `ANIMATOR_DURATION_SCALE` (Reduce Motion) or Reduce Transparency handling.
   - `StatusPill` warning color (`#F7B928` on `#FEF3D7`) fails WCAG AA (~1.6:1 contrast).
   - `bodySmall` (`11.sp`) and `labelSmall` (`10.sp`) fall below comfortable readability; compact buttons (`36.dp`), `FilterChipButton`, and `.scale(0.78f)` switches risk undersized touch targets.
   - `SectionHeader` lacks `heading()` accessibility semantics.
10. **Unnecessary / Verbose Text to Replace with Icons & Visuals**
    - `AboutAppScreen.kt`: 10 long paragraphs of raw prose + stale hardcoded `"Version 1.0.8"` (app is `1.1.1`) and no Hinge Coast brand hero.
    - `DnsManagerScreen.kt`: verbose hero paragraph + 4-step text card that can become crisp icon trust pills + 3-icon visual stepper.
    - `DocumentScannerScreen.kt`: multi-sentence prose replaceable by a crisp viewfinder graphic + 3 icon chips (`Up to 20p`, `Auto-crop`, `PDF`).
    - `HomeScreen.kt` Edit Mode banner + redundant `SettingsFootnote`s & wordy subtitles across Settings sub-pages.

---

## 2. Decisions Needed Before Phase 1

Please confirm your preferences on these 4 choices (or approve the **Recommended** defaults):

| # | Decision | Recommended (Apple HIG) | Alternative |
| --- | --- | --- | --- |
| 1 | **Dark Mode Base** | **Pure OLED Black (`#000000`)** → `#1C1C1E` cards → `#2C2C2E` elevated sheets | Deep Charcoal (`#0E1013`) → `#1B1D22` cards |
| 2 | **Primary Accent** | **Hinge Coast Blue (`#0088FF` light / `#0A84FF` dark)** + Cyan/Mint harmony | Pure Apple System Blue (`#007AFF` / `#0A84FF`) |
| 3 | **In-App Tool Icon Tiles** | **Vibrant Apple Squircle Tiles** (rich saturated/subtle-gradient fill + white glyph) | Refined Soft-Tinted Tiles (higher-clarity pastel + saturated glyph) |
| 4 | **Bottom Navigation Bar** | **Floating Liquid Glass Pill Dock** (iOS 26 style floating capsule + sliding active pill) | Full-width frosted edge-to-edge bar with hairline top border |

---

## 3. Phased Upgrade Plan (Review & Approve 1 Phase at a Time)

### Phase 1 — Color System, Dark/Light Calibration, Typography & Accessibility Foundation
**Scope:** Design tokens & accessibility primitives only — zero risky layout rewrites yet, immediate visual uplift across Light & Dark modes.

- **[MODIFY] `core/designsystem/Color.kt`**
  - Replace legacy "Meta Classic" Facebook palette with Apple HIG semantic Light/Dark tokens harmonized with Hinge Coast:
    - **Light:** Grouped base `#F2F2F7`, Cards `#FFFFFF`, Elevated `#FFFFFF`, Input/Field `#E9E9EE`, Hairline divider `#3C3C43` (18%), Primary label `#000000`, Secondary `#57575C` (>= 6.5:1), Tertiary `#3C3C43` (42%), Primary action `#007AE5` (4.6:1 on white) / accent `#0088FF`.
    - **Dark:** OLED Base `#000000`, Cards `#1C1C1E`, Elevated Sheets/Menus `#2C2C2E`, Input/Field `#2C2C2E`, Hairline divider `#545458` (42%), Primary label `#FFFFFF`, Secondary `#A1A1A8` (>= 7.5:1), Primary `#0A84FF` (5.1:1 on `#1C1C1E`), **`onPrimary = #FFFFFF`** (fixes the dark-navy `AppSwitch` thumb bug!).
    - **WCAG-Safe Status Colors:** Light Warning `#B25000` on `#FFF4E5` (5.1:1!), Light Success `#248A3D`, Light Danger `#D70015`; Dark Success `#30D158`, Dark Warning `#FF9F0A`, Dark Danger `#FF453A`.
    - **Calibrated Accents (11 hues including Hinge Coast `cyan` & `mint`):** iOS Light & Dark calibrated system hues.
  - Add `elevatedCard`, `textTertiary`, `hairlineBorder`, and `warningOnContainer` to `ExtendedColors`.
- **[MODIFY] `res/values/colors.xml` & `res/values-night/colors.xml`**
  - Sync native window/status/widget colors (`#F2F2F7` / `#000000`, `#0088FF` / `#0A84FF`) to prevent cold-start color flashes.
- **[MODIFY] `core/designsystem/Type.kt` & `core/designsystem/Shape.kt`**
  - Raise caption/label floor from `10.sp`/`11.sp` to `12.sp`; tighten display/headline tracking; add `TabularNumeric` helper (`fontFeatureSettings = "tnum"`).
  - Concentric shape scale (`extraSmall = 8.dp`, `small = 10.dp`, `medium = 12.dp`, `large = 18.dp`, `extraLarge = 28.dp`).
- **[NEW] `core/designsystem/A11y.kt` & [MODIFY] `core/designsystem/Theme.kt`**
  - Add `LocalReduceMotion` (observes `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`) and `LocalReduceTransparency` (high-contrast / solid fallback).
- **[MODIFY] `ListItems.kt`, `Buttons.kt`, `SettingsComponents.kt`**
  - Add `semantics { heading() }` to `SectionHeader`.
  - Guarantee `>= 48.dp` touch target on compact buttons/chips; fix `StatusPill` contrast.
- **[NEW] JVM Unit Test `ColorContrastTest.kt`**
  - Automatically verifies WCAG AA contrast ratios (>= 4.5:1 text, >= 3.0:1 UI) across Light & Dark tokens.

---

### Phase 2 — Floating Liquid Glass Chrome & Navigation Architecture
**Scope:** Fix the bottom bar bleed-through, top bar alignment, and modal elevation hierarchy.

- **[MODIFY] `navigation/RootScaffold.kt`**
  - Replace full-width translucent slab with a **Floating Liquid Glass Pill Dock** (`RoundedCornerShape(32.dp)`, floating `12.dp` above the nav inset) with subtle bottom scroll-edge fade gradient, specular rim, and spring-sliding active tab capsule.
  - Automatic solid elevated pill fallback under `LocalReduceTransparency`.
- **[MODIFY] `core/designsystem/components/TopBar.kt` & `Chrome.kt`**
  - Fix left-edge alignment so titles align flush with `Spacing.lg` (`16.dp`) content cards below (`Home`, `Today`, `Settings`).
  - Add scroll-reactive `0.5.dp` hairline / soft edge separation and capsule inline search bar in `SearchAppTopBar`.
  - Add Hinge Coast brand mark slot on `HomeScreen` header.
- **[MODIFY] `core/designsystem/components/Glass.kt` & `Sheets.kt`**
  - Tune `GlassTokens` for `#F2F2F7` Light and `#000000` Dark (eliminating washed-out dark glass).
  - Upgrade `AppBottomSheet` & `AppAlertDialog` to `extendedColors.elevated` (`#2C2C2E` in dark mode), `28.dp` top corners, slim Apple drag handle (`36.dp × 4.dp`), and `0.5.dp` subtle dark-mode top rim.

---

### Phase 3 — In-App Iconography Overhaul & Core Component Polish
**Scope:** Upgrade every in-app icon (main launcher icon stays Hinge Coast) and rebuild core UI primitives to Apple precision.

- **[NEW] `res/drawable/ic_daykit_mark.xml` + Custom Crisp Tool Vectors**
  - Add the Hinge Coast vector mark for in-app branding (`Home` header, `About`, `ToolUnlockScreen`).
  - Upgrade weak/generic tool glyphs across `HomeTools.kt` & `SettingsScreen.kt` (Habits, Dayflow, Event Light, Notes, File Vault, Canvas, DNS, Focus).
- **[MODIFY] `core/designsystem/components/Cards.kt`**
  - Upgrade `AccentIconTile` to vibrant Apple squircle tiles with subtle luminance lift, crisp white glyphs, and concentric corner radius math (`outerRadius - padding`).
  - Add subtle `0.5.dp` dark-mode card hairline (`Color.White.copy(alpha = 0.06f)`) on `AppCard`.
- **[MODIFY] `Switches.kt`, `Chips.kt`, `ListItems.kt`, `Inputs.kt`, `Feedback.kt`**
  - **`AppSwitch`**: Replace blurry `.scale(0.78f)` M3 Switch with a crisp custom-drawn iOS-proportioned toggle (`50.dp × 30.dp` inside `48.dp` touch target, pure white elevated thumb in both Light & Dark modes, spring animation).
  - **`SegmentedControl`** *(NEW in `Chips.kt`)*: Apple-style sliding-thumb segmented pill for mode switches.
  - **`AppListRow` & `RowDivider`**: `0.5.dp` hairline dividers with text-aligned inset (`startIndent = 60.dp` when icon tile is present) + `trailingValue: String?` support (`10s ›`).
  - **`EmptyState`**: Layered concentric accent halo + crisp emblem + concise title + single primary CTA.

---

### Phase 4 — Motion Physics & Tactile Micro-Interactions
**Scope:** Smooth Apple-style spring physics everywhere, with automatic Reduce Motion compliance.

- **[NEW] `core/designsystem/Motion.kt`**
  - Spring presets (`NavSpring`, `ControlSpring`, `PressSpring`) + `Modifier.pressScale()` micro-interaction, all automatically downgrading to `fadeIn`/`snap` when `LocalReduceMotion.current == true`.
- **[MODIFY] `navigation/DayKitNavHost.kt`**
  - Replace `EnterTransition.None` / `ExitTransition.None` on tool & settings pushes with smooth iOS-style horizontal push/pop + `-22%` subtle parallax & scrim (predictive-back friendly, Reduce-Motion aware).
- **[MODIFY] `Cards.kt`, `Buttons.kt`, `RootScaffold.kt`, `HomeScreen.kt`**
  - Subtle spring press feedback (`0.97f`) on interactive cards/buttons and lifted spring scale on Home Edit Mode drag.

---

### Phase 5 — Hub Layouts & Text-to-Visual Decluttering (Home, Today, Settings)
**Scope:** Fix every layout imbalance observed in live screenshots and replace verbose prose with crisp visual elements across the 3 main hubs.

- **[MODIFY] `feature/home/ui/HomeScreen.kt`**
  - Remove artificial `reservedDescriptionHeight` blank gap; rebalance 2-column cards with `14.dp` padding and larger `40.dp` vibrant squircle icons.
  - Remove the verbose Edit Mode instruction banner (rely on drag handles + subtle edit state + `"Done"` pill).
  - Upgrade `HomeViewModeSheet` to use visual grid-column icons + `SegmentedControl`.
- **[MODIFY] `feature/today/ui/TodayScreen.kt` & `TodayCharts.kt`**
  - Fix Hero Ring center `"33% of today"` clipping; refine ring stroke/track contrast; use `TabularNumeric` for counts & timers; upgrade Quick Actions to vibrant compact squircle pills.
- **[MODIFY] `feature/settings/ui/SettingsScreen.kt`, `AppearanceScreen.kt`, `AboutAppScreen.kt`, & Settings Sub-Pages**
  - `AppearanceScreen`: Replace 3-row Theme radio stack and 3-row Card Style radio stack with compact Apple `SegmentedControl`s — entire Appearance screen fits cleanly with minimal scrolling.
  - Inset `0.5.dp` text-aligned dividers + trailing inline values (`10s ›`, `30s ›`) across Settings sub-pages; trim redundant `SettingsFootnote` prose.
  - `AboutAppScreen`: Replace 10 paragraphs of prose + stale `"Version 1.0.8"` with a Hinge Coast hero graphic, dynamic app version (`1.1.1`), privacy badges (`Local-First`, `AES-GCM`, `Zero Telemetry`), and compact scannable icon rows.

---

### Phase 6 — Tool-by-Tool Visual Polish & Copy Reduction (All 16 Tools)
**Scope:** Bring every individual tool screen up to the same Apple-grade visual & copy standard.

- **Productivity (`FocusScreen`, `HabitScreen`, `DayflowScreen`, `ReminderScreen`, `ExpenseScreen`)**:
  - `HabitScreen`: Remove M3 `LinearProgressIndicator` stop-indicator dots (`drawStopIndicator = {}`), make quote header slim/optional or unobtrusive, fix half-width `Breaking` card grid alignment, move `+` cleanly or upgrade FAB to circular Liquid Glass with scroll clearance.
  - `FocusScreen`: Replace faded disabled switches on `"Locked till midnight"` rows with crisp lock status badges/icons; group `"Short video blocker"` + `"Suggest apps to block"` into one clean inset-grouped card; refine mode pills in Dark Mode.
  - Apply `TabularNumeric`, `SegmentedControl`, and layered `EmptyState` across Dayflow, Reminders, and Expenses.
- **Utilities (`DnsManagerScreen`, `DocumentScannerScreen`, `EventLightScreen`, `ImageToolScreen`, `DeviceManagerScreen`, `CanvasScreen`, `EditorScreen`)**:
  - `DnsManagerScreen`: Replace hero paragraph + 4-step text card with 3 trust icon chips + visual 3-step icon flow + copy-to-checkmark morph.
  - `DocumentScannerScreen`: Replace prose paragraph with crisp illustrated viewfinder graphic + 3 icon chips (`20 pages`, `Auto-crop`, `PDF`).
  - Audit & replace hardcoded raw colors with semantic tokens where appropriate.
- **Security (`AppLockScreen`, `KeyStoreScreen`, `SecureNotesScreen`, `FileLockerScreen`, `ToolUnlockScreen`)**:
  - `AppLockScreen`: Upgrade `All / Locked / Unlocked` chips to `SegmentedControl`.
  - `ToolUnlockScreen`: Add Hinge Coast mark + crisp Apple PIN-dot spacing (preserving `HideOverlayWindows()` and `FLAG_SECURE`).
  - Unify layered `EmptyState` & concise copy across Key Store, Secure Notes, and File Vault.

---

## 4. Verification Workflow (Repeated After Every Phase)

1. **Automated Build & Unit Tests:**
   ```bash
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew :app:assembleDebug :app:testDebugUnitTest
   ```
2. **Live Device Install & ADB Visual Inspection (`SM_S928B`):**
   - Install updated debug build (`./gradlew :app:installDebug`).
   - Capture Light Mode & Dark Mode screenshots of every touched screen via `adb exec-out screencap -p`.
   - Verify Large Font (`fontScale >= 1.3`), Reduce Motion (`animator_duration_scale = 0`), and Flat / Clay / Liquid Glass card styles.
   - Present before/after screenshots for your approval before moving to the next phase.
