---
layout: home

hero:
  name: WattScope Next
  text: 高通平台功耗遥测
  tagline: 基于 Qualcomm Power Telemetry，以接近零的运行时开销实时观测并记录功耗、频率、温度与帧率。测量工具本身，尽可能不干扰测量结果。
  image:
    src: /logo.svg
    alt: WattScope Next
  actions:
    - theme: brand
      text: 立即下载
      link: /download
    - theme: alt
      text: 快速上手
      link: /guide/getting-started
    - theme: alt
      text: GitHub
      link: https://github.com/RuSheng920/WattScope-Next

features:
  - title: 八分区功耗
    details: 直接读取 powercap 下的 QPT zone，功率与能量双读，把功耗归因到 SOC / CPU-L / CPU-M / GPU / NSP，而不是只给一个总数。
  - title: 接近零的采样开销
    details: 单次 su 往返完成一轮全指标采样，shell 内建 read 替代 $(cat)。5 Hz 下不产生进程 fork，测量工具自身几乎不耗电。
  - title: 悬浮球记录
    details: 单击开始记录当前前台应用，退出该应用自动停止；长按打开控制面板，三档尺寸，闲置自动淡出。
  - title: OSD 浮层
    details: 11 项指标任选，两列 HUD 排版，可拖动、轻点循环透明度，只做实时显示、绝不写入记录。
  - title: 历史详情
    details: 全局同步十字光标与浮动 Tooltip，每图独立平均值，分区筛选，曲线顺序可自定义，支持 JSON / CSV 导出。
  - title: 原生 MIUIX 设计
    details: 基于 MIUIX 设计语言组件库构建，深色 / 浅色 / 跟随系统三态主题，悬浮液态玻璃底栏与预测性返回。
---

<TelemetryPanel />

## 从这里开始

<div class="start-grid">

[**什么是 WattScope Next**](/guide/what-is-wattscope)
定位、四项设计取向、与 WattScope 1.0 的差异，以及已知限制。

[**安装与上手**](/guide/getting-started)
环境要求、首次引导授权、开始一次记录并导出结果。

[**功耗读取方法**](/power-reading)
QPT 分区与整机功耗到底怎么读，屏幕上的数字从哪来。

[**常见问题**](/guide/faq)
QPT 缺失、整机功耗为 0、FPS 测不到、双电芯机型等。

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
