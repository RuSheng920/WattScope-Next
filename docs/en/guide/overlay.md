# Floating ball & OSD

Two overlays with different jobs: the ball **controls recording**, the OSD **shows live numbers**.

## Floating ball

| Gesture | Behaviour |
| --- | --- |
| Tap | Start / stop recording the foreground app |
| Long-press | Open the control panel (live SoC power and FPS on top) |
| Drag | Move it; it stays where you release it (enable "snap to edge" for the old behaviour) |
| Idle 7 s | Fades to 55% opacity |

The ball is `FLAG_NOT_FOCUSABLE` — **tapping it never steals focus**, so the recording target is always the app you were using, not WattScope itself.

### Recording linkage

Once a ball-started recording is running, leaving the target app stops it. Two details matter:

- **"Start recording" and "still in foreground?" use the same data source** (root `dumpsys`), so the two checks cannot disagree and stop a recording one second in
- A **5-second grace period** after start suppresses the "left app" check, covering the window where a freshly focused app has not settled in system state yet

### Sizes

Mini 40 / standard 48 / large 56 dp, standard by default, applied instantly. The ball is dark glass: top inner highlight, gradient rim, and an outer glow ring while recording.

## OSD overlay

Display only — **never written to recordings**. Its job is glancing at power while gaming, not replacing recording.

### Metrics

Any of 11, opt-in:

| Group | Metrics |
| --- | --- |
| Power | SOC, CPU-L, CPU-M, GPU, wall power |
| Performance | GPU frequency, GPU busy, FPS |
| Thermal | CPU, GPU, DDR |

### Layout and interaction

- **FPS gets its own row**: frame rate is the one metric you scan at a glance, so it renders as a 30sp monospace number instead of sharing a line
- **Two-column HUD**: labels left, values right-aligned in fixed-width cells, so digits do not jitter while you read
- Title row carries a **REC / LIVE** dot; while recording it shows `REC mm:ss`, so you never have to go back into the app to confirm
- Draggable anywhere
- Tap to cycle three opacity levels
- **Long-press to start / stop recording** (same as the bubble, no need to leave the game)
- Slow sensors (thermals) hold their last valid value instead of flashing `--`

## Why neither overlay uses live blur

Per-frame Gaussian blur keeps the GPU busy — which is exactly what pollutes the power you are measuring. Both overlays therefore use a static liquid-glass layer (translucency + 1 dp rim + inner highlight). Only the in-app nav bar uses Backdrop's live blur, because at that moment you are not measuring.

The same reasoning drives "reduce nav bar effects while recording", on by default.

## Troubleshooting

If the ball never appears, check the overlay grant first: [Permissions & troubleshooting](/en/guide/permissions).
