<p align="center">
  <img src="docs/app-icon.png" width="120" alt="WattScope Next" />
</p>

<h1 align="center">WattScope Next</h1>

<p align="center"><b>简体中文</b> · <a href="README.en.md">English</a></p>

<p align="center"><b>高通平台 SoC 功耗遥测工具 · 重构版</b></p>

<p align="center">基于 Qualcomm Power Telemetry，以接近零的运行时开销实时观测并记录功耗、频率、温度与帧率。</p>

<p align="center">
  <a href="../../releases"><img src="https://img.shields.io/badge/Release-v1.2.5-2EA44F?logo=github&logoColor=white" alt="Release" /></a>
  <a href="https://watt.rusheng.top"><img src="https://img.shields.io/badge/%E5%AE%98%E7%BD%91-watt.rusheng.top-F23A2A" alt="官网" /></a>
  <a href="docs/power-reading.md"><img src="https://img.shields.io/badge/%E6%96%87%E6%A1%A3-Docs-4285F4?logo=readthedocs&logoColor=white" alt="Docs" /></a>
  <img src="https://img.shields.io/badge/Android%2013%2B-3DDC84?logo=android&logoColor=white" alt="Android 13+" />
  <img src="https://img.shields.io/badge/Kotlin%202.3-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.3" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/MIUIX-FF6900?logo=xiaomi&logoColor=white" alt="MIUIX" />
  <img src="https://img.shields.io/badge/Room-003B57?logo=sqlite&logoColor=white" alt="Room" />
  <img src="https://img.shields.io/badge/libsu-4EAA25?logo=gnubash&logoColor=white" alt="libsu" />
</p>

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

三个主 Tab：**仪表盘 / 记录 / 设置**。设置首页是分类索引 —— 外观与主题、采样与功耗、悬浮窗与 OSD、FPS 数据源、服务与权限、关于各自独立成页，权限中心作为二级页从这里进入。

- 深色、浅色、跟随系统三态主题，偏好持久化
- 悬浮液态玻璃底栏（MIUIX `FloatingNavigationBar` + Backdrop 实时模糊 / 边缘折射 / 内阴影），浓度三档可调，可回落为贴底样式
- 权限中心逐项展示授权状态并可直接跳转系统页面（root、悬浮窗、通知、使用情况访问、电池优化白名单），页面可见时每 1.5 s 自动复检
- 预测性返回：Tab 内滑动返回与详情页手势均有进度联动
- **中英双语**：界面提供简体中文与英文，默认跟随系统，可在「设置 → 外观与主题 → 语言」固定为其中一种（Android 13+ 系统级 per-app language），切换即时生效

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
│   ├── fps/              SurfaceFlinger 帧率
│   └── update/           GitHub Releases 版本查询与 SemVer 比较
├── data/
│   ├── local/            Room 实体、DAO、迁移
│   └── repo/             MonitorRepository：采样编排、落库、导出；UpdateRepository：更新检查与下载
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
5. 「设置 → 关于」可查看版本号、检查更新（正式版 / Beta 双通道），并跳转 GitHub 主页与官网

## 下载

安装包见 [Releases](../../releases)。

> 应用 ID 为 `io.github.rusheng920.wattscope`，与原版 WattScope 的 `com.qpt.powermonitor` 相互独立，可同时安装。

## 更新日志

### v1.3.0

本版把**应用内更新**做进「关于」页，并补齐项目入口。

#### 新增

- **自动检查更新**：开关打开后，每次启动向 GitHub Releases 查询新版本；发现更新会弹窗，可当场下载并调起系统安装器完成覆盖安装。默认关闭 —— 更新检查要联网，交给你决定
- **正式版 / Beta 版双通道**：Beta 通道同时考虑测试版与正式版，按版本号取最高者；测试版版本号低于正式版时，更新到的仍是正式版（`1.2.6-beta.2` 不会盖掉 `1.2.6`）
- **关于页新增官方网站入口**：`https://watt.rusheng.top`
- 更新说明直接取 Release 正文，展示前清掉 Markdown 标记

#### 变更

- 关于页重排为「品牌 / 更新 / 项目」三组；GitHub 与官网条目显示完整 URL（含 `https://` 前缀），不再截断成仓库名
- 测试版（`beta/` 标签）改为同时发布 **prerelease** Release —— 应用内 Beta 通道靠 Releases API 取包，而 Actions 产物匿名不可下载

#### 说明

- 覆盖安装需要「安装未知应用」权限，未授权时会直接跳到对应的系统设置页
- 检查更新走匿名 GitHub API，受每小时 60 次的公共额度限制；正常使用（每次启动一次）远低于该上限

### v1.2.5

本版重做**设置页的信息架构**，并修掉一批真机走查发现的可读性问题。

#### 新增

- **设置页拆成「分类索引 + 六个独立页」**：外观与主题、采样与功耗、悬浮窗与 OSD、FPS 数据源、服务与权限、关于各自成页；索引行右侧直接显示当前值（如「200 ms」「深色」「就绪」），不进页面就能看到状态
- **二级页独立顶栏与返回**：进入二级页后底栏收起、顶栏变为「返回箭头 + 页标题」；系统返回手势分层生效（二级页 → 索引 → 首页），两层都带跟手缩放

#### 变更

- 液态玻璃三项（底栏开关、浓度、记录时暂停实时模糊）归入「外观与主题」，与主题模式、语言同页
- 采样档位文案改为 `100 ms / 200 ms / 500 ms / 1000 ms` 写法，四档在窄屏一行排满
- 电池优化白名单的状态改用状态胶囊表达，与「权限中心 → 就绪」同形
- 互斥单选控件统一选型：2–3 项短标签用分段控件，4 项及以上或长标签用 chip + 自动换行

#### 修复

- **深色模式下卡片内文字几乎不可读**：未显式设色的文字会落到 Material 默认黑色，在 `#242424` 卡片上对比度仅约 1.1:1。现由 `MiuixCard` 统一注入内容前景色，实测约 11:1，达到 WCAG AA
- FPS 数据源页的「扫描图层」改为带箭头的操作行，不再是一段看不出能点的文字
- 采样间隔的第四档在窄屏不再被推出可视区
- 关于页品牌位换回红白燃焰图标，GitHub 条目不再把仓库名挤到第二行

### v1.2.4

本版是**国际化**版本：界面、README 与 Release 说明一次性补齐英文，中文仍是默认语言。

#### 新增

- **App 内中英双语**：219 条界面文案全部落入字符串资源，中文为默认、英文走 `values-en`；「设置 → 外观与主题 → 语言」可在「跟随系统 / 简体中文 / English」间切换（Android 13+ 系统级 per-app language，切换即时生效，采样服务与进行中的记录都不受影响），系统设置的「应用 → 语言」里同样能找到本应用
- **英文 README**：新增 `README.en.md`，与中文版顶部互链
- **英文文档站**：补齐「权限与故障排查」英文页，站点中英文导航完整对齐

#### 变更

- **Release 说明改为双语**：正文同时给出中文与英文（`---` 分隔），由两份 README 的同名版本小节拼接生成
- **运行时状态文案改为保存资源 id**：切换语言后首页状态行立即跟随，不再停留在旧语言

### v1.2.3

本版让记录的**可读性**上一个台阶：所有监控项与 SoC 功率同采样率落库，详情页曲线时间轴逐点对齐，并支持自定义曲线顺序。同时包含测试版（v1.2.3-beta.1~3）验证过的悬浮球与暗色修复。

#### 新增

- **详情页曲线自定义排序**：「曲线」标题右侧新增「排序」按钮，SOC 功率、FPS、整机功耗、CPU 簇频率、CPU 核心占用、GPU、DDR、分区功率、温度九个区块各带 ↑/↓ 按钮调序，改动即时生效；顺序持久化为全局设置，所有记录共用。默认顺序按分析动线排列：总功率 → 性能结果 → 原因下钻 → 分区细分 → 温度收尾
- **曲线顺序向后兼容**：升级后旧存档不受影响；未来新增的曲线区块自动补齐到列表尾部

#### 变更

- **记录采样率统一**：CPU 簇频率、每核占用、整机功耗、GPU 频率/占用、温度与 FPS 不再按 1 秒慢节奏采样，全部跟随用户设定的采样间隔（默认 200ms）与 SoC 功率同拍落库 —— 详情页八类图表时间轴逐点对齐，跨图对比不再因采样错位而眯眼看。采样命令仍是单次 su 往返（0 fork），落库事务保持约 1 次/秒，记录功耗开销几乎不变

#### 修复

- **悬浮球开启记录后仅 1 秒就自动停止**：「开始记录」与「是否还在前台」判定改用同一数据源（root `dumpsys`），并增加 5 秒宽限期，覆盖目标应用刚获得焦点时系统前台状态尚未稳定的窗口
- **悬浮球拖动后强制吸附到屏幕边缘**：默认停留在松手位置，可在设置 → 悬浮球开启「松手吸附边缘」恢复旧行为
- **切换深浅色主题后首页渐变不跟随**：`HeroCard` 的 `remember` 缓存 key 补上主题色值
- **深色模式下 Chip 与环形进度条轨道完全隐形**：主题层统一提亮 MIUIX 暗色 `surfaceContainerHigh` 至 #343434，浅色保持官方值
- **分区功耗占比显示被截断**：整数除法改为浮点四舍五入，占比与进度条不再自相矛盾

### v1.2.2

本版集中提升**暗色模式可读性**，补齐**双电芯机型**适配，并换上全新的「燃焰 W」应用图标。

#### 新增

- **双电芯机型开关**：设置 → 采样新增「双电芯机型」。部分双电芯机型的电池驱动只上报单路电芯电流，整机功耗会低估约一半；开启后按两路求和，实时预览、OSD 浮层与记录落库同步生效，设置本地持久化
- **全新应用图标 · 燃焰 W**：斜体粗体 W（功耗 Watt）与右笔焰尾（骁龙燃焰）一形两读，中峰收为闪电尖（功耗遥测）；红白配色 + 亮红至深红渐变。自适应图标前景由母稿等比移植，全部顶点落在中心 72dp 安全圆内（圆形 / 圆角方形 / 水滴遮罩均不裁切），焰尾外缘用二次贝塞尔还原曲线；同步更新 Android 13+ 单色主题图标层，与彩色图标同形

#### 修复

- **退出重进误报「权限未给全」**：Root 授权状态在异步探测完成前为 `null`，被当作未授权弹横幅，点进权限中心却发现都已授予。现在探测期间不再误报，且权限刷新改为应用可见期间每 1.5 秒持续进行，授权变化实时同步到仪表盘
- **暗色摘要文字在亮容器上不达 WCAG AA**：MIUIX 官方 `onSurfaceVariantSummary` 为 50% 白，在 #343434 容器上对比度仅约 4.4:1；提亮至 70% 白后全面达到 7:1 以上。MIUIX 组件摘要与分隔线在主题层一处提亮、全局生效，无需逐组件覆盖
- **暗色卡片内分隔线近乎隐形**：`dividerLine` 官方值 #393939 对 #242424 卡片仅约 1.3:1，提至 #4A4A4A（约 1.75:1），可见且不抢眼
- **暗色 Chip 与卡片同色没边界**：`SelectChip` 未选中态原用 MIUIX `surfaceContainerHigh`（与卡片底色相同），改走 Material 同 role 层级；`surfaceVariant` / `surfaceContainerHigh` 统一提至 #343434，进度条轨道等元素与卡片拉开层级
- **暗色图表发虚**：网格线与坐标轴提亮，状态胶囊四色底同步校准

#### 变更

- **组件库扩充**：新增统一指标瓦片 `SurfaceTile`、环形进度 `CircularProgress`、列表入场动画 `AnimatedListItem`；空状态图标底改为主色渐变
- **仪表盘**：HeroCard 增加状态胶囊与全宽主按钮，实时柱状图分区行新增功耗占比；首页大数字与单位基线对齐
- **权限中心 / 首次引导**：完成度由细进度条改为环形进度 + 中心数字，步骤行改为竖向时间线；引导页顶部图标放大居中，背景改为与仪表盘一致的暗色渐变
- **设置页**：「关于」卡片改为居中应用徽标 + 版本信息，权限中心入口显示就绪状态胶囊
- **悬浮窗**：玻璃球录制态状态环外移并增强发光，OSD 空状态字号与对比度提升

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
