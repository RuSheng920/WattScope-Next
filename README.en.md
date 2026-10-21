<p align="center">
  <img src="docs/app-icon.png" width="120" alt="WattScope Next" />
</p>

<h1 align="center">WattScope Next</h1>

<p align="center"><b>SoC power telemetry for Qualcomm platforms · rewritten</b></p>

<p align="center">Built on Qualcomm Power Telemetry: observe and record power, frequency, temperature and frame rate in real time, at close to zero runtime overhead.</p>

<p align="center">
  <a href="README.md">简体中文</a> · <b>English</b>
</p>

<p align="center">
  <a href="../../releases"><img src="https://img.shields.io/badge/Release-v1.2.5-2EA44F?logo=github&logoColor=white" alt="Release" /></a>
  <a href="https://watt.rusheng.top"><img src="https://img.shields.io/badge/Website-watt.rusheng.top-F23A2A" alt="Website" /></a>
  <a href="docs/power-reading.md"><img src="https://img.shields.io/badge/Docs-Docs-4285F4?logo=readthedocs&logoColor=white" alt="Docs" /></a>
  <img src="https://img.shields.io/badge/Android%2013%2B-3DDC84?logo=android&logoColor=white" alt="Android 13+" />
  <img src="https://img.shields.io/badge/Kotlin%202.3-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.3" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/MIUIX-FF6900?logo=xiaomi&logoColor=white" alt="MIUIX" />
  <img src="https://img.shields.io/badge/Room-003B57?logo=sqlite&logoColor=white" alt="Room" />
  <img src="https://img.shields.io/badge/libsu-4EAA25?logo=gnubash&logoColor=white" alt="libsu" />
</p>

---

## Introduction

WattScope-Next is a ground-up rewrite of [WattScope](https://github.com/Alita23333/WattScope). It keeps the original QPT telemetry model and record schema, but **the UI layer, sampling pipeline, FPS data source and permission flow have all been rewritten**, with one goal: the measuring tool itself should disturb the measurement as little as possible.

- **Overhead first**: one round of sampling per single `su` round-trip; the shell builtin `read` replaces `$(cat)` so each tick does not fork dozens of processes
- **Native design language**: the UI is built on the MIUIX design-language Compose component library (`top.yukonga.miuix.kmp`) rather than hand-drawn imitations
- **Trustworthy data**: timing is based on `elapsedRealtime`, so NTP clock adjustments cannot fake a power spike; FPS comes from frames actually presented by SurfaceFlinger
- **Separate package**: `io.github.rusheng920.wattscope`, installs alongside the original WattScope without overwriting its data

Verified on **Redmi K90 Pro Max (myron / SM8850 / Android 16)**.

## Features

### Power telemetry

| Capability | Source | Notes |
| --- | --- | --- |
| QPT zone power | `/sys/class/powercap/qpt` | 8 zones: soc / cpu-m / cpu-l / gpu / nsp / debug-0 / 2 / 4, both power and energy are read |
| CPU cluster frequency | dynamically enumerated `cpufreq` policies | No hard-coded cluster count; works on 2-cluster and 3-cluster platforms |
| Per-core usage | `/proc/stat` | Colored per cluster, Y axis in 10% steps |
| Device power | `power_now` / battery nodes | Falls back to voltage × current when the node is missing |
| Temperature | thermal zones | CPU / DDR / GPU / battery |
| GPU | devfreq + gpu busy | Dual-axis chart of frequency and usage |
| DDR frequency | devfreq | — |
| FPS | SurfaceFlinger `--latency` | Follows the foreground app, falls back to `dumpsys gfxinfo` |

### Recording and observation

- **Floating ball**: tap to record the current app and **stop automatically when you leave it**; long-press opens the control panel; drag and release to snap to an edge; mini / standard / large sizes; fades out when idle
- **OSD overlay**: 11 optional metrics (SOC / CPU-L / CPU-M / GPU power, GPU frequency and usage, FPS, CPU/GPU/DDR temperature, device power) in a two-column HUD; draggable, tap to cycle opacity, never written to records; slow sensors keep their last valid value
- **Record detail**: average power on top, globally synced crosshair with floating tooltip, per-chart averages, zone filter, JSON / CSV export
- **Background keep-alive**: while the UI is invisible previews drop to 1 Hz so curves stay continuous without hammering the hardware with the screen off

### Interface

Three main tabs: **Dashboard / Records / Settings**. The settings tab is a category index — Appearance & theme, Sampling & power, Overlay & OSD, FPS source, Service & permissions and About each open as their own page, and the permission center is a secondary page reached from there.

- Light, dark and follow-system themes, preference persisted
- Floating liquid-glass bottom bar (MIUIX `FloatingNavigationBar` + Backdrop live blur / edge refraction / inner shadow), three intensity levels, can fall back to a docked bar
- The permission center lists every grant state and jumps straight to the matching system page (root, overlay, notifications, usage access, battery whitelist), re-checking every 1.5 s while visible
- Predictive back: both in-tab swipe-back and the detail page animate with gesture progress
- **Chinese and English UI**, following the system by default and switchable in Settings → Appearance & theme (Android 13+ per-app language)

## Differences from WattScope 1.0

| Aspect | WattScope 1.0 | WattScope-Next |
| --- | --- | --- |
| UI | Hand-drawn MIUI lookalike components | MIUIX design-language library + three tabs + floating glass bar |
| Sampling | ~32 `fork/exec` per tick (`$(cat)`, `grep`, `awk`) | Shell builtin `read`, batched into a single `su` round-trip |
| Sampling rate | Every metric at the same rate | Fast/slow split: power at high rate, temperature/usage at lower rate |
| FPS | Primarily `dumpsys gfxinfo`, misses SurfaceView games | SurfaceFlinger presentation timestamps first, `gfxinfo` only as fallback |
| Foreground app | `dumpsys activity` on every sample | 2 s TTL cache, no longer filtered by `com.` / `android.` prefixes |
| Persistence | One commit per tick, serialized with sampling | Batched commits + WAL with `synchronous=NORMAL` |
| Detail page | Subscribes to whole tables, O(n²) queries while recording | Stride downsampling + background aggregation |
| Time base | `currentTimeMillis` | `elapsedRealtime` with a fixed phase, immune to clock jumps |
| Theme | Dark only | Three-state theme + floating glass bar (three intensities) |
| Permissions | Scattered checks | First-run onboarding + permission page in Settings, live refresh |

Database schema is v4 with a full 1→4 migration, so existing records carry over.

## Architecture

```
app/src/main/java/io/github/rusheng920/wattscope/
├── core/                 sampling layer (everything runs inside the root shell)
│   ├── root/             libsu wrapper, shell escaping
│   ├── qpt/              QPT zone enumeration, power/energy reads
│   ├── auxiliary/        frequency, usage, temperature, GPU, DDR, device power
│   ├── fps/              SurfaceFlinger frame rate
│   └── update/           GitHub Releases lookup and SemVer comparison
├── data/
│   ├── local/            Room entities, DAOs, migrations
│   └── repo/             MonitorRepository: sampling orchestration, persistence, export; UpdateRepository: update checks and download
├── domain/               domain models and enums (intervals, themes, OSD metrics)
├── service/              foreground service, floating ball, OSD (View layer; overlay/ holds the drawn ball and HUD)
└── ui/                   Compose + MIUIX screens and theme
```

Data flow:

```
MonitorRepository ──batched su shell sampling──> core/* Reader
        │
        ├─> Room (while recording, batched)
        └─> StateFlow (preview / OSD / floating ball)
                    │
                    └─> Compose UI, FloatView
```

Every sampling command escapes paths through `RootManager.escapeForShell()`. Whether a sysfs node exists is decided by "is the content non-empty", never by `-f` / `-s` (most nodes report `st_size` as 4096).

## Building

Requirements:

| Component | Version |
| --- | --- |
| JDK | 17 |
| Android SDK | compileSdk 36 / build-tools 36 |
| Gradle | 8.13 (via wrapper) |
| AGP / Kotlin / KSP | 8.13.2 / 2.3.21 / 2.3.12 |

```bash
./gradlew assembleDebug      # debug build
./gradlew assembleRelease    # release build (needs keystore.properties, unsigned when absent)
# output: app/build/outputs/apk/{debug,release}/
```

Notes:

- `gradle/wrapper/gradle-wrapper.properties` points to the Huawei Cloud mirror by default (`services.gradle.org` is unreachable on some networks); swap it back if you have normal access
- Release signing reads `keystore.properties` from the repository root (`storeFile` / `storePassword` / `keyAlias` / `keyPassword`); that file and `*.jks` / `*.keystore` are excluded in `.gitignore`
- Dependencies include JitPack (libsu), so the first build needs network access

## Usage

1. Root the device (KernelSU / Magisk) and grant root to WattScope Next
2. On first launch follow the onboarding to grant overlay, notifications, usage access and the battery whitelist
3. Tap **Start recording** on the dashboard, or use the floating ball to record the current foreground app; recording stops when you leave that app
4. Open the **Records** tab to inspect curves and statistics, and export JSON / CSV
5. **Settings → About** shows the version, checks for updates (stable / beta channels) and links to the repository and the official website

## Download

See [Releases](../../releases).

> The application ID is `io.github.rusheng920.wattscope`, independent from the original WattScope (`com.qpt.powermonitor`), so both can be installed side by side.

## Changelog

### v1.3.0

This release brings **in-app updates** to the About page and rounds out the project links.

#### Added

- **Automatic update checks**: with the switch on, every launch queries GitHub Releases for a newer version. When one is found, a dialog offers to download it and hands the APK to the system installer. Off by default — checking requires network access, so it stays your call
- **Stable / Beta channels**: the Beta channel considers both pre-releases and stable builds and picks the highest version, so a beta that ranks below the stable version never wins (`1.2.6-beta.2` will not replace `1.2.6`)
- **Official website link on the About page**: `https://watt.rusheng.top`
- Release notes are taken from the GitHub release body and stripped of Markdown before display

#### Changed

- The About page is regrouped into Brand / Update / Project; the GitHub and website rows show the full URL including the `https://` prefix instead of a truncated repository name
- Beta builds (`beta/` tags) are now also published as a **prerelease** Release — the in-app Beta channel reads the Releases API, while Actions artifacts are not anonymously downloadable

#### Notes

- Overwriting an install requires the "install unknown apps" permission; without it the app opens the matching system settings page
- Update checks use the anonymous GitHub API, which is rate limited to 60 requests per hour; one request per launch stays far below that

### v1.2.5

This release rebuilds the **Settings information architecture** and fixes a batch of readability issues found in an on-device walkthrough.

#### Added

- **Settings is now a category index plus six dedicated pages**: Appearance & theme, Sampling & power, Overlay & OSD, FPS source, Service & permissions and About each open as their own page. Each index row shows its current value on the right (e.g. "200 ms", "Dark", "Ready"), so you can read the state without entering the page
- **Dedicated top bar and back navigation on secondary pages**: the navigation bar collapses and the top bar becomes a back arrow plus page title. The system back gesture now works in layers (secondary page → index → first tab), with the same gesture-following scale on both

#### Changed

- The three liquid-glass options (bar toggle, intensity, pause live blur while recording) now live under Appearance & theme, alongside theme mode and language
- Sampling presets read `100 ms / 200 ms / 500 ms / 1000 ms`, so all four fit on one row on narrow screens
- Battery optimisation status is shown as a status pill, matching the "Permission center → Ready" pattern
- Mutually exclusive selectors are now chosen by rule: 2–3 short options use a segmented control, four or more (or long labels) use chips with wrapping

#### Fixed

- **Card text was nearly unreadable in dark mode**: text without an explicit colour fell back to Material's default black, about 1.1:1 contrast on a `#242424` card. `MiuixCard` now injects the content foreground colour, measuring around 11:1 and meeting WCAG AA
- "Scan layers" on the FPS page is a proper action row with a chevron instead of text that did not look tappable
- The fourth sampling preset is no longer pushed outside the viewport on narrow screens
- The About page uses the red-and-white flame icon again, and the GitHub row no longer wraps the repository name onto a second line

### v1.2.4

An **internationalisation** release: the UI, README and release notes all gain English, with Chinese remaining the default.

#### Added

- **Chinese and English UI**: all 219 UI strings now live in string resources — Chinese as the default, English in `values-en`. Settings → Appearance & theme → Language switches between System / 简体中文 / English (Android 13+ per-app language; the switch applies immediately and neither the sampling service nor an in-progress recording is affected). The app also appears under system Settings → Apps → Language
- **English README**: new `README.en.md`, cross-linked from the Chinese one
- **English docs site**: added the missing "Permissions & troubleshooting" page; Chinese and English navigation are now complete

#### Changed

- **Bilingual release notes**: the body now carries both Chinese and English (separated by `---`), assembled from the same version section in each README
- **Runtime status text stores a resource id**: after switching language the dashboard status line updates immediately instead of staying in the old language

### v1.2.3

This release makes records far more **readable**: every metric is now persisted at the same rate as SoC power, so detail-page charts share an identical time axis, and chart sections can be reordered. It also includes the floating-ball and dark-mode fixes validated in the betas (v1.2.3-beta.1~3).

#### Added

- **Custom chart ordering on the detail page**: a new "Sort" button next to the Charts heading. The nine sections — SOC power, FPS, device power, CPU cluster frequency, CPU core usage, GPU, DDR, zone power, temperature — each get ↑/↓ buttons and apply instantly. The order is persisted globally and shared by all records. The default follows an analysis path: total power → performance result → cause drill-down → per-zone detail → temperature
- **Forward-compatible chart order**: existing records are unaffected after upgrading; chart sections added in future versions are appended to the end automatically

#### Changed

- **Unified recording sample rate**: CPU cluster frequency, per-core usage, device power, GPU frequency/usage, temperature and FPS no longer run on a 1 s slow path — they follow the configured interval (200 ms by default) and land in the same tick as SoC power. All eight detail charts then share a point-for-point time axis. Sampling is still a single `su` round-trip (0 forks) and commits stay at roughly one per second, so the recording cost is essentially unchanged

#### Fixed

- **Floating-ball recordings stopped by themselves after ~1 s**: "start recording" and "is it still in the foreground" now use the same data source (root `dumpsys`), plus a 5 s grace window covering the moment the target app has just gained focus and the system's foreground state is not yet stable
- **Floating ball snapped to the screen edge after dragging**: it now stays where you drop it; the old behaviour is available via Settings → Floating ball → "Snap to edge on release"
- **Dashboard gradient did not follow a theme switch**: `HeroCard`'s `remember` cache key now includes the theme colors
- **Chips and progress-bar tracks were invisible in dark mode**: the theme layer raises MIUIX dark `surfaceContainerHigh` to #343434, keeping the official light value
- **Zone power percentage was truncated**: integer division replaced with floating-point rounding, so the number and the bar agree again

### v1.2.2

Focused on **dark-mode readability**, **dual-cell device support**, and a brand-new "flaming W" app icon.

#### Added

- **Dual-cell switch**: Settings → Sampling gains "Dual-cell device". Some dual-cell phones report only one cell's current, halving device power; enabling this sums both rails for live preview, OSD overlay and persisted records alike, with the setting stored locally
- **New app icon · flaming W**: an italic bold W (Watt) whose right stroke trails off as a flame (Snapdragon), with the middle peak sharpened into a lightning tip (power telemetry); red-and-white palette with a bright-to-deep red gradient. The adaptive foreground is ported proportionally from the master artwork, every vertex inside the 72 dp safe circle (no cropping under circle, squircle or teardrop masks), the flame's outer edge rebuilt with a quadratic Bézier; the Android 13+ monochrome layer matches the colored icon

#### Fixed

- **False "permissions missing" after relaunch**: root state is `null` until the async probe finishes and was treated as denied. Nothing is reported while probing now, and permission refresh runs every 1.5 s while the app is visible so grants reach the dashboard immediately
- **Dark summary text missed WCAG AA on light containers**: MIUIX's `onSurfaceVariantSummary` is 50% white, about 4.4:1 on a #343434 container; raised to 70% white, now above 7:1. MIUIX summary text and dividers are lifted once in the theme layer
- **In-card dividers were nearly invisible in dark mode**: `dividerLine` #393939 is only ~1.3:1 against a #242424 card; raised to #4A4A4A (~1.75:1)
- **Dark chips were indistinguishable from cards**: the unselected `SelectChip` state used MIUIX `surfaceContainerHigh`, identical to the card; it now follows the Material equivalent role, and `surfaceVariant` / `surfaceContainerHigh` are raised to #343434
- **Dark charts looked washed out**: grid lines and axes raised, status pill backgrounds recalibrated

#### Changed

- **Component library**: new `SurfaceTile`, `CircularProgress` and `AnimatedListItem`
- **Dashboard**: HeroCard gains a status pill and a full-width primary button; live bar-chart rows show each zone's share; the big number and its unit share a baseline
- **Permission center / onboarding**: a ring-shaped progress indicator with a centered count replaces the thin bar, step rows became a vertical timeline; the onboarding header icon is larger and centered on a dark gradient matching the dashboard
- **Settings**: the About card is now a centered app badge with version info, and the permission-center entry shows a ready pill
- **Overlays**: the recording ring moved outward and glows more; OSD empty state got larger, higher-contrast text

### v1.2.1

Real-world fixes for **overlay visualization and the sampling pipeline**. Everything developed after v1.2.0 is folded in here.

#### Added

- **Three floating-ball sizes**: mini 40 / standard 48 / large 56 dp, noticeably smaller than before, applied instantly from Settings
- **Edge snapping**: releasing a drag sticks the ball to the nearest screen edge, position remembered locally, fading to 55% after 7 s idle
- **Two-column OSD HUD**: left-aligned labels, right-aligned monospaced values, a REC / LIVE dot in the title row; tap to cycle three opacity levels
- **Long-press control panel**: shows live SOC power and FPS at the top

#### Fixed

- **Shell syntax error in the merged sampling command**: each QPT fragment ended with `"; "`, producing `; ;` once concatenated with the auxiliary command, and the shell discarded the whole line as a syntax error — temperature / GPU frequency and usage / DDR / device power **were never sampled**, and every fifth recording tick also lost a batch of QPT samples
- **Live data only refreshed while recording**: the preview and OSD loops deferred to each other, and the OSD loop gave up permanently if opened before device scanning finished. They are now one loop: QPT at the configured interval, auxiliary metrics and FPS at 1 Hz, all refreshed by a single `su` round-trip
- **Device power stuck at `0.00 W` / `--`**: many BMS drivers only update `power_now` while charging and report a literal `0` on discharge. Following OpenMonitor (1orz/OpenMonitor), it is computed as `current_now × voltage_now` by probing `/sys/class/power_supply/{battery, Battery, bms, hisi_bk_battery}` with unit auto-detection; `power_now` is only the last resort
- The live bar chart rounded all four corners; now only the top two (bars grow from the baseline)
- Disabling the floating ball left the view attached (an animation callback referenced a cleared field); the panel kept TextView references after closing; overlay `removeView` lacked exception protection
- Tapping the notification could throw `SecurityException` when overlay permission had been revoked; the notification icon now uses the monochrome app icon
- The permission center's buttons were squeezed on narrow screens or large fonts; pills and buttons now use a flexible layout

#### Changed

- Liquid glass reworked: surface tint from 26%–56% down to 5%–18%, blur from 6–17 dp down to 2–8 dp, using `colorControls` plus edge refraction and an inner shadow to convey thickness; a very subtle vertical gradient in the page background gives the glass something to refract
- Zone order unified as SOC → CPU-L (big cores) → CPU-M (little cores) → GPU → NSP across dashboard, detail page, OSD and the average-power card
- OSD metrics in Settings became checkable pills, floating-ball size selection was added, empty states became icon + title + description, dividers unified on MIUIX `dividerLine`

### v1.2.0

- **Liquid glass rework**: the bottom bar uses Backdrop's `colorControls` with light blur and edge refraction; surface tint from 26%–56% down to 5%–18%, plus an inner shadow and default ambient highlight — no more frosted-acrylic look
- **Smaller ball with snapping**: mini / standard / large (36 / 44 / 54 dp, 44 by default), auto-snaps to the nearest edge on release, fades to 55% after 7 s idle, position remembered locally
- **Redrawn ball**: dark glass sphere with a top highlight, gradient rim and recording ring, replacing the "▶" text button; tap to record, long-press for the control panel
- **OSD rework**: two-column HUD (left-aligned labels, right-aligned monospaced values), REC / LIVE dot in the title row, tap to cycle three opacity levels
- **UI details**: a very subtle vertical warm/cool gradient in the page background (something for the glass to refract), empty states as icon + title + description, OSD metrics as checkable pills, dividers unified on MIUIX `dividerLine`

### v1.1.0

- **First-run onboarding**: a dedicated permission page; the app no longer requests notifications or overlay permission on launch
- **Information architecture**: the bottom bar is now Dashboard / Records / Settings, the permission center moved into Settings as a secondary page with predictive back
- **Floating glass bottom bar**: built on MIUIX `FloatingNavigationBar`, three intensity levels, can fall back to a docked bar
- **Live power chart**: the dashboard switched from a line chart to per-zone bars with peak labels, value labels and adaptive ticks
- **Root detection fix**: prefers the state of an already-established shell, so re-granting in the root manager works without restarting the app
- **Overlay visuals**: the ball and OSD use solid translucency plus a 1 dp rim, dropping the gradient glass layer to cut constant GPU redraw cost
- **Naming and icon**: the app is uniformly named WattScope Next, with a redrawn adaptive icon (including the monochrome layer)

### v1.0.0

- First public release

## Known limitations

- QPT nodes come from the vendor BSP; devices that are not Qualcomm-based or do not expose powercap can only record frequency, temperature, FPS and other auxiliary metrics
- Battery temperature and GPU busy nodes are named differently on some devices; the corresponding charts simply hide when absent
- The floating ball and OSD never use live Gaussian blur (per-frame blur keeps the GPU busy and pollutes the power measurement); they rely on a static liquid-glass backing instead
- Switching from the original WattScope is a fresh install: different package ID, history is not migrated

## Documentation

- [`docs/power-reading.md`](docs/power-reading.md) — how QPT zones and device power are read, node semantics and measured data (in Chinese)
- [`docs/power-review-recording.md`](docs/power-review-recording.md) — power analysis of the recording pipeline and the optimizations that landed (in Chinese)

## Credits and license

This project is a derivative of [Alita23333/WattScope](https://github.com/Alita23333/WattScope); the telemetry model and record schema come from the original. Thanks to its author.

For learning and research only, not for commercial use; keep the original author and this repository credited when redistributing.
