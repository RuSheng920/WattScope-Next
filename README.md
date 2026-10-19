<div align="center">

# WattScope-Next

**高通平台 SoC 功耗遥测工具 · 重构版**

基于 Qualcomm Power Telemetry，以接近零的运行时开销实时观测并记录功耗、频率、温度与帧率。

`Android 13+` `Kotlin 2.3` `Jetpack Compose` `MIUIX` `Room` `libsu`

</div>

---

## 简介

WattScope-Next 是 [WattScope](https://github.com/Alita23333/WattScope) 的重构续作。项目沿用原版的 QPT 遥测模型与记录数据结构，但 **UI 层、采样链路、FPS 数据源与权限流程均已重写**，目标是让"测量工具本身"尽可能不干扰测量结果。

- **低开销优先**：单次 `su` 往返完成一轮采样，shell 内建 `read` 替代 `$(cat)`，避免每 tick 数十次进程 fork
- **原生设计语言**：界面基于 MIUIX 设计语言的 Compose 组件库（`top.yukonga.miuix.kmp`）构建，而非自绘仿制
- **数据可信**：时间基准改用 `elapsedRealtime`，规避 NTP 校时造成的假功耗尖峰；FPS 取自 SurfaceFlinger 实际上屏帧
- **独立包名**：`io.github.rusheng920.wattscope`，可与原版 WattScope 共存安装，互不覆盖数据

已在 **Redmi K90 Pro Max（myron / SM8850 / Android 16）** 上验证。

## 功能

### 功耗遥测

| 能力 | 数据源 | 说明 |
| --- | --- | --- |
| QPT 分区功耗 | `/sys/class/powercap/qpt` | 8 个 zone：soc / cpu-m / cpu-l / gpu / nsp / debug-0 / 2 / 4，功率与能量双读 |
| CPU 簇频率 | `cpufreq` policy 动态枚举 | 不写死簇数量，适配 2 簇 / 3 簇平台 |
| 每核占用 | `/proc/stat` | 按簇着色，纵轴每 10% 分度 |
| 整机功耗 | `power_now` / 电池节点 | 缺少节点时自动改用电压 × 电流计算 |
| 温度 | thermal zone | CPU / DDR / GPU / 电池 |
| GPU | devfreq + gpu busy | 频率与占用双轴图 |
| DDR 频率 | devfreq | — |
| FPS | SurfaceFlinger `--latency` | 跟随前台应用，回退 `dumpsys gfxinfo` |

### 记录与观测

- **悬浮球**：点按开始记录当前应用，**退出该应用自动停止**；记录标题带应用名与图标
- **OSD 浮层**：11 项指标可选（SOC / CPU-L / CPU-M / GPU 功耗、GPU 频率与占用、FPS、CPU/GPU/DDR 温度、整机功耗），竖排显示、可拖动、不写入记录；刷新缓慢的传感器保持上次有效值
- **历史详情**：平均功耗置顶、全局同步十字光标 + 浮动 Tooltip、每图独立平均值、分区筛选、JSON / CSV 导出
- **后台保活**：界面不可见时预览降为 1 Hz，曲线不断点，避免息屏后持续高频采样

### 界面

四个主 Tab：**仪表盘 / 记录 / 权限 / 设置**。

- 深色、浅色、跟随系统三态主题，偏好持久化
- 权限中心逐项展示授权状态并可直接跳转系统页面（root、悬浮窗、通知、使用情况访问、电池优化白名单）
- 预测性返回：Tab 内滑动返回与详情页手势均有进度联动

## 与 WattScope 1.0 的差异

| 维度 | WattScope 1.0 | WattScope-Next |
| --- | --- | --- |
| UI | 自绘仿 MIUI 组件 | MIUIX 设计语言组件库 + 四 Tab 信息架构 |
| 采样 | 每 tick 约 32 次 `fork/exec`（`$(cat)`、`grep`、`awk`） | shell 内建 `read`，单次 `su` 往返批量取数 |
| 采样频率 | 全指标同频 | 快慢分级：功率高频、温度/占用等慢变量降频 |
| FPS | `dumpsys gfxinfo` 为主源，SurfaceView 游戏常测不到 | SurfaceFlinger 呈现时间戳为主源，`gfxinfo` 仅回退 |
| 前台应用识别 | 每次采样重新 `dumpsys activity` | 2 s TTL 缓存，包名判定不再按 `com.` / `android.` 前缀过滤 |
| 落库 | 每 tick 单独提交，与采样串行 | 批量提交 + WAL `synchronous=NORMAL` |
| 详情页 | 订阅整张表，录制中打开历史触发 O(n²) 查询 | stride 降采样 + 后台线程聚合 |
| 时间基准 | `currentTimeMillis` | `elapsedRealtime` + 固定相位，抗校时跳变 |
| 主题 | 固定深色 | 三态主题 |
| 权限 | 分散检查 | 权限中心统一状态与跳转 |

数据库 schema 为 v4，内置 1→4 全量迁移，旧记录可直接沿用。

## 架构

```
app/src/main/java/com/qpt/powermonitor/
├── core/                 采集层（全部在 root shell 内完成）
│   ├── root/             libsu 封装、shell 转义
│   ├── qpt/              QPT zone 枚举与功率/能量读取
│   ├── auxiliary/        频率、占用、温度、GPU、DDR、整机功耗
│   └── fps/              SurfaceFlinger 帧率
├── data/
│   ├── local/            Room 实体、DAO、迁移
│   └── repo/             MonitorRepository：采样编排、落库、导出
├── domain/               领域模型与枚举（采样档位、主题、OSD 指标）
├── service/              前台服务、悬浮球、OSD（View 体系 + 液态玻璃背景）
└── ui/                   Compose + MIUIX 页面与主题
```

数据流：

```
MonitorRepository ──su shell 批量采样──> core/* Reader
        │
        ├─> Room（记录态，批量落库）
        └─> StateFlow（预览 / OSD / 悬浮球）
                    │
                    └─> Compose UI、FloatView
```

采样命令统一走 `RootManager.escapeForShell()` 转义路径；sysfs 节点是否存在以"读出的内容是否为空"判断，不依赖 `-f` / `-s`（多数节点 `st_size` 恒为 4096）。

## 构建

环境要求：

| 组件 | 版本 |
| --- | --- |
| JDK | 17 |
| Android SDK | compileSdk 36 / build-tools 36 |
| Gradle | 8.13（随 wrapper） |
| AGP / Kotlin / KSP | 8.13.2 / 2.3.21 / 2.3.12 |

```bash
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 发布包（需 keystore.properties，缺失时为未签名）
# 产物：app/build/outputs/apk/{debug,release}/
```

补充说明：

- `gradle/wrapper/gradle-wrapper.properties` 默认指向华为云镜像（`services.gradle.org` 在部分网络不可达）；如访问正常可自行替换回官方地址
- 发布签名读取根目录 `keystore.properties`（`storeFile` / `storePassword` / `keyAlias` / `keyPassword`），该文件与 `*.jks` / `*.keystore` 均已在 `.gitignore` 中排除
- 依赖含 JitPack（libsu），首次构建需联网

## 使用

1. 设备已 root（KernelSU / Magisk），授予 WattScope root 权限
2. 首次启动按引导完成悬浮窗、通知、使用情况访问与电池优化白名单授权
3. 仪表盘点击「开始记录」，或使用悬浮球记录当前前台应用；退出该应用自动停止
4. 记录完成后进入「记录」Tab 查看曲线与统计，支持导出 JSON / CSV
5. 「设置 → 关于」可查看版本号，并直接跳转本项目的 GitHub 主页

## 下载

安装包见 [Releases](../../releases)。

> 应用 ID 为 `io.github.rusheng920.wattscope`，与原版 WattScope 的 `com.qpt.powermonitor` 相互独立，可同时安装。

## 已知限制

- QPT 节点由厂商 BSP 提供，非高通平台或未开放 powercap 的设备只能记录频率、温度、FPS 等附加项
- 部分机型的电池温度、GPU busy 节点命名不同，缺失时对应图表自动隐藏
- 悬浮球与 OSD 常驻浮层不做实时高斯模糊（逐帧模糊会持续占用 GPU，污染功耗测量），改用静态液态玻璃底衬
- 从原版 WattScope 切换过来属于全新安装：包名不同，历史记录不会迁移

## 文档

- [`docs/power-reading.md`](docs/power-reading.md)：QPT 分区与整机功耗的读取方法、节点含义与实测数据
- [`docs/power-review-recording.md`](docs/power-review-recording.md)：记录链路的功耗剖析与优化项落地记录

## 致谢与许可

本项目基于 [Alita23333/WattScope](https://github.com/Alita23333/WattScope) 二次开发，遥测模型与数据结构源自原项目，在此致谢。

仅供学习与研究使用，请勿用于商业用途；二次分发请保留原作者与本仓库出处。
