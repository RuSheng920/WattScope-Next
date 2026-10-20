# Install & setup

From download to first export: about ten minutes.

## Requirements

| Item | Requirement |
| --- | --- |
| OS | Android 13 or newer |
| Platform | Qualcomm, with QPT nodes exposed (`/sys/class/powercap/qpt`) |
| Root | KernelSU / Magisk / APatch, permission grantable |
| Overlay | Screen overlay permission if you want the floating ball or OSD |

Without QPT nodes the app still installs and works — you simply lose per-zone power and keep frequencies, utilisation, thermals and FPS. See [FAQ](/en/guide/faq).

## Install

1. Grab the latest `WattScope-Next-v*.apk` from [Releases](https://github.com/RuSheng920/WattScope-Next/releases)
2. Install it directly — the app ID `io.github.rusheng920.wattscope` does not collide with the original WattScope
3. On first launch you land on the **permission onboarding page**; nothing is requested automatically

::: tip Download only from Releases
Builds are signed with a fixed release key (certificate SHA-256 `a118fa78…47141d`). Never hand root to an APK from an unverified mirror.
:::

## Permissions

Each row links to the matching system page; status is re-checked **every 1.5 s while the page is visible**.

| Permission | Purpose | If missing |
| --- | --- | --- |
| Root | Reads QPT, `cpufreq`, thermal and other sysfs nodes | Almost everything shows `--` |
| Screen overlay | Floating ball and OSD | In-app recording only |
| Notifications | Foreground service keeps recording alive | Sampling may be frozen |
| Usage access | Detects the foreground app (recording target) | Ball recording and FPS following break |
| Battery whitelist | Keeps sampling alive when the screen is off | Curves break after screen-off |

## First recording

::: code-group

```text [In-app]
Dashboard → Start recording → switch to the target app → come back → Stop
```

```text [Floating ball]
Enable the ball in Settings → switch to the target app → tap the ball to start
→ leaving that app stops the recording automatically
```

:::

The ball is `FLAG_NOT_FOCUSABLE`, so tapping it never steals focus — the recording target is always the app you were actually using.

::: warning 5-second grace period
For the first 5 seconds after a recording starts, no "app left" check is performed. A freshly focused app often has not settled in the system's foreground state yet; without the grace period recordings would stop themselves almost immediately.
:::

## Review and export

1. The record appears in the **Records** tab with app name, duration and average power
2. Open the detail view for full power, frequency, utilisation, thermal and FPS charts
3. Filter by zone and export as JSON (all fields) or CSV (spreadsheet-friendly)

Chart order is configurable: the **Sort** button next to the "Curves" heading offers ↑ / ↓ for all nine blocks, applied instantly and persisted globally.

## Next

- [Features](/en/guide/features)
- [Floating ball & OSD](/en/guide/overlay)
- [FAQ](/en/guide/faq)
