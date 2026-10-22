# OpenMonitor 功能清单与移植评估

对照对象：<https://github.com/1orz/OpenMonitor>
对照目标：WattScope-Next（`io.github.rusheng920.wattscope`，minSdk 33 / targetSdk 36）

口径说明：

- **成本**：S ≤ 0.5 天，M = 1–2 天，L ≥ 3 天（含 native、数据资产或第三方服务）。
- **依赖**：`sysfs` = 读内核节点（需 root 或部分公开可读）；`API` = Android 公开接口；`root` = 必须 root；
  `native` = 需要 C/C++/Rust 与 NDK 构建；`data` = 需要外部数据资产。
- **建议**：★ 建议做（与功耗分析主线互补）；○ 可选（看使用频率）；× 不建议（成本/收益不匹配或与现有实现冲突）。
- OpenMonitor 的 Rust 守护进程在本项目**不需要移植**：`RootShellPool` 的三通道（SAMPLE/QUERY/CONTROL）
  已经把「常驻特权采样」这件事做掉了，再引入 Rust 只是重复建设。

---

## A. 硬件与 SoC

| ID | 功能点 | OpenMonitor 的做法 | 本项目的落点 | 依赖 | 成本 | 建议 |
|----|--------|-------------------|-------------|------|------|------|
| A1 | CPU 分析页：按 L/M/P 集群分组，每核负载、频率、在线状态、governor | `/sys/devices/system/cpu/cpuN/{cpufreq,online}` + `/proc/stat` | 复用 `AuxReader` 已有集群与每核采样，补 governor 读取与整页 UI | sysfs | S | ★ |
| A2 | CPU 缓存层级 L1D/L1I/L2/L3、NEON 支持 | cpuinfo JNI / sysfs `cpuN/cache/index*` | 读 `cache/index*/{level,type,size}`，NEON 走 `Build.SUPPORTED_ABIS` + `/proc/cpuinfo` Features | sysfs | S | ★ |
| A3 | 内存页：RAM 总量/已用/可用、Swap、ZRam 压缩率 + 弧形仪表 | `/proc/meminfo`、`/sys/block/zram0/` | 新增 `MemoryReader`，与现有采样节拍解耦（低频 1s） | 公开可读 | S | ★ |
| A4 | GPU 详情：型号、当前/最大频率、负载、governor | `/sys/class/kgsl/kgsl-3d0/*` 或 devfreq | `AuxReader` 已有 gpuSensor，补型号/governor/max 并出页 | sysfs | S | ★ |
| A5 | 显示详情：分辨率、刷新率、PPI、物理尺寸、HDR、广色域、支持模式列表 | Display API + `Display.Mode[]` | `DisplayManager` + `Display.mode` + `DisplayMetrics` | API | S | ○ |
| A6 | 存储详情：类型（UFS 3.1/4.0）、分区、文件系统、块大小、用量 | `/proc/partitions`、`/sys/block/*`、`StatFs` | 读 sysfs `ufs*/` 推断版本 + `StatFs` 算用量 | sysfs | M | ○ |
| A7 | SoC 数据库（900+ 型号 → 制程/架构/集群/GPU） | 内置 JSON 资产 | 需要新建数据资产；本项目机型窄（旗舰高通/天玑），做一个 30–50 条的精简表即可 | data | M | ○ |
| A8 | Vulkan 能力枚举（设备/堆/限制/扩展） | JNI + vulkan-info | 需 NDK + `libvulkan` 绑定 | native | L | × |
| A9 | OpenGL ES 版本与特性 | GLES 上下文查询 | `ActivityManager.deviceConfigurationInfo.glEsVersion` 已够用，不出独立页 | API | S | ○ |

## B. 电池

| ID | 功能点 | OpenMonitor 的做法 | 本项目的落点 | 依赖 | 成本 | 建议 |
|----|--------|-------------------|-------------|------|------|------|
| B1 | 电池状态卡强化：电量/电压/温度/健康/预计续航/充电剩余 | `BatteryManager` + `BatteryStats` | 现有 `AuxReader` 只取温度，补电压、电流、健康、剩余时间 | API | S | ★ |
| B2 | 电量历史折线图 + 区间筛选 | Room 落库 | 新表 `battery_sample`，或复用 `aux_sample` 加电压/电流列 | — | S | ★ |
| B3 | 应用耗电分解（图标 + % + mAh） | `UsageStatsManager` / `dumpsys batterystats` | `dumpsys batterystats --charged` 解析重、不稳；建议走 `UsageStats` 前台时长 × 平均功率近似，并明确标注「估算」 | API | M | ○ |
| B4 | 电池数据 CSV 导出 | — | 复用现有导出通道 | — | S | ★ |

## C. FPS 会话（与现有记录体系合并，不另建一套）

| ID | 功能点 | OpenMonitor 的做法 | 本项目的落点 | 依赖 | 成本 | 建议 |
|----|--------|-------------------|-------------|------|------|------|
| C1 | 会话列表：应用图标、平均 FPS、时长、时间戳 | 独立 FPS 会话表 | 现有「记录」已经按应用分组，补 FPS 聚合列即可 | — | M | ★ |
| C2 | 六项指标：最大/最小/平均、方差、流畅度（≥45fps 帧占比）、5% Low、最高温、平均功耗 | 离线统计 | 详情页新增「性能指标」卡片，纯 Kotlin 计算 | — | M | ★ |
| C3 | 卡顿图 / 帧时间曲线 | 帧时间序列 | 现有 `fps_sample` 只有 fps，需同时落 `frameTimeMs` 或由 fps 反推 | — | M | ★ |
| C4 | 应用切换时间轴 | 前台包名变更事件 | `ForegroundTracker` 已有事件源，记录期间落一条 `app_switch` | — | S | ★ |
| C5 | 导出 PNG（图表 + 设备信息 + 二维码横幅） | Canvas 合成 | 需自绘分享图，二维码要引依赖 | — | L | ○ |
| C6 | 导出 CSV 原始数据 | — | 已有导出基础 | — | S | ★ |
| C7 | 重命名 / 多选批量删除 | — | 记录列表补多选态 | — | S | ○ |
| C8 | 卡顿（jank）判定算法 | 帧间隔阈值 | 与 C3 同批做 | — | M | ★ |

## D. 进程管理

| ID | 功能点 | OpenMonitor 的做法 | 本项目的落点 | 依赖 | 成本 | 建议 |
|----|--------|-------------------|-------------|------|------|------|
| D1 | 进程列表：图标、名称、PID、CPU%、内存 MB | `ps -A -o` / `/proc` | root 走 `top -b -n 1`，兜底 `/proc/[pid]/stat` | root | M | ★ |
| D2 | 搜索 / 过滤（全部·仅应用）/ 排序 | 内存过滤 | 纯 UI | — | S | ★ |
| D3 | 结束进程 | `kill` / `am force-stop` | `Lane.CONTROL`，应用走 `am force-stop` 更安全 | root | S | ★ |
| D4 | 进程详情（线程、内存映射、fd） | — | 可选，`/proc/[pid]/{status,maps}` 直读 | root | M | ○ |

## E. 蜂窝网络

| ID | 功能点 | OpenMonitor 的做法 | 本项目的落点 | 依赖 | 成本 | 建议 |
|----|--------|-------------------|-------------|------|------|------|
| E1 | SIM 槽 / 运营商 / PLMN / MCC-MNC / RAT | TelephonyManager | 直接可做，双卡用 `SubscriptionManager` | API | S | ○ |
| E2 | RSRP / RSRQ / RSSI / SINR 实时曲线 | `SignalStrength` | 可做，需 `READ_PHONE_STATE` + 位置权限（A12+ 小区信息受限） | API | S | ○ |
| E3 | 服务小区 GCI/ECI/TAC/PCI、NR ARFCN | Telephony API | 可做，但字段可用性随机型/运营商差异大 | API | M | ○ |
| E4 | 邻区 + 载波聚合矩阵 | Telephony API | 字段极不稳定，多数机型拿不到 | API | M | × |
| E5 | RRC/NAS/IMS 信令抓取 + PCAP 导出 | native DIAG / QMI（仅高通） | 需 NDK + 基带协议解析，工程量等于一个独立项目 | native | L | × |
| E6 | 基带事件日志 | 同上 | 同上 | native | L | × |

> E 组整体与本项目「功耗溯源」主线弱相关。如果目标是排查「待机耗电是不是基带引起的」，E1+E2 就够；E5/E6 建议直接引用 OpenMonitor，不在本项目重造。

## F. 系统集成

| ID | 功能点 | OpenMonitor 的做法 | 本项目的落点 | 依赖 | 成本 | 建议 |
|----|--------|-------------------|-------------|------|------|------|
| F1 | 快捷设置磁贴：一键开关悬浮监控 | `TileService` | 新增 `MonitorTileService`，绑定现有 OSD 开关 | — | S | ★ |
| F2 | 桌面小组件：CPU 负载 / 温度 / 内存 | `AppWidgetProvider` + 直读 `/proc` | 用 RemoteViews，低频刷新（30min 系统下限 + 点击刷新），避免自身耗电 | — | M | ★ |
| F3 | 密钥认证（Key Attestation） | KeyStore attestation + 证书链解析 | 纯 Kotlin 可做：`KeyGenParameterSpec.setAttestationChallenge` + ASN.1 解析 OID 1.3.6.1.4.1.11129.2.1.17 | API | M | ○ |
| F4 | 隐私协议 / 首次引导 | 独立页 | 现有 `OnboardingScreen` 已覆盖，不重复 | — | — | × |

## G. 通用与外观

| ID | 功能点 | OpenMonitor 的做法 | 本项目的落点 | 依赖 | 成本 | 建议 |
|----|--------|-------------------|-------------|------|------|------|
| G1 | AMOLED 纯黑主题 | 主题枚举加一档 | `ThemeMode` 加 `AMOLED`，背景强制 #000、卡片 #0D0D0D | — | S | ★ |
| G2 | 动态取色（Material You） | Material Kolor | 与 MIUIX 主题体系冲突，收益低 | — | M | × |
| G3 | 繁体中文 / 日语 | 资源目录 | 加 `values-zh-rTW`、`values-ja`；文案量约 330 条 | — | M | ○ |
| G4 | 悬浮窗分项：FPS/CPU/内存/GPU/温度各自独立浮层 | 多窗口 | 现有 OSD 是单窗口多指标，拆多窗口会成倍增加功耗与遮挡风险；建议只补「精简模式」（2 行）与「完整模式」 | — | M | ○ |
| G5 | 数据本地化声明页 | 设置内说明 | 现有「关于」页补一段即可 | — | S | ○ |

---

## 建议的落地顺序

**第 1 批（数据层已经有的，先把信息补齐）**
A1 · A2 · A3 · A4 · B1 · G1 · F1

理由：全部走现有 `AuxReader` / `RootShellPool`，不新增依赖，一次能把「硬件与实时状态」这条线补满。

**第 2 批（把记录变成可分析的资产）**
C1 · C2 · C3 · C4 · C6 · C8 · B2 · B4

理由：`fps_sample` / `record` 表已有数据，补统计与图表即可，是当前产品最缺的一环。

**第 3 批（工具型页面）**
D1 · D2 · D3 · A5 · A6 · F2

**第 4 批（可选项，按需）**
A7 · B3 · E1 · E2 · F3 · G3 · G5

**不做**：A8（Vulkan）、E4/E5/E6（DIAG 信令）、G2（动态取色）、F4（已有）。
