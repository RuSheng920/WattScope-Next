<div align="center">

# WattScope Next

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

- **悬浮球**：单击开始记录当前应用，**退出该应用自动停止**；长按打开控制面板，拖拽松手自动吸附屏幕边缘；迷你 / 标准 / 大三档尺寸，闲置自动淡出
- **OSD 浮层**：11 项指标可选（SOC / CPU-L / CPU-M / GPU 功耗、GPU 频率与占用、FPS、CPU/GPU/DDR 温度、整机功耗），两列 HUD 排版、可拖动、轻点切换透明度、不写入记录；刷新缓慢的传感器保持上次有效值
- **历史详情**：平均功耗置顶、全局同步十字光标 + 浮动 Tooltip、每图独立平均值、分区筛选、JSON / CSV 导出
- **后台保活**：界面不可见时预览降为 1 Hz，曲线不断点，避免息屏后持续高频采样

### 界面

三个主 Tab：**仪表盘 / 记录 / 设置**，权限中心作为设置内的二级页独立进入。

- 深色、浅色、跟随系统三态主题，偏好持久化
- 悬浮液态玻璃底栏（MIUIX `FloatingNavigationBar` + Backdrop 实时模糊 / 边缘折射 / 内阴影），浓度三档可调，可回落为贴底样式
- 权限中心逐项展示授权状态并可直接跳转系统页面（root、悬浮窗、通知、使用情况访问、电池优化白名单），页面可见时每 1.5 s 自动复检
- 预测性返回：Tab 内滑动返回与详情页手势均有进度联动

## 与 WattScope 1.0 的差异

| 维度 | WattScope 1.0 | WattScope-Next |
| --- | --- | --- |
| UI | 自绘仿 MIUI 组件 | MIUIX 设计语言组件库 + 三 Tab + 悬浮玻璃底栏 |
| 采样 | 每 tick 约 32 次 `fork/exec`（`$(cat)`、`grep`、`awk`） | shell 内建 `read`，单次 `su` 往返批量取数 |
| 采样频率 | 全指标同频 | 快慢分级：功率高频、温度/占用等慢变量降频 |
| FPS | `dumpsys gfxinfo` 为主源，SurfaceView 游戏常测不到 | SurfaceFlinger 呈现时间戳为主源，`gfxinfo` 仅回退 |
| 前台应用识别 | 每次采样重新 `dumpsys activity` | 2 s TTL 缓存，包名判定不再按 `com.` / `android.` 前缀过滤 |
| 落库 | 每 tick 单独提交，与采样串行 | 批量提交 + WAL `synchronous=NORMAL` |
| 详情页 | 订阅整张表，录制中打开历史触发 O(n²) 查询 | stride 降采样 + 后台线程聚合 |
| 时间基准 | `currentTimeMillis` | `elapsedRealtime` + 固定相位，抗校时跳变 |
| 主题 | 固定深色 | 三态主题 + 悬浮玻璃底栏（三档浓度） |
| 权限 | 分散检查 | 首次安装引导 + 设置内权限二级页，状态实时刷新 |

数据库 schema 为 v4，内置 1→4 全量迁移，旧记录可直接沿用。

## 架构

```
app/src/main/java/io/github/rusheng920/wattscope/
├── core/                 采集层（全部在 root shell 内完成）
│   ├── root/             libsu 封装、shell 转义
│   ├── qpt/              QPT zone 枚举与功率/能量读取
│   ├── auxiliary/        频率、占用、温度、GPU、DDR、整机功耗
│   └── fps/              SurfaceFlinger 帧率
├── data/
│   ├── local/            Room 实体、DAO、迁移
│   └── repo/             MonitorRepository：采样编排、落库、导出
├── domain/               领域模型与枚举（采样档位、主题、OSD 指标）
├── service/              前台服务、悬浮球、OSD（View 体系；overlay/ 下为自绘玻璃球与 HUD 面板）
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

1. 设备已 root（KernelSU / Magisk），授予 WattScope Next root 权限
2. 首次启动按引导完成悬浮窗、通知、使用情况访问与电池优化白名单授权
3. 仪表盘点击「开始记录」，或使用悬浮球记录当前前台应用；退出该应用自动停止
4. 记录完成后进入「记录」Tab 查看曲线与统计，支持导出 JSON / CSV
5. 「设置 → 关于」可查看版本号，并直接跳转本项目的 GitHub 主页

## 下载

安装包见 [Releases](../../releases)。

> 应用 ID 为 `io.github.rusheng920.wattscope`，与原版 WattScope 的 `com.qpt.powermonitor` 相互独立，可同时安装。

## 更新日志

### v1.2.1

本版集中处理**浮层可视化与采样链路**的实际问题。新增与修复如下（v1.2.0 之后的开发版本统一并入本版）。

#### 新增

- **悬浮球尺寸三档**：迷你 40 / 标准 48 / 大 56 dp，默认档比旧版明显更小，设置内切换即刻生效
- **悬浮球边缘吸附**：拖拽松手自动贴到最近一侧屏幕边缘，位置本地记忆，闲置 7 秒淡出到 55%
- **OSD 两列 HUD**：标签左对齐、数值右对齐并固定等宽，标题行带 REC / LIVE 状态点；轻点循环三档透明度
- **悬浮球长按控制面板**：面板顶部实时显示 SOC 功耗与帧率

#### 修复

- **采样合并命令的 shell 语法错误**：QPT 采样片段每段以 `"; "` 收尾，与附加监控命令拼接后产生 `; ;`，shell 直接以语法错误丢弃整条命令 —— 温度 / GPU 频率与占用 / DDR / 整机功耗**从未被采样到**，且记录时每 5 个 tick 会连带丢失一批 QPT 样本
- **实时数据只在记录时刷新**：预览与 OSD 两条循环互相让位，OSD 循环在设备扫描完成前被打开时还会直接放弃且不再重试；现合并为一条实时循环，QPT 按采样间隔、附加项与 FPS 按 1 秒，一次 `su` 往返同时刷新首页与 OSD
- **整机功耗恒为 `0.00 W` / `--`**：`power_now` 在不少 BMS 驱动上只在充电时更新，放电时读到字面上的 `0`。参照 OpenMonitor（1orz/OpenMonitor）的做法，改为按 `current_now × voltage_now` 计算，依次探测 `/sys/class/power_supply/{battery, Battery, bms, hisi_bk_battery}`，并按物理量级自适应单位；`power_now` 仅作最后兜底
- 实时功耗柱状图四角全圆，改为只圆顶部两角（柱子自基线生长）
- 关闭悬浮球时视图未被移除（动画回调引用了已置空的字段）；面板关闭后仍持有 TextView 引用；浮层 `removeView` 缺异常保护
- 通知被点时若悬浮窗授权已被回收会抛 `SecurityException`；通知小图标改用应用单色图标
- 权限中心在大字体 / 窄屏下按钮被压缩变形，状态胶囊与按钮改为弹性布局

#### 变更

- 液态玻璃通透度重做：表面着色由 26%–56% 压到 5%–18%，模糊由 6–17 dp 降到 2–8 dp，改用 `colorControls` 提纯 + 边缘折射 + 内阴影表现玻璃厚度；页面底色加入极淡纵向渐变，为玻璃提供可折射的内容
- 分区展示顺序统一为 SOC → CPU-L（大核）→ CPU-M（小核）→ GPU → NSP，首页、详情页、OSD 与平均功耗卡保持一致
- 设置页 OSD 指标由复选框列表改为可勾选胶囊，新增悬浮球尺寸选择；空状态改为图标 + 标题 + 说明；分隔线统一为 MIUIX `dividerLine`

### v1.2.0

- **液态玻璃重做**：底栏改用 Backdrop 的 `colorControls` + 低模糊 + 边缘折射，表面着色由 26%–56% 压到 5%–18%，并补上内阴影与默认环境高光，透光不再呈磨砂亚克力观感
- **悬浮球缩小与吸附**：新增迷你 / 标准 / 大三档（36 / 44 / 54 dp，默认 44），拖拽松手自动吸附最近边缘，闲置 7 秒淡出到 55%，位置本地记忆
- **悬浮球重绘**：深色玻璃球 + 顶部内高光 + 渐变亮边 + 记录态状态环，替换原来的「▶」文字按钮；单击记录、长按控制面板
- **OSD 重做**：两列 HUD（标签左对齐 / 数值右对齐等宽），标题行带 REC / LIVE 状态点，轻点循环三档透明度
- **界面细节**：页面底色加入极淡纵向冷暖过渡（为玻璃提供可折射的内容），空状态改为图标 + 标题 + 说明，OSD 指标选择改为可勾选胶囊，分隔线统一为 MIUIX `dividerLine`

### v1.1.0

- **首次引导**：新增独立的权限引导页，安装后不再于启动时自动申请通知、悬浮窗权限，改为在引导页逐项授权
- **信息架构**：底栏收敛为「仪表盘 / 记录 / 设置」三 Tab，权限中心改为设置内的二级页，支持预测性返回
- **悬浮玻璃底栏**：基于 MIUIX `FloatingNavigationBar` 的液态玻璃底栏，浓度三档可调，可回落为贴底样式
- **实时功耗图表**：首页由折线改为按分区柱状图，含峰值标注、数值标签与自适应刻度
- **Root 检测修复**：优先读取已建立 shell 的真实状态，在 root 管理器中重新授权后无需重启应用即可识别
- **浮层视觉**：悬浮球 / OSD 改为纯色半透明 + 1 dp 亮边，移除渐变玻璃层，降低常驻浮层的 GPU 重绘开销
- **命名与图标**：应用名统一为 WattScope Next，重绘自适应图标（含 monochrome 层）

### v1.0.0

- 首个公开发布版本

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
