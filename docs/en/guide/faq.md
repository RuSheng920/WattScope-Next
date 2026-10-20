# FAQ

## Can I use it without QPT nodes?

Yes. QPT only powers the **per-zone** view. Without it you still get:

- CPU cluster frequencies and per-core utilisation
- GPU frequency / busy and DDR frequency
- Four thermal zones (CPU / DDR / GPU / battery)
- Wall power (via `power_now` or voltage × current)
- FPS and foreground-app recording

Only the zone bar chart and zone curves disappear.

```bash
su -c 'ls /sys/class/powercap/qpt'
```

If the directory is missing, there are no QPT nodes; if it exists but is empty, the vendor did not enable them.

## Wall power is stuck at 0.00 W

Not a bug. Many BMS drivers only refresh `power_now` while charging and report a literal `0` on discharge.

WattScope Next therefore:

1. Probes `/sys/class/power_supply/{battery, Battery, bms, hisi_bk_battery}`
2. Computes `current_now × voltage_now`, adapting units to the physical magnitude
3. Falls back to `power_now` only as a last resort

If all three fail you see `--`.

## Dual-cell devices read about half the real power

Some dual-cell BMS drivers report only one cell's current. Enable **Settings → Sampling → Dual-cell device** to sum both paths — live preview, OSD and stored data all follow.

## FPS shows 0 in games

The primary source is **SurfaceFlinger presentation timestamps**; `dumpsys gfxinfo` is only a fallback. A few games use compositing paths that neither source can see, so FPS reads 0 while everything else works. Version 1.0 relied on `gfxinfo` first, which made this far more common.

## How much does recording itself cost?

At 5 Hz the remaining cost comes from the shell sample, persistence and preview refresh. After the rewrite: **zero forks** per sample (1.0 forked ~32 times), batched writes (~1 transaction/s), and FPS/foreground detection decoupled from the sampling tick.

## Can it coexist with the original WattScope?

Yes. Different package IDs (`io.github.rusheng920.wattscope` vs `com.qpt.powermonitor`), fully independent data. They do not share history either — switching means starting fresh.

## Where is my data stored?

In a local Room database inside the app's private directory. Exports (JSON / CSV) are started manually by you. Nothing is uploaded.

## Which sample interval should I use?

| Use case | Suggestion |
| --- | --- |
| Casual monitoring, frame-rate drops | 200 ms (default) |
| Long runs (1 h+) | 500 ms – 1 s: lighter, still dense enough |
| Catching transient spikes | 100 ms, at the cost of the tool's own footprint becoming visible |

::: tip Everything shares one rate
Since v1.2.3, CPU frequencies, per-core utilisation, wall power, GPU, thermals and FPS are no longer sampled on a slow 1 s cadence — they all follow your interval and land in the same tick as SoC power, so every chart aligns point by point.
:::

## Why Android 13+ only?

`minSdk` is 33. Older versions are neither adapted nor verified; permission and API differences would produce unpredictable behaviour, so no support is claimed.

## Why aren't the overlays frosted glass?

Per-frame blur keeps the GPU busy and pollutes the measurement. The overlays use a static liquid-glass layer; only the in-app nav bar uses live blur — and even that degrades while recording.

## Non-Qualcomm platforms?

Per-zone power needs Qualcomm QPT. MediaTek and Samsung devices have no such node and only get the secondary metrics. Node names differ across vendor ROMs; missing charts hide themselves automatically.
