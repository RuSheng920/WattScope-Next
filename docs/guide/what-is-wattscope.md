# 什么是 WattScope Next

WattScope Next 是 [WattScope](https://github.com/Alita23333/WattScope) 的重构续作 —— 一个面向 **root 环境的高通平台 SoC 功耗遥测工具**。它沿用原版的 QPT 遥测模型与记录数据结构，但 UI 层、采样链路、FPS 数据源与权限流程均已重写。

一句话概括设计目标：**让「测量工具本身」尽可能不干扰测量结果。**

## 四项设计取向

### 低开销优先

一轮全指标采样在**单次 `su` 往返**内完成，shell 用内建 `read` 替代 `$(cat)`、避免 `grep` / `awk` 子进程。原版每个采样 tick 要 fork 约 32 次，重构后为 0 次 —— 5 Hz 采样下这部分开销直接归零，测得的数据才更接近"应用真实功耗"。

### 原生设计语言

界面基于 MIUIX 设计语言的 Compose 组件库（`top.yukonga.miuix.kmp`）构建，而不是自绘仿制。三 Tab 结构（仪表盘 / 记录 / 设置）、悬浮液态玻璃底栏、预测性返回手势都来自这套组件。

### 数据可信

- 时间基准改用 `elapsedRealtime`，规避 NTP 校时造成的假功耗尖峰
- FPS 取自 SurfaceFlinger 实际上屏帧时间戳，而非 `dumpsys gfxinfo` 的粗粒度统计
- 前台应用识别加 2 秒 TTL 缓存，不对包名做 `com.` / `android.` 前缀过滤
- 所有监控项与 SoC 功率同采样率落库，详情页各图表的时间轴逐点对齐

### 独立包名

应用 ID 为 `io.github.rusheng920.wattscope`，与原版 WattScope 的 `com.qpt.powermonitor` 相互独立，**可与原版共存安装**，互不覆盖数据。

## 与 WattScope 1.0 的差异

| 维度 | WattScope 1.0 | WattScope Next |
| --- | --- | --- |
| UI | 自绘仿 MIUI 组件 | MIUIX 设计语言组件库 + 三 Tab + 悬浮玻璃底栏 |
| 采样 | 每 tick 约 32 次 `fork/exec` | shell 内建 `read`，单次 `su` 往返批量取数 |
| 采样频率 | 全指标同频 | 快慢分级：功率高频、温度/占用等慢变量降频 |
| FPS | `dumpsys gfxinfo` 为主源，SurfaceView 游戏常测不到 | SurfaceFlinger 呈现时间戳为主源，`gfxinfo` 仅回退 |
| 前台应用识别 | 每次采样重新 `dumpsys activity` | 2 秒 TTL 缓存，不按包名前缀过滤 |
| 落库 | 每 tick 单独提交，与采样串行 | 批量提交 + WAL `synchronous=NORMAL` |
| 详情页 | 订阅整张表，录制中打开历史触发 O(n²) 查询 | stride 降采样 + 后台线程聚合 |
| 时间基准 | `currentTimeMillis` | `elapsedRealtime` + 固定相位，抗校时跳变 |
| 主题 | 固定深色 | 三态主题 + 悬浮玻璃底栏（三档浓度） |
| 权限 | 分散检查 | 首次安装引导 + 设置内权限二级页，状态实时刷新 |

数据库 schema 为 v4，内置 1 → 4 全量迁移能力。

## 支持与不支持

::: warning 前提条件
必须是**已 root 的高通平台设备**（KernelSU / Magisk），且设备 BSP 开放了 `powercap` 下的 QPT 节点。已在 Redmi K90 Pro Max（myron / SM8850 / Android 16）上验证。
:::

QPT 节点由厂商 BSP 提供。非高通平台或未开放 powercap 的设备，仍然可以记录频率、温度、FPS 等附加项，但没有分区功耗数据。

部分机型的电池温度、GPU busy 节点命名不同，缺失时对应图表会自动隐藏，不影响其它功能。

::: tip 从原版切换过来
包名不同，属于全新安装，历史记录不会自动迁移。数据库 schema 兼容 v4，如需迁移可自行导出 JSON 后处理。
:::

## 下一步

- [安装与上手](/guide/getting-started) —— 从下载到第一次记录
- [功能详解](/guide/features) —— 三个 Tab 各自能做什么
- [功耗读取方法](/power-reading) —— 想弄清数字来源的话
