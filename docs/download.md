# 下载

安装包托管在 GitHub Releases，签名固定，随版本更新。

<div class="dl-card">

<div>

### 当前版本

<p class="dl-ver dl-ver-line">{{VERSION}}</p>

- Android 13 及以上
- 高通平台 + 已 root（KernelSU / Magisk / APatch）
- 应用 ID `io.github.rusheng920.wattscope`，可与原版 WattScope 共存

</div>

<div class="dl-actions">

<a class="dl-btn" href="https://github.com/RuSheng920/WattScope-Next/releases/latest" target="_blank" rel="noopener">前往 Releases 下载</a>

<a class="dl-btn dl-btn-alt" href="https://github.com/RuSheng920/WattScope-Next/releases" target="_blank" rel="noopener">历史版本</a>

</div>

</div>

## 安装步骤

1. 下载最新的 `WattScope-Next-v*.apk`
2. 允许「安装未知来源应用」后直接安装
3. 打开应用，按引导页逐项授权（root、悬浮窗、通知、使用情况访问、电池优化白名单）
4. 回到仪表盘，点「开始记录」

第一次使用建议先读[安装与上手](/guide/getting-started)，里面有每项权限的用途与缺失后果。

## 校验签名

APK 使用固定签名发布，证书 SHA-256 为：

```text
a118fa78…47141d
```

需要自行核验时：

```bash
apksigner verify --print-certs WattScope-Next-{{VERSION}}.apk
```

::: warning 不要从第三方渠道安装
WattScope Next 需要 root 权限，从非官方渠道拿到的包一旦签名不符，等于把 root 交给未知代码。只从本仓库 Releases 下载。
:::

## 测试版

测试版不发布 Release，构建产物挂在 GitHub Actions 上：

1. 打开 [Actions](https://github.com/RuSheng920/WattScope-Next/actions) 页面
2. 选择最近一次由 `beta/*` 标签触发的运行
3. 在页面底部 Artifacts 里下载 APK（需登录 GitHub）

测试版与正式版签名一致，可以直接覆盖安装；回退到正式版时如果版本号更低，需要先卸载。

## 从原版 WattScope 迁移

包名不同，**属于全新安装**。原版的历史记录不会自动迁移，两套数据各自独立，可以同时装着对比。

## 下一步

- [安装与上手](/guide/getting-started)
- [常见问题](/guide/faq)

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

/* 版本号独立成行：标题文本保持静态，避免锚点把占位符写进 aria-label */
.dl-ver-line {
  margin: -4px 0 14px;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
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
