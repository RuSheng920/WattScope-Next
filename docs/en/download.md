# Download

Builds are hosted on GitHub Releases, signed with a fixed key.

<div class="dl-card">

<div>

### Current version <span class="dl-ver">v1.2.3</span>

- Android 13 or newer
- Qualcomm platform, rooted (KernelSU / Magisk / APatch)
- App ID `io.github.rusheng920.wattscope` — installs alongside the original WattScope

</div>

<div class="dl-actions">

<a class="dl-btn" href="https://github.com/RuSheng920/WattScope-Next/releases/latest" target="_blank" rel="noopener">Open Releases</a>

<a class="dl-btn dl-btn-alt" href="https://github.com/RuSheng920/WattScope-Next/releases" target="_blank" rel="noopener">All versions</a>

</div>

</div>

## Steps

1. Download the latest `WattScope-Next-v*.apk`
2. Allow installs from unknown sources and install
3. Walk through the permission onboarding (root, overlay, notifications, usage access, battery whitelist)
4. Return to the dashboard and hit Start recording

Read [Install & setup](/en/guide/getting-started) for what each permission is for and what breaks without it.

## Verify the signature

```bash
apksigner verify --print-certs WattScope-Next-v1.2.3.apk
```

Certificate SHA-256: `a118fa78…47141d`.

::: warning Do not install from mirrors
This app needs root. An APK with a mismatched signature means handing root to unknown code — download only from this repository's Releases.
:::

## Beta builds

Betas are not published as Releases; their artifacts live on GitHub Actions:

1. Open [Actions](https://github.com/RuSheng920/WattScope-Next/actions)
2. Pick the most recent run triggered by a `beta/*` tag
3. Download the APK from the Artifacts section (requires a GitHub login)

Betas share the release key, so they install over a stable build. Downgrading may require an uninstall first.

<style scoped>
.dl-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin: 24px 0 8px;
  padding: 24px 28px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 14px;
  background: var(--vp-c-bg-soft);
}

.dl-card h3 {
  margin: 0 0 10px;
  padding: 0;
  border: none;
  font-size: 18px;
}

.dl-ver {
  font-family: var(--vp-font-family-mono);
  color: var(--vp-c-brand-1);
}

.dl-card ul {
  margin: 0;
  padding-left: 18px;
  font-size: 14px;
  color: var(--vp-c-text-2);
}

.dl-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.dl-btn {
  display: inline-flex;
  align-items: center;
  height: 42px;
  padding: 0 22px;
  border-radius: 1000px;
  background: var(--vp-c-brand-2);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: background 0.2s ease, transform 0.2s ease;
}

.dl-btn:hover {
  background: var(--vp-c-brand-3);
  transform: translateY(-1px);
}

.dl-btn-alt {
  background: transparent;
  color: var(--vp-c-text-1);
  border: 1px solid var(--vp-c-divider);
}

.dl-btn-alt:hover {
  background: var(--vp-c-default-soft);
}
</style>
