# Features

Three tabs: **Dashboard / Records / Settings**. The permission centre lives inside Settings.

## Dashboard

Everything about "this second" in one screen:

- **Total power** as the hero number, with units baseline-aligned
- **Status pills**: recording / previewing / permissions ready
- **Live per-zone bar chart**: SOC → CPU-L → CPU-M → GPU → NSP, each bar with a value label, peak marker and **share of total**, growing from the baseline
- **Secondary metrics**: CPU cluster frequencies, per-core utilisation (coloured by cluster, 10% gridlines), GPU frequency and busy, DDR frequency, four thermal zones, wall power

The preview loop refreshes at your sampling interval while visible; **when the UI is not visible it drops to 1 Hz** so curves stay continuous without burning power in the background.

Zone order is identical everywhere — dashboard, detail view, OSD and the average card — so you never have to re-map your eyes between screens.

## Records

The Records tab is your history:

- Each entry shows app icon, name, time range, duration and average power
- Tap through to the detail view
- Empty state is an icon, a title and a hint — not a blank page

### Detail view

| Capability | Notes |
| --- | --- |
| Average power pinned on top | See the overall level at a glance |
| Global synchronised crosshair | Move on one chart, all charts highlight the same instant |
| Floating tooltip | Exact values at that instant |
| Per-chart averages | Each chart has its own mean line |
| Zone filtering | Focus on one zone or cluster |
| Reorderable charts | Nine blocks with ↑ / ↓, persisted globally |
| Export | JSON (full fidelity) or CSV (analysis) |

Default order follows the analysis path: **total power → performance outcome → causes → zone breakdown → thermals**. That ordering is deliberate — conclusion first, then why.

::: tip Large recordings stay fast
The detail view uses stride downsampling plus background aggregation, so opening a long history record while recording does not saturate the database. Compared to 1.0's whole-table subscription the difference on long recordings is obvious.
:::

## Settings

### Sampling

- **Sample interval**: default 200 ms (5 Hz), adjustable
- **Dual-cell device**: some dual-cell BMS drivers report only one cell's current, halving wall power. Enable this to sum both paths — live preview, OSD and stored data all follow
- All metrics are stored at the same rate as SoC power, so every chart shares one time axis

### Theme

Dark / light / follow-system, persisted locally. The nav bar is a MIUIX `FloatingNavigationBar` rendered through Backdrop: live blur, edge refraction, inner shadow, three opacity levels, or fall back to a docked style.

::: warning Blur is disabled while recording
"Reduce nav bar effects while recording" is on by default. Per-frame blur keeps the GPU busy and pollutes the power you are measuring, so recording mode keeps only a static glass layer.
:::

### Floating ball and OSD

See [Floating ball & OSD](/en/guide/overlay).

### About

Shows the version and links to this repository. The permission centre entry lives here too, with a "ready" status pill.

### Language

The UI ships in Chinese and English. By default it follows the system language; Settings → Appearance lets you pin either one (Android 13+ per-app language), and the switch takes effect immediately without touching sampling.
