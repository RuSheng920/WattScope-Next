# Permissions & troubleshooting

WattScope Next needs six permissions. Missing one never crashes the app — the corresponding feature just fails silently, so check yourself against this table first.

## Permission list

### Root

**Why**: reading the QPT, `cpufreq`, `/proc/stat`, thermal-zone and devfreq sysfs nodes. Every sample completes inside a single `su` shell round-trip.

**Symptom when missing**: the dashboard banner reports that root is not granted, and almost every value shows `--`.

**How to fix**:

1. Confirm that `io.github.rusheng920.wattscope` has root in KernelSU / Magisk
2. Tap **Re-check** in the in-app permission center, or leave and re-enter the app
3. Root detection prefers the **real state of an already-established shell**, so re-granting in the root manager works without restarting the app; while probing, it polls every 1.5 s

### Display over other apps

**Why**: showing the floating ball and the OSD overlay.

**Symptom when missing**: enabling the floating ball in Settings does nothing; if the grant is revoked while tapping the notification, the app catches the exception instead of crashing.

**How to fix**: system Settings → Apps → Special app access → Display over other apps. Some ROMs also require "show background pop-ups".

### Notifications

**Why**: keeping the foreground service alive and showing the current recording state.

**Symptom when missing**: switching to another app while recording lets the system freeze sampling, and the curve gets gaps.

**How to fix**: on Android 13+ notifications are a runtime permission, granted separately in system settings.

### Usage access

**Why**: identifying the current foreground app (the recording target and the FPS follow target).

**Symptom when missing**: ball-triggered recording cannot determine its target app, and the FPS layer cannot follow.

**How to fix**: system Settings → Apps → Special app access → Usage access.

### Battery optimization whitelist

**Why**: stopping the system from freezing sampling once the screen is off.

**Symptom when missing**: gaps in the curve after the screen has been off for a while, and records that end too early.

**How to fix**: system Settings → Battery → App battery management → WattScope Next → allow background activity / unrestricted.

### Background keep-alive service (accessibility)

**Why**: keeps the process from being frozen in the background, especially while gaming.

ROM freezers (ColorOS app freezing, HyperOS tombstone) freeze the whole process group by uid — **even when a foreground service is running and even when the app is on the battery whitelist**. While frozen, the overlay is still composited on screen but touch events never reach the process: the bubble appears on top of the game and simply does not respond, down to missing the press-scale animation.

An accessibility service is permanently bound by `system_server`, so its process state is never `CACHED` and the freezer leaves it alone. Once enabled:

- The bubble and OSD are hosted by the accessibility service (`TYPE_ACCESSIBILITY_OVERLAY`), which **no longer needs the overlay permission**, and games do not swallow its touches
- The sampling coroutines live in the same process, so they are protected too

**Privacy boundary**: `canRetrieveWindowContent=false` — the service never reads screen content, never injects gestures, and never simulates taps. It only reads two metadata fields carried by window-state events (package name and window class name), used for one purpose: deciding which app is currently in the foreground. That check used to run `dumpsys activity` every 2 seconds; the event stream makes it free and makes "stop recording when the target app is left" more responsive. The package name lives in memory only — never written to disk, never uploaded.

**Symptom when missing**: everything still works, but the bubble may go dead after the app is backgrounded or while a game is running.

**How to fix**: Permission center → Background keep-alive service → open it; then enable "WattScope Next keep-alive & overlay" in the system accessibility list.

## Permission center

Settings → Service & permissions → Permission center is a per-item list of grant states:

- Every row shows "Granted / Not granted" with a button that jumps straight to the matching system page
- **While the page is visible it re-checks every 1.5 s**, so anything you change in system settings shows up when you come back
- A ring indicator at the top shows overall progress
- While root probing is still running the state reads "checking" and is **never falsely reported as denied**

## Symptom cheat sheet

| Symptom | Most likely cause |
| --- | --- |
| The dashboard always says permissions are missing, but the permission center shows everything granted | Root probing has not finished; wait 1–2 s, then tap **Re-check** |
| All zone power values are `--`, but temperature and FPS work | The device has no QPT nodes, or the `qpt` directory is not enabled |
| Device power shows `0.00 W` | The battery driver does not update `power_now` while discharging; power is computed as voltage × current — see [FAQ](/en/guide/faq) |
| Recording stops after one second | The target app has just gained focus and its foreground state is not stable yet. A 5 s grace window covers this; if it still happens, open an issue with your device model |
| Gaps in the curve after the screen turns off | Notifications or the battery whitelist is missing |
| Bubble taps do nothing in a game and it does not even scale down | The process is frozen by the ROM freezer. Enable **Background keep-alive service (accessibility)** |
| FPS stays 0 in a game | SurfaceFlinger does not report that surface and the fallback fails too; rendering paths differ a lot between games |
| Device power reads about half the measured value | A dual-cell phone reports only one rail; enable **Dual-cell device** in Settings → Sampling & power → Calibration |

## Still not right?

Open an [issue](https://github.com/RuSheng920/WattScope-Next/issues) with the following — it makes diagnosis much faster:

- Device model / platform codename / Android version / root solution
- A screenshot of the permission center
- The actual content of the relevant nodes, for example:

```bash
su -c 'ls /sys/class/powercap/qpt 2>/dev/null | head'
su -c 'cat /sys/class/power_supply/battery/power_now 2>/dev/null'
```
