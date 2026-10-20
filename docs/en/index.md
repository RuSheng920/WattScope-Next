---
layout: home

hero:
  name: WattScope Next
  text: Qualcomm SoC power telemetry
  tagline: Real-time power, frequency, thermal and frame-rate monitoring built on Qualcomm Power Telemetry — with near-zero runtime overhead. The measuring tool should never disturb the measurement.
  image:
    src: /logo.svg
    alt: WattScope Next
  actions:
    - theme: brand
      text: Download
      link: /en/download
    - theme: alt
      text: Quick start
      link: /en/guide/getting-started
    - theme: alt
      text: GitHub
      link: https://github.com/RuSheng920/WattScope-Next

features:
  - title: Per-zone power
    details: Reads the QPT zones under powercap directly and attributes power to SOC / CPU-L / CPU-M / GPU / NSP instead of showing a single total.
  - title: Near-zero sampling cost
    details: One su round-trip per sample, shell built-in read instead of $(cat). Zero forks at 5 Hz, so the tool itself barely draws anything.
  - title: Floating ball recording
    details: Tap to start recording the foreground app, auto-stop when you leave it. Long-press for the control panel, three sizes, idle fade-out.
  - title: OSD overlay
    details: Pick any of 11 metrics, two-column HUD, draggable, tap to cycle opacity. Display only — never written to recordings.
  - title: Detail view
    details: Synchronised crosshair across every chart, floating tooltip, per-chart averages, zone filtering, reorderable charts, JSON / CSV export.
  - title: Native MIUIX design
    details: Built on the MIUIX component library — dark / light / system themes, floating liquid-glass navigation bar, predictive back.
---

<TelemetryPanel locale="en" />

## Start here

<div class="start-grid">

[**What is WattScope Next**](/en/guide/what-is-wattscope)
Positioning, design principles, differences from WattScope 1.0, and limitations.

[**Install & setup**](/en/guide/getting-started)
Requirements, permission onboarding, your first recording and export.

[**Floating ball & OSD**](/en/guide/overlay)
How the two overlays behave, and why neither uses live blur.

[**FAQ**](/en/guide/faq)
Missing QPT, zero wall power, invisible FPS, dual-cell devices and more.

</div>

<style scoped>
.start-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.start-grid > p {
  margin: 0;
  padding: 18px 20px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  background: var(--vp-c-bg-soft);
  font-size: 14px;
  line-height: 1.7;
  color: var(--vp-c-text-2);
  transition: border-color 0.25s ease;
}

.start-grid > p:hover {
  border-color: var(--vp-c-brand-1);
}

.start-grid a {
  display: block;
  margin-bottom: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--vp-c-text-1);
  text-decoration: none;
}

.start-grid a:hover {
  color: var(--vp-c-brand-1);
}
</style>
