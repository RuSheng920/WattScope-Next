# What is WattScope Next

WattScope Next is a rewrite of [WattScope](https://github.com/Alita23333/WattScope) — a **power telemetry tool for rooted Qualcomm devices**. It keeps the original QPT telemetry model and recording schema, but the UI layer, sampling pipeline, FPS source and permission flow have all been rewritten.

In one sentence: **the measuring tool should disturb the measurement as little as possible.**

## Design principles

### Low overhead first

A full sample — every metric — completes inside a **single `su` round-trip**. The shell uses built-in `read` instead of `$(cat)` and avoids `grep` / `awk` subprocesses. Version 1.0 forked roughly 32 times per tick; this build forks zero times, so at 5 Hz that overhead is simply gone and the numbers you see are closer to the app's real draw.

### Native design language

The interface is built on the MIUIX Compose component library (`top.yukonga.miuix.kmp`) rather than hand-rolled lookalikes: three tabs (Dashboard / Records / Settings), a floating liquid-glass navigation bar, and predictive back gestures.

### Trustworthy data

- Time base is `elapsedRealtime`, so NTP corrections cannot create fake power spikes
- FPS comes from SurfaceFlinger presentation timestamps, not coarse `dumpsys gfxinfo` counters
- Foreground detection is cached for 2 s and does not filter by `com.` / `android.` package prefixes
- Every metric is stored at the same rate as SoC power, so all charts in the detail view share one time axis

### Separate package

The app ID is `io.github.rusheng920.wattscope`, independent from the original `com.qpt.powermonitor`. **Both can be installed side by side** without touching each other's data.

## Differences from WattScope 1.0

| Aspect | WattScope 1.0 | WattScope Next |
| --- | --- | --- |
| UI | Hand-drawn MIUI imitations | MIUIX component library, 3 tabs, glass nav bar |
| Sampling | ~32 `fork/exec` per tick | Shell built-in `read`, one `su` round-trip |
| Sample rate | One rate for everything | Fast/slow tiers: power fast, slow sensors throttled |
| FPS | `gfxinfo` primary, games often unmeasurable | SurfaceFlinger timestamps primary, `gfxinfo` fallback |
| Foreground app | Fresh `dumpsys activity` per sample | 2 s TTL cache, no package-prefix filtering |
| Persistence | Per-tick commit, serialised with sampling | Batched writes, WAL `synchronous=NORMAL` |
| Detail view | Subscribed to the whole table, O(n²) reads | Stride downsampling + background aggregation |
| Time base | `currentTimeMillis` | `elapsedRealtime` with fixed phase |
| Theme | Dark only | Three-state theme, glass nav bar (three opacities) |
| Permissions | Scattered checks | Onboarding + permission page with live status |

Database schema is v4 with full 1 → 4 migrations.

## Requirements and limits

::: warning Prerequisites
A **rooted Qualcomm device** (KernelSU / Magisk / APatch) whose BSP exposes the QPT nodes under `powercap`. Verified on Redmi K90 Pro Max (myron / SM8850 / Android 16).
:::

QPT nodes are supplied by the vendor BSP. On non-Qualcomm platforms, or devices where powercap is disabled, the app still records frequencies, thermal data and FPS — just without per-zone power.

Some devices name their battery temperature or GPU busy nodes differently; missing metrics simply hide the corresponding chart.

::: tip Coming from the original
The package name differs, so this is a clean install and history will not be migrated automatically. Schema v4 is compatible, so a JSON export can be reprocessed if you need it.
:::

## Next

- [Install & setup](/en/guide/getting-started)
- [Features](/en/guide/features)
- [Permissions & troubleshooting](/en/guide/permissions)
- [FAQ](/en/guide/faq)
