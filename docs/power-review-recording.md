# 记录功能功耗剖析与优化方案

> 代码基线：`main` @ versionName 1.0.0（versionCode 2）
> 分析范围：数据记录链路（`MonitorRepository` 采样循环 → `RootManager` → `QptPowerReader`/`AuxReader` → Room）
> 结论针对 K90 Pro Max（8 个 QPT zone / 2 个 CPU 簇 / 8 核）实测量级推算，所有数值均标注了估算依据，未标注「实测」的均为结构性推算，需按第 6 节校准。

## 实现状态

| 项 | 状态 | 落地位置 |
|---|---|---|
| P0-1 采样命令零 fork（`read` 内建） | ✅ 已实现 | `QptPowerReader.kt`、`AuxReader.kt` |
| P0-2 FPS 改用 SurfaceFlinger | ✅ 已实现 | `core/fps/FpsReader.kt`（新增） |
| P0-2c FPS 图层可选（自动 / 手工指定） | ✅ 已实现 | `FpsReader.kt`、`QptViewModel.kt`、`QptApp.kt` |
| 包名白名单误杀（`tv.`/`org.`/`me.`） | ✅ 已修复 | `AuxReader.kt`、`AppInspector.kt` |
| 前台包名重复 dumpsys | ✅ 已合并为 2 s TTL 缓存 | `AppInspector.kt` |
| P0-3 后台预览 | ✅ 已改为 1 Hz 低频保活（曲线不回断） | `MonitorRepository.kt`、`MainActivity.kt` |
| P0-4 分级采样（快/慢双命令） | ✅ 已实现 | `MonitorRepository.kt` |
| P1-5 批量落库 + `synchronous=NORMAL` | ✅ 已实现 | `MonitorRepository.kt`、`QptDatabase.kt` |
| P1-6 扫描合并 + 5 min TTL | ✅ 已实现（TTL 为主） | `MonitorRepository.kt` |
| P1-7 详情页降采样 + 后台线程聚合 | ✅ 已实现（stride + `flowOn(Default)`） | `Daos.kt`、`QptViewModel.kt` |
| 时间基准改 `elapsedRealtime` + 固定相位 | ✅ 已实现 | `QptPowerReader.kt`、`MonitorRepository.kt` |
| P1-8 OSD 采集协程泄漏 | ✅ 已修复 | `QptMonitorService.kt` |
| 整机功耗 `power_now` 空值导致恒 null | ✅ 已修复（`BATC` 路径 + Kotlin 计算） | `AuxReader.kt` |
| `/proc/stat` guest 时间重复计数 | ✅ 已修复 | `AuxReader.kt` |

验证：`./gradlew assembleDebug` → BUILD SUCCESSFUL；采样命令片段在本机 bash 实测 5 个用例（节点存在 / 节点缺失静默 / 空文件 / 电池两条分支 / STAT 解析与字段数）。**FPS 新链路需真机验证**（见 9.3）。

---

## 0. 结论速览

| # | 根因 | 代码位置 | 资源类型 | 记录态开销占比（估算） |
|---|---|---|---|---|
| P0-1 | 单次采样触发 **~32 次进程 fork/exec**（`$(cat ...)` × 30 + `grep`/`sed`/`awk`） | `QptPowerReader.kt:44-52`、`AuxReader.kt:188-218` | CPU / 调度唤醒 | **35–45%** |
| P0-2 | **1 Hz `dumpsys activity` + `dumpsys gfxinfo`**，2 s 又一次全新 `dumpsys activity` | `AuxReader.kt:118-162`、`AppInspector.kt:58-66`、`MonitorRepository.kt:415-418` | CPU / binder / 内存分配 | **25–35%** |
| P0-3 | 预览循环**无生命周期门控**，App 退到后台仍以 5 Hz 常驻 `su` | `MonitorRepository.kt:180-183, 211-231` | 后台常驻 CPU | 静置态 **~100 mW 常驻** |
| P0-4 | 默认 **200 ms 全指标同频**，温变/核占用等慢变量也被 5 Hz 采样 | `Models.kt:29-34`、`MonitorRepository.kt:354` | 采样次数基数 | 放大 P0-1/P0-2 **5×** |
| P1-5 | 每 tick 一次 WAL 提交 + 每秒一次独立 FPS 提交，**落库与采样串行** | `MonitorRepository.kt:377-380, 406` | 存储 fsync / IO 阻塞 | 10–15% |
| P1-6 | `startRecording()` 每次全量重扫，**11 次 `su` 往返 + 80 次进程** | `MonitorRepository.kt:317`、`QptViewModel.kt:37` | 瞬时 CPU 尖峰 | 记录起始 0.5–1 s |
| P1-7 | 详情页订阅**整张表**，录制中打开历史 → 每 tick 全量重查（O(n²)） | `QptViewModel.kt:56-104`、`Daos.kt:41-45` | 主线程 CPU / 内存 | 可达 100% 单核 |
| P1-8 | OSD 采样协程**只增不减**（`hideOsd` 不取消 Job） | `QptMonitorService.kt:142-150` | 协程泄漏 | 随开关次数线性增长 |

**一句话结论**：功耗主体不是"读 sysfs"，而是**每次采样用 32 次 fork 换 32 个字节**，以及**用 `dumpsys` 这类重量级系统命令做 1 Hz 轮询**。这两处把一件原本 <1 ms 的工作放大到了 20–40 ms。

另有 3 个与"测量可信度"直接相关的缺陷（第 5.2 节）：整机功耗在本机恒为 `null`、guest 时间重复计数、时间差用 `currentTimeMillis` 会在 NTP 校时时产出假功耗尖峰。

---

## 1. 先厘清一个假象：为什么 App 自身 CPU 只有 1.2%

`docs/power-reading.md:198` 记录"App 进程单核占用约 1.2%"，与"功耗偏高"并不矛盾：

- 采样工作真正发生在 **root shell 子进程**里（`su` → `sh` → 32 个 `toybox cat`/`grep`/`sed`/`awk`），CPU 记在 `uid 0` / `shell` 名下；
- `/proc/<app-pid>/stat` 只统计 App 进程自身，**完全看不到这部分开销**；
- libsu 的 `Shell.getShell()` 常驻一个 root shell 进程，其 CPU 也不计入 App。

所以"低 CPU 占用"是统计口径造成的错觉。**验证必须聚合 `su`/`sh`/`toybox`/`dumpsys` 全部进程**（见 6.2）。这也是本项目最容易踩的度量陷阱。

---

## 2. 一次采样 tick 到底做了什么

`MonitorRepository.kt:370-420`，默认 `interval=200ms`：

```
tick(n)
├─ rootManager.execute(cachedCommand)          ← 1 次 libsu IPC + shell 解析 ~1.5 KB 脚本
│   ├─ for 8 zones: cat power_uw + cat energy_uj   → 16 次 fork/exec
│   ├─ 2 clusters: cat scaling_cur_freq            →  2 次
│   ├─ 温度: cpu×N + ddr + gpu + batt: cat temp     →  6~8 次
│   ├─ 电池: cat power_now                          →  1 次（本机恒为空，见 5.2）
│   ├─ GPU: cat gpuclk + gpubusy                    →  2 次
│   ├─ DDR: cat cur_freq                            →  1 次
│   └─ grep /proc/stat | sed                        →  2 次 + 管道
├─ parse ×2（纯内存，可忽略）
├─ database.withTransaction { 8 张表 insert }        ← 1 次 WAL 提交 + fsync
├─ [每 1 s] auxReader.sampleFps()                    ← dumpsys activity + dumpsys gfxinfo
│                                                     （串行阻塞在本循环里，见 P0-2）
├─ [每 2 s] appInspector.isForeground()              ← 又一次完整 dumpsys activity
└─ delay(intervalMs)
```

**单 tick 外部进程数：约 30–32 个；5 Hz 下 ≈ 150–160 进程/秒。**

`AuxReader.sampleFps()` 与 `isForeground()` 都是**同步串行**执行，因此实际节奏是
`200ms + dumpsys 耗时(60–200ms)`，采样周期在 200–400 ms 之间抖动，而时间戳在读取**之前**打点（`MonitorRepository.kt:371`），导致：

- 能量差分法的 Δt 偏小 → `computedPowerUw` 系统性偏高；
- 图表 X 轴时间与实际采样时刻错位。

---

## 3. 逐项剖析

### P0-1 采样命令的 fork 风暴（最大单项）

**证据**：

```kotlin
// QptPowerReader.kt:48-51  —— 每个 zone 两个 cat
"e=\$(cat \"\$z/energy_uj\" 2>/dev/null); " +
"p=\$(cat \"\$z/power_uw\" 2>/dev/null); " +
"echo \"QPT|\$z|\$p|\$e\"; done"
```

`AuxReader.buildAuxCommand()`（`:188-218`）同样是"每个指标一个 `echo "...$(cat file)"`"的模式，外加 `grep|sed` 处理 `/proc/stat`、`awk` 计算电池功率。

**机理**：`$(cat f)` 中 `cat` 是**外部可执行文件**，每次都要 `fork + exec + 动态链接 + 读 sysfs + 写管道 + exit`。在 ARM64 Android 上单次约 **0.5–1.0 ms** CPU（含调度唤醒开销），32 次即 **16–32 ms/tick**。5 Hz 下 ≈ **80–160 ms CPU/s**，即**单个核心的 8–16%**。

更贵的是"唤醒"而非"指令"：每次 fork 都是短任务，调度器倾向于把它放到**大核**（wake-up migration），频繁唤醒会阻止 CPU 进入深度 idle，单核 10% 占用的实际功耗远高于 10% 的线性外推。

**修法（零风险、纯等价改写）**：`read` 是 shell 内建命令，重定向由 shell 自己打开文件，**不产生任何进程**：

```bash
# 优化后：0 次 fork
for z in '/sys/.../qpt:0' ... ; do
  p= e=
  read -r p 2>/dev/null < "$z/power_uw"
  [ -n "$p" ] || read -r e 2>/dev/null < "$z/energy_uj"   # 仅在 power_uw 缺失时才读能量
  printf 'QPT|%s|%s|%s\n' "$z" "$p" "$e"
done
```

同时消除两处浪费：
1. **`energy_uj` 不必要地每 tick 都读**（`power_uw` 存在时它只用于累计能量统计，1 Hz 足够）；
2. **`/proc/stat` 用 `grep|sed` 管道** → 改为 `while read` 循环（内建，0 fork）：

```bash
while read -r c u n s i w irq sirq st; do
  case "$c" in cpu[0-9]*) printf 'STAT|%s|%s|%s|%s|%s|%s|%s|%s\n' "${c#cpu}" "$u" "$n" "$s" "$i" "$w" "$irq" "$sirq";; esac
done < /proc/stat
```

**预期**：进程数 32 → 0（每 tick 仅剩 1 次 libsu IPC），该路径 CPU 从 ~12% 单核降至 **<1%**。

---

### P0-2 FPS 与前台检测的 dumpsys（第二大头，且污染被测数据）

**证据**：

```kotlin
// AuxReader.kt:120-126  —— 每秒一次
"pkg=$(dumpsys activity activities | grep -m1 'topResumedActivity=' | sed ...); " +
"dumpsys gfxinfo \"$pkg\" | awk '/Total frames rendered/{print ...}'"
```

```kotlin
// AppInspector.kt:59-61 —— 每 2 秒又一次完整 dump
"dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity='"
```

**机理**：

- `dumpsys activity activities` 会序列化**全部 Activity/Task 栈**，即使在 `grep -m1` 提前关闭管道（SIGPIPE）的情况下，system_server 侧的准备成本仍为 **50–150 ms**；
- `dumpsys gfxinfo <pkg>` 需要向目标 App 进程 binder 调用取帧统计，**会短暂占用目标 App 的 binder 线程并触发其帧统计遍历**——也就是说，**测量行为本身在拖慢被测量的 App**，FPS 读数被系统性压低，且抖动增大；
- 同一份"前台包名"在 1 s 内被解析了两次（`sampleFps` 内部一次、`isForeground` 一次），是纯粹的重复劳动；
- `Shell.cmd(...).exec()` 把输出缓冲成 `List<String>` 再遍历，短生命周期的大对象会带来 GC 压力。

**修法**：
1. 引入统一的 `ForegroundTracker`，前台包名 **2 s 内复用**（`sampleFps` 的解析结果直接喂给 `isForeground`）；
2. `isForeground` 改为**优先走 UsageStats `ACTIVITY_RESUMED`**（`AppInspector.kt:68-85` 已有实现，零 dumpsys、零 root），仅当返回 null 时回退 dumpsys；
3. FPS 采样 1 s → **2 s**（FPS 曲线仍然平滑，人眼无感）；
4. （可选，后续）`dumpsys gfxinfo` 换 `dumpsys SurfaceFlinger --latency`，输出仅最后 128 帧时间戳，成本更低。

**预期**：dumpsys 调用从 **1.5 次/秒 → 0.5 次/秒**，且消除测量污染。

---

### P0-3 预览循环无生命周期门控（后台常驻耗电元凶）

**证据**：

```kotlin
// MonitorRepository.kt:180-183
if (enabled && samplerJob?.isActive != true) {
    startPreview()          // 只要 QPT enabled 就常驻
}
```

`refreshDeviceState()` 会在 3 个地方被调用：`QptViewModel.init`（每次打开 App）、`QptMonitorService.onCreate`（前台服务，`START_STICKY`）、`startRecording()`。一旦触发，`previewJob`（`:219-230`）就**以 5 Hz 无限循环**，即使：

- App 已退到后台、屏幕已熄灭；
- 没有任何 UI 在消费 `latestSamples`；
- 用户只是"打开过一次 App"。

这是"感觉这软件很耗电"的最直接来源：**150 进程/秒 × 后台常驻**。

**修法**：给采样循环加可见性门控。

```kotlin
// MainActivity.onStart / onStop
override fun onStart() { super.onStart(); repository.setUiVisible(true) }
override fun onStop()  { super.onStop();  repository.setUiVisible(false) }

// MonitorRepository
private fun shouldRunPreview() =
    _uiVisible && _state.value.qptEnabled && samplerJob?.isActive != true && !_osdEnabled.value
```

`startPreview()` 开头加 `if (!shouldRunPreview()) return`；`setUiVisible(false)` 时 `stopPreview()`。OSD / 记录态不受影响（它们有独立的生命周期理由）。

**预期**：后台静置从 `5 Hz 采样 + 1 Hz dumpsys` 降为 **0**，后台功耗回到系统的"仅前台服务通知"水平。

---

### P0-4 默认 200 ms 全指标同频

**证据**：`Models.kt:29-34`（`Ms100/Ms200/Ms500/Ms1000`，默认 `Ms200`，`MonitorRepository.kt:76`）；`buildAuxCommand()` 把所有指标塞进同一条命令、同一频率。

**问题**：不同物理量的**有效带宽完全不同**：

| 指标 | 物理变化速率 | 合理采样率 | 现状 |
|---|---|---|---|
| QPT 各 rail 功率 | 快（ms 级） | 5–10 Hz | 5 Hz ✅ |
| CPU 簇频率 | 快 | 2–5 Hz | 5 Hz ✅ |
| 每核占用（jiffies 差分） | 慢 | **1 Hz** | 5 Hz ❌（且 200 ms 窗口下统计噪声极大） |
| 温度（thermal zone） | 极慢（秒级） | **0.5–1 Hz** | 5 Hz ❌ |
| GPU / DDR 频率 | 中 | 2 Hz | 5 Hz ⚠️ |
| 整机功耗 | 中 | 1–2 Hz | 5 Hz ⚠️ |

**修法**：分级采样（快慢双命令），并顺手提升数据质量——`/proc/stat` 的 jiffies 分辨率约 10 ms，200 ms 窗口只有约 20 个 tick，占用率量化误差可达 ±5%，**1 s 窗口反而更准**。

```kotlin
val fastCmd = qptReader.buildSampleCommand(zones)                       // 仅 QPT
val slowCmd = qptReader.buildSampleCommand(zones) + "; " + auxCommand    // QPT + 附加项
var tick = 0
while (isActive) {
    val cmd = if (tick % slowEvery == 0) slowCmd else fastCmd            // slowEvery = 5
    ...
    tick++
}
```

**预期**：附加项 sysfs 读取量与解析量降 **5×**，与 P0-1 叠加后单 tick 成本进入"可忽略"区间。

---

### P1-5 落库与采样串行 + 高频 WAL 提交

```kotlin
// MonitorRepository.kt:377-380
database.withTransaction {
    if (qptSamples.isNotEmpty()) sampleDao.insertAll(...)
    insertAuxBatchInTransaction(recordId, timestamp, auxBatch)
}
// :406 —— FPS 在事务外，单独一次提交
auxSampleDao.insertFps(...)
```

**问题**：
1. **5 次事务/秒**（+1 次 FPS 独立提交 = 6 次），每次 `COMMIT` 都要 WAL fsync。Room 默认 `synchronous=FULL`，每次提交至少一次 fsync —— 这是 UFS 唤醒 + 写放大的主要来源，且**fsync 抖动（可达 10–20 ms）会直接推迟下一个采样点**，因为采样和落库是同一个协程串行执行的；
2. 数据量：`28 行/tick × 5 Hz ≈ 140 行/秒 ≈ 50 万行/小时 ≈ 20–30 MB/小时`（1 Hz 时约 4–6 MB/h）。导出、详情页聚合都会随之变慢；
3. FPS 单条 insert 独立提交，纯浪费。

**修法**：
- 采样协程只负责**采集**，把样本推进 `Channel`；独立 writer 协程**攒 1 s（或 5 个 tick）合并成一次事务**；
- FPS 样本并入同一批次；
- `RoomDatabase.Callback.onOpen` 中执行 `PRAGMA synchronous=NORMAL`（WAL 下断电最多丢最后一个事务，对本场景完全可接受；需实测确认该 PRAGMA 作用于写连接）。

**预期**：事务数 6/s → **1/s**，写放大与 fsync 唤醒降 6×，采样节奏不再被 IO 抖动牵引。

---

### P1-6 `startRecording()` 的全量重扫

```kotlin
// MonitorRepository.kt:316-317
suspend fun startRecording(autoStopOnAppExit: Boolean = false): Boolean {
    refreshDeviceState()          // 每次都全量扫描
```

`refreshDeviceState()`（`:145-184`）依次执行：`hasRoot` / `isAvailable` / `isEnabled` / `scanZones`（`find` + 批量 `cat`）/ `scanClusters`（循环 `cat`）/ `scanTempZones`（循环 `cat` + 电池探测）/ `isBatteryPowerAvailable` / `scanGpuSensor`（2 次）/ `scanDdrFreqPath`（循环 `basename`）——**11 次 `su` 往返、60–80 个进程**。

后果不止是慢：这段扫描发生在**记录开始的前一刻**，它的功耗尖峰正好落在"被测应用启动"的窗口里，污染记录开头的基线数据。

**修法**：
1. 加 TTL 缓存（设备拓扑在运行期不会变）：`if (!force && now - lastScan < 5min) return`；
2. 把 11 次 exec **合并为 1 条脚本**（分段打标签 `ROOT|`、`QPTDIR|`、`ZONE|`、`CLU|`、`TEMPZ|`、`BATAV|`、`GPU|`、`DDR|`），一次往返完成全部探测；
3. `QptViewModel.init`（`:37`）改为只读缓存，不触发全扫。

**预期**：记录启动耗时从 0.5–1 s 降到 <100 ms，起始基线不再被自身扫描污染。

---

### P1-7 详情页订阅整表（录制中打开历史会打满一个核）

```kotlin
// QptViewModel.kt:72-103
combine(observeRecord, observeSamples, auxA, auxB) { ... stats = repository.stats(samples) ... }
    .stateIn(viewModelScope, ...)
```

**问题**：
- `observeSamples(recordId)` = `SELECT * FROM samples WHERE recordId=?`（`Daos.kt:41-45`），**无分页、无聚合、无 LIMIT**；
- 录制期间每 200 ms 写入 `samples`/`aux` 表 → Room 表级失效 → **每 tick 触发 7 条 Flow 重新查询**；
- 若打开的正是"正在录制"的记录，等于每 tick 把整表拉回内存并重算 `groupBy`/`average`（`stats()`、`clusterAvgFreqs`…），**复杂度 O(采样点总数) × 5 Hz → O(n²)**；
- `combine` 的下游变换运行在 `viewModelScope`（Main），**主线程做全量聚合**，直接掉帧；
- `.distinctUntilChanged()` 无效——数据每 tick 都在变。

**修法**（按成本递增）：
1. **最低成本**：`detailId` 记录的 `endedAtMs == null` 时，不订阅 Flow，改为 5 s 定时快照；
2. **推荐**：图表数据改用**SQL 侧分桶聚合**（`GROUP BY timestampMs/:bucket`，把任意长度的记录压到 ≤2000 点），原始数据只在导出时全量读取；
3. 聚合计算 `flowOn(Dispatchers.Default)`，绝不留在 Main。

---

### P1-8 OSD 采集协程泄漏

```kotlin
// QptMonitorService.kt:134, 142-150
private fun showOsd() { ...; observeOsdSample() }   // 每次 show 都新起一个 collector
private fun hideOsd()  { ... }                       // 从不取消
```

`showOsd()` 在 `osdRoot == null` 时才执行，但**每次 OSD 关→开循环都会新增一个永不退出的 collector**；`hideOsd()` 只移除 View，不取消 Job。切换 N 次后有 N 个协程同时 `combine` + `renderOsd` + 写 `TextView`。

**修法**：`private var osdSampleJob: Job?`，`observeOsdSample()` 先 `cancel()` 再 `launch`，`hideOsd()` 里 `cancel()` 并置空。

---

### P2 调度与健壮性

| 问题 | 位置 | 影响 | 建议 |
|---|---|---|---|
| 时间基准用 `System.currentTimeMillis()` | `MonitorRepository.kt:222/261/371`、`QptPowerReader.kt:67` | NTP 校时跳变 → 能量差分产出**假功耗尖峰**（可达成百瓦） | 差值/调度一律 `SystemClock.elapsedRealtime()`，墙钟只用于展示 |
| 周期 = `delay(interval)`，无相位补偿 | `:419`、`:228`、`:275` | 周期 = interval + 工作耗时，持续漂移，实际采样率低于标称 | `next += interval; delay(next - now)` 固定相位 |
| `RootManager.execute()` 无超时、不检查退出码 | `RootManager.kt:21-23` | root shell 卡住 → 采样循环永久阻塞；读取失败静默变 0 | `withTimeoutOrNull(2s)` + 连续失败计数降级 |
| 无 `PARTIAL_WAKE_LOCK` | 全局 | 熄屏 suspend 后 `delay` 不触发 → **熄屏记录静默断点**（这是"不耗电但数据缺失"的另一面，需在产品上明确） | 明确产品语义：熄屏记录要么不支持（推荐），要么显式持有 wakelock 并在 UI 标注功耗代价 |
| `QptViewModel` 在 `setContent` 内 new | `MainActivity.kt:45` | 每次 Activity 重建 → 新 VM → `init` 再触发一次全量扫描（11 次 su） | 改用 `viewModel()` / `viewModelFactory` |
| OSD 悬浮窗 2 Hz 重绘 | `MonitorRepository.kt:270-274` | 额外 overlay 图层可能使 GPU 合成退化为 client composition，游戏中额外耗电并压 FPS | OSD 默认关闭（已是）；开启时降到 1 Hz |

---

## 4. 优化方案（可直接落地的改写）

### 4.1 `QptPowerReader.buildSampleCommand` —— 零 fork

```kotlin
fun buildSampleCommand(zones: List<PowerZone>, includeEnergy: Boolean): String =
    if (zones.isEmpty()) "" else buildString {
        for (z in zones) {
            append("p=; read -r p 2>/dev/null < '").append(z.path).append("/power_uw'; ")
            if (includeEnergy) {
                append("e=; read -r e 2>/dev/null < '").append(z.path).append("/energy_uj'; ")
            } else {
                append("e=; [ -n \"\$p\" ] || read -r e 2>/dev/null < '").append(z.path).append("/energy_uj'; ")
            }
            append("printf 'QPT|%s|%s|%s\\n' '").append(z.path).append("' \"\$p\" \"\$e\"; ")
        }
    }
```

> `read` 是 mksh/toybox sh 的内建命令，重定向由 shell 打开文件，**不 fork**。`2>/dev/null` 放在输入重定向**之前**，才能吞掉文件不存在时的报错。

### 4.2 `AuxReader.buildAuxCommand` —— 零 fork + 电池计算下沉到 Kotlin

```kotlin
fun buildAuxCommand(clusters, sensors, gpu, ddrFreqPath, withStat: Boolean): String {
    val commands = mutableListOf<String>()
    clusters.forEach {
        commands += "v=; read -r v 2>/dev/null < '${it.path}/scaling_cur_freq'; printf 'FREQ|${it.name}|%s\\n' \"\$v\""
    }
    sensors.cpuZones.forEach {
        commands += "v=; read -r v 2>/dev/null < '$it/temp'; printf 'TEMP|cpu|%s\\n' \"\$v\""
    }
    sensors.ddrZone?.let { commands += "v=; read -r v 2>/dev/null < '$it/temp'; printf 'TEMP|ddr|%s\\n' \"\$v\"" }
    sensors.gpuZone?.let { commands += "v=; read -r v 2>/dev/null < '$it/temp'; printf 'TEMP|gpu|%s\\n' \"\$v\"" }
    if (sensors.hasBatteryTemp) commands += "v=; read -r v 2>/dev/null < /sys/class/power_supply/battery/temp; printf 'TEMP|batt|%s\\n' \"\$v\""

    // power_now 为空是本机常态 → 必须用 -s（非空）判定，且电流×电压交给 Kotlin 用 Long 算
    commands += "if [ -s /sys/class/power_supply/battery/power_now ]; then v=; " +
        "read -r v < /sys/class/power_supply/battery/power_now; printf 'BAT|%s\\n' \"\$v\"; " +
        "elif [ -s /sys/class/power_supply/battery/current_now ] && [ -s /sys/class/power_supply/battery/voltage_now ]; then " +
        "c=; v=; read -r c < /sys/class/power_supply/battery/current_now; " +
        "read -r v < /sys/class/power_supply/battery/voltage_now; printf 'BATC|%s|%s\\n' \"\$c\" \"\$v\"; fi"
    // GPU / DDR 同理
    if (withStat) commands += "while read -r c u n s i w irq sirq st; do " +
        "case \"\$c\" in cpu[0-9]*) printf 'STAT|%s|%s|%s|%s|%s|%s|%s|%s\\n' \"\${c#cpu}\" \"\$u\" \"\$n\" \"\$s\" \"\$i\" \"\$w\" \"\$irq\" \"\$sirq\";; esac; done < /proc/stat"
    return commands.joinToString("; ")
}
```

解析侧新增 `BATC`：

```kotlin
line.startsWith("BATC|") -> {
    val p = line.substringAfter('|').split('|')
    val c = p.getOrNull(0)?.trim()?.toLongOrNull()
    val v = p.getOrNull(1)?.trim()?.toLongOrNull()
    if (c != null && v != null) {
        val pW = kotlin.math.abs(c) * v          // µA × µV = pW，最大 ~5e13，Long 安全
        devicePower = pW / 1_000_000L            // → µW
    }
}
```

### 4.3 采样循环：分级 + 固定相位 + 解耦 FPS

```kotlin
samplerJob = scope.launch {
    var tick = 0L
    var next = SystemClock.elapsedRealtime()
    while (isActive) {
        val ts = SystemClock.elapsedRealtime()
        val slow = tick % slowEvery == 0L
        val cmd = if (slow) slowCmd else fastCmd
        val lines = rootManager.execute(cmd)
        // … parse，样本写入 Channel（非直接落库）
        tick++
        next += intervalMs
        val sleep = next - SystemClock.elapsedRealtime()
        if (sleep > 0) delay(sleep) else next = SystemClock.elapsedRealtime()   // 防追帧死循环
    }
}
// FPS 独立协程，不再阻塞采样相位
fpsJob = scope.launch {
    while (isActive) {
        delay(fpsIntervalMs)                       // 2000
        auxReader.sampleFps()?.let { pendingFps = it }
    }
}
```

### 4.4 落库批量化

```kotlin
private val writeQueue = Channel<PendingBatch>(Channel.UNLIMITED)
// writer：攒够 1 s 或 8 条批次，合并为 1 次 withTransaction
val writerJob = scope.launch(Dispatchers.IO) {
    val pending = mutableListOf<PendingBatch>()
    for (batch in writeQueue) {
        pending += batch
        if (pending.size >= batchSize || SystemClock.elapsedRealtime() - pending.first().ts > 1000) {
            database.withTransaction { pending.forEach { insertOne(it) } }
            pending.clear()
        }
    }
}
```

---

## 5. 预期收益

### 5.1 功耗（记录态，5 Hz 默认配置）

| 来源 | 现状（估算） | 优化后 | 依据 |
|---|---|---|---|
| 32 次 fork/exec × 5 Hz | 80–160 ms CPU/s（8–16% 单核） | ~0 | 单次 fork+exec 0.5–1.0 ms |
| libsu IPC 往返 × 5 Hz | 5–15 ms/s | 不变 | 1–3 ms/次 |
| QPT sysfs 16 次读 | 5–16 ms/s | 2–5 ms/s（仅 power_uw） | 每次 sysfs 读 ~0.1–0.3 ms |
| dumpsys（1.5 次/s） | 90–300 ms/s（9–30% 单核） | 30–80 ms/s（0.5 次/s） | 单次 60–200 ms |
| WAL 提交 6 次/s | 6–18 ms/s + fsync 抖动 | 1 次/s | 1–3 ms/次（抖动可达 20 ms） |
| **合计（记录态附加功耗）** | **≈ 250–500 mW** | **≈ 60–110 mW** | 按 5 W 游戏负载下整机 5 W 计，占比 5–10% → 1.2–2.2% |

> 以上为**结构性推算**，用于确定优化优先级与量级判断；绝对数值必须用第 6 节的 A/B 实测替换。三处最不确定的量：fork 单次成本、`dumpsys activity` 的实际耗时、QPT `power_uw` 读取是否触发驱动内重算。

**外部锚点校准**：原作者在酷安发布帖中自述「这玩意有一定量 cpu 开销……对整机功耗的影响大概是 **0.2 W**」。这与本表 250–500 mW 的推算同量级，说明量级判断成立，但**上界偏保守**——实际稳态更接近 200 mW 一侧（作者很可能是在较优条件下测的）。**后续一律以 0.2 W 作为基线锚点**，优化目标相应收紧为 **≤ 80 mW**（即砍掉约 60%）。
> 该帖同时点出了本报告第 6.4 节的自指偏差："所以整机功耗那块相比 scene 帧率记录，功耗会偏高"——**工具自身在耗电，而它在测整机功耗**。这一句反过来说明：降低自身开销不只是省电，更是**提升测量数据可信度**，与项目「拒绝猜测，从数据出发」的立身之本直接冲突，应当作为一等需求而非优化项。

**分层收益（可分批验收）**：
- 仅做 P0-1（零 fork 命令）：采样侧 CPU 降 **80–90%**，整机功耗降 **约 80–150 mW**；
- 再做 P0-2（dumpsys 降频 + 复用）：再降 **约 50–100 mW**；
- 再做 P0-3（后台门控）：**后台静置功耗降 ~100%**（这是用户主观感受最强的一项）；
- P0-4 + P1-5（间隔 1 s + 批量落库）：记录态总量再降 **40–50%**，数据量降 80%。

### 5.2 顺带修复的正确性缺陷

| 缺陷 | 位置 | 后果 |
|---|---|---|
| `power_now` 用 `-f`（存在）而非 `-s`（非空）判定 | `AuxReader.kt:296`、`:178-182` | K90 上该文件存在但为空 → 走错分支 → **整机功耗整场记录恒为 null**，而 `devicePowerAvailable=true` 仍在 UI 显示空值 |
| `/proc/stat` 把 `guest`/`guest_nice` 计入 total | `AuxReader.kt:311` | guest 时间已在 user/nice 中统计 → **CPU 占用率系统性虚高**（虚拟化场景可差数个百分点） |
| 能量差分用墙钟 | `QptPowerReader.kt:66-67` | NTP 校时/时区跳变 → 假功耗尖峰 |
| `RootManager.execute` 忽略退出码 | `RootManager.kt:21-23` | 读取失败静默为 0，与"真实 0 功耗"无法区分 |

---

## 6. 验证方式

### 6.1 微基准：直接量化"一次采样要花多少 CPU"（无需外部设备，10 分钟内可完成）

```bash
# 旧命令 / 新命令各跑 200 次，比较 real/user/sys
adb shell su -c 'time sh -c "i=0; while [ \$i -lt 200 ]; do <待测命令> >/dev/null; i=\$((i+1)); done"'
```

判定标准：新命令 `user+sys` 应下降 **一个数量级以上**（预期 200 次从 3–6 s 降到 0.2–0.5 s）。

### 6.2 进程级归因：把 `su`/`sh`/`toybox` 算进来（关键，别只看 App 自己）

```bash
# 记录中采 60 s，按进程聚合
adb shell top -b -n 1 -o PID,USER,%CPU,ARGS | grep -E "su|sh|toybox|dumpsys|powermonitor"
adb shell dumpsys cpuinfo | grep -E "TOTAL|powermonitor|root|shell"

# fork/exec 客观计数（perfetto，最硬的证据）
adb shell perfetto -o /data/misc/perfetto-traces/fork.pftrace -t 10s \
  sched/sched_process_fork sched/sched_process_exec
# trace_processor 聚合后：旧版应 ≈ 1500 次/10s，新版应 ≈ 10–20 次/10s
```

### 6.3 系统级功耗归因

```bash
adb shell dumpsys batterystats --reset
# —— 跑固定 10 分钟负载 ——
adb shell dumpsys batterystats --charged com.qpt.powermonitor | head -80   # 看 Estimated power use (mAh) / uid cpu
adb shell dumpsys power | grep -i "wake lock"                              # 确认无 wakelock（解释熄屏断点）
```

### 6.4 A/B 实验协议（必须做，否则数值不可信）

**固定条件**：飞行模式、亮度锁定 50%、音量 0、性能模式固定、同一游戏同一场景 10 分钟、电量 60–80%、每次实验前静置冷却 10 分钟使温度回到基线。

**三组对照**（关键：把"命令开销"和"频率开销"拆成两个独立变量）：

| 组 | 版本 | 间隔 | 目的 |
|---|---|---|---|
| A | 当前版本 | 200 ms | 基线 |
| B | 仅改命令（零 fork + dumpsys 降频） | 200 ms | 隔离 P0-1/P0-2 的纯收益 |
| C | 全部优化 | 1000 ms | 最终形态 |

每组重复 3 次，取中位数；测量指标：外部 USB 功率计的平均功率（**首选**）、`batterystats` 的 mAh/时长。

**自指误差提醒**：本工具在测量自己的功耗，内部 `devicePowerUw`/`soc` 数据存在自指偏差，只能作**相对比较**，绝对值以外部功率计或 `batterystats` 为准。

### 6.5 验收阈值（建议写进 CI/发版检查）

- 每 tick 外部进程 fork ≤ **2**，每 10 s 总 fork ≤ **20**；
- 记录态附加功耗 ≤ **100 mW**（相对同场景不记录时）；
- 采样抖动：实际间隔 p95 ≤ **1.10 × interval**（当前 5 Hz 下预计 p95 ≈ 1.8×，必须改善）；
- 记录中打开历史详情：主线程单帧 ≤ 16 ms，无 ANR；
- DB 增长 ≤ **6 MB/h @1 Hz**、≤ 30 MB/h @5 Hz；
- 熄屏 10 分钟内不产生"伪连续"数据（要么有数据且说明持锁，要么明确标记断点）。

---

## 7. 落地顺序与风险

**建议分 4 个 PR，每个都可独立验证、独立回滚**：

1. **PR-1（纯性能、零语义变更）**：4.1 + 4.2 零 fork 命令 + `BATC` 解析 + P1-8 协程泄漏。
   风险：极低（输出格式不变）。验收：数据与旧版逐条 diff 一致，CPU 大幅下降。
2. **PR-2（调用频次 + FPS 数据源）**：P0-2 dumpsys 复用/降频 + **第 9 节 SurfaceFlinger 替换 `gfxinfo`** + P0-4 分级采样 + 固定相位时间基准。
   风险：中（采样率与 FPS 数据源语义变化，会影响图表密度与读数；需同步调整图表显示与导出说明）。验收：6.4 的 B 组 + 9.3 的 FPS 判据。
3. **PR-3（后台行为）**：P0-3 生命周期门控 + P1-6 扫描合并与 TTL。
   风险：中（后台不再刷新，若有用户依赖"退到后台继续看曲线"需产品确认）。验收：静置 30 min 功耗对比。
4. **PR-4（存储与详情页）**：P1-5 批量落库 + P1-7 分桶聚合查询。
   风险：中（涉及 DAO 与详情页数据路径，需回归导出）。

**必须一起改的语义问题**：熄屏后无 wakelock 导致采样暂停。当前实现"不耗电但数据缺失"，这是一个隐藏的产品契约，建议在 UI 明确提示，而不是默默产生断点。

---

## 8. 与原作者公开说法的交叉验证

来源：酷安发布帖（Alita23333，2026-08-13，REDMI K90 Pro Max）。

| 作者说法 | 代码印证 | 结论 |
|---|---|---|
| "对整机功耗的影响大概是 0.2 W" | 与 5.1 节推算同量级（250–500 mW） | 量级成立，**以此作为基线锚点**，目标收紧到 ≤80 mW |
| "整机功耗那块相比 scene 帧率记录，功耗会偏高" | 工具自身 0.2 W 计入被测整机功耗（自指偏差） | 降低自身开销 = 提升数据可信度，应作为**一等需求** |
| "FPS 检测有 bug，懒得修……（修不好）" | 见第 9 节：数据源选错，非实现 bug | 可修，且修完顺带干掉 P0-2 的主要成本 |
| "OSD 不记录 / 悬浮球记录当前应用 / 通知面板控制" | `QptMonitorService.kt` 行为一致 | 当前代码即发布版，无隐藏实现 |
| "debug_0/2/4 语义不明，原样展示" | `parseSampleLines` 不做映射，按 `name` 透传 | 处理方式正确，不应"猜"名称 |
| "仅供 8 Elite Gen5 使用" | `README` 写 SM8850；`minSdk 33` 但 zone 动态发现 | 代码本身不限机型，限制来自实测范围，属于文档表述 |

结论：**分析对象与线上发布版一致**，不存在"作者已私下优化"的落差；第 3 节的问题在发布版中全部存在。

---

## 9. FPS 采集为什么"修不好"，以及怎么真正修好

作者自述 FPS 有 bug 且"修不好"。从代码看，这不是实现瑕疵，是**数据源选错**，三个缺陷叠加：

### 9.1 三个叠加的缺陷

1. **包名白名单过窄**（`AuxReader.kt:132`，同样的问题在 `AppInspector.kt:64`）

   ```kotlin
   .takeIf { it.startsWith("com.") || it.startsWith("android.") }
   ```

   `tv.danmaku.bili`、`org.telegram.messenger`、`me.ele`、`cn.wps.moffice` 等**合法包名被直接判为无效** → `pkg = null` → `:136` 返回 null → 这些应用的 FPS **恒为空**。

2. **`gfxinfo` 只统计 HWUI 帧**（根本原因）

   `Total frames rendered` 来自 HWUI/RenderThread 的帧统计。游戏用 SurfaceView + 自己的 EGL/Vulkan 线程渲染，**帧根本不进 HWUI 计数器** → 计数长期不变 → `delta <= 0` → `:154` 主动返回 null。

   **越是重负载游戏，越测不到帧**。这与"功耗工具主要服务于游戏调校"的定位正好相反——所以作者会觉得"修不好"：换任何阈值、任何重试都救不回来。

3. **静默失败不可观测**（`MonitorRepository.kt:403-411`）

   `sampleFps()` 返回 null 至少有 4 种互不相同的原因：无前台包名 / gfxinfo 无输出 / 应用切换基线重置 / 真的没新帧。调用方一律当作"本秒没数据"丢弃，用户只看到 FPS 曲线缺段，无法判断是工具坏了还是场景本来如此。

### 9.2 替换方案：读 SurfaceFlinger 的呈现帧计数

思路：读主显示的**呈现帧计数**，两次采样做差除以时间。全屏游戏下显示帧率 = 游戏帧率；成本是一次 binder 调用，比 `dumpsys gfxinfo` 便宜一到两个数量级。

| 路径 | 命令 | 优点 | 代价 |
|---|---|---|---|
| A（推荐） | `dumpsys SurfaceFlinger --latency <layer>` | 输出最后 128 帧时间戳，语义最准，可算 p95 帧耗时 | 需先 `--list` 拿层名并缓存 |
| B（最省） | `service call SurfaceFlinger 1013` | 单次 binder，返回显示帧计数 | 事务号是 AIDL 实现细节，需本 ROM 验证 |

无论走哪条，都同时解决三件事：**FPS 对游戏有效**、**每秒省掉一次 `dumpsys activity` + `dumpsys gfxinfo`**（P0-2 的主要成本）、**不再污染被测应用**。

工程要求：
- 保留 `gfxinfo` 作为回退路径（SurfaceFlinger 不可用时）；
- 把"数据源"（surfaceflinger / gfxinfo）与"失败原因"落进 record，避免两种口径混在同一张图，也让缺段可解释；
- 采样间隔同步放宽到 2 s（FPS 曲线仍然平滑）。

### 9.3 验收判据

在同一固定 60 fps 场景下：新实现读数应稳定在 **60±2**；旧实现在 Unity/UE 类游戏上应接近 0 或整段缺失。跑通这一条即可判定"修好了"。

---

## 10. 运行策略（实现细节）

### 10.1 分级采样

`slowEvery = ceil(1000 / interval)`，快慢两条命令在记录开始时构建一次：

| 监控项 | 采样率 | 理由 |
|---|---|---|
| QPT 各 rail 功率 / 能量 | 用户设定（100–1000 ms） | 唯一需要高频的物理量 |
| CPU 簇频率、GPU、DDR | 1 Hz | 变化以百毫秒计，1 Hz 足够画趋势 |
| 温度、整机功率 | 1 Hz | 热惯性大 |
| 每核占用（jiffies 差分） | 1 Hz | 10 ms 的 jiffies 分辨率下，1 s 窗口比 200 ms 更准 |

### 10.2 前台 / 后台

| 状态 | 预览采样 | 说明 |
|---|---|---|
| 前台 | 跟随采样间隔 | 首页曲线实时刷新 |
| 退到后台 | **1 Hz** | 曲线继续滚动，开销约 1 次 su/s + 8 次内建读（估算 5–15 mW） |
| 记录中 | 记录间隔 | 与可见性无关，保证数据完整 |

`MainActivity.onStart/onStop` → `setUiVisible()` → 重建预览协程。

### 10.3 落库

- 采样协程把每 tick 打包成 `PendingBatch`，攒够 `slowEvery` 个（≈1 s）后一次 `withTransaction` 提交；
- 手动停止走 `cancelAndJoin()`，`finally` 在 `NonCancellable` 中补写缓冲，**最后一秒不丢**；
- `PRAGMA synchronous=NORMAL`：WAL 下断电最多丢最后一个事务，对统计量可接受。

### 10.4 详情页

- `stride = max(1, countSamples / 3000)`，SQL 侧 `id % stride = 0` 抽样，图表仍能反映趋势；
- 聚合链 `flowOn(Dispatchers.Default)`，不再占主线程；
- **导出仍是全量**（`getXxx` 不带 stride），不影响数据完整性。

### 10.5 FPS 图层选择

设置页新增「FPS 图层」：

1. 点「扫描 SurfaceFlinger 图层」→ `dumpsys SurfaceFlinger --list`，列出全部层名；
2. 选中即持久化到 `fps_layer`，`FpsReader` 直接使用该层，不再自动发现；
3. 「自动」模式的选择顺序：`SurfaceView[...]` → 含 `#0` 的主层 → 最后一个含包名的层；
4. 选错层的表现是 FPS 恒空（该层没有 latency 缓冲）→ 连续 3 次失败后 60 s 内回退 `gfxinfo`，不会卡死记录。

### 10.6 设备拓扑扫描

`refreshDeviceState()` 加 5 分钟 TTL；下拉刷新与切换 QPT 开关时 `force = true` 强制重扫。
效果：`startRecording()` 不再在测量窗口里插入 11 次 `su` 往返。

---

## 附录 A：优先级与收益汇总

| 优先级 | 动作 | 改动量 | 记录态收益 | 后台静置收益 |
|---|---|---|---|---|
| P0-1 | 零 fork 采样命令 | ~80 行 | 80–150 mW | 同比例 |
| P0-2 | dumpsys 复用 + 降频 | ~60 行 | 50–100 mW | 同比例 |
| P0-2b | FPS 换 SurfaceFlinger 帧计数（顺带修 FPS） | ~80 行 | 含在 P0-2 内 | 同比例 |
| P0-3 | 生命周期门控 | ~30 行 | — | **~100 mW → 0** |
| P0-4 | 分级采样 + 默认 1 s | ~50 行 | 40–50%（叠加） | 同比例 |
| P1-5 | 批量落库 | ~70 行 | 10–25 mW + 抖动 | — |
| P1-6 | 扫描合并 + TTL | ~50 行 | 起始尖峰消除 | — |
| P1-7 | 详情页分桶 | ~80 行 | 消除主线程打满 | — |
| P1-8 | OSD 协程泄漏 | ~10 行 | 随开关次数 | — |

**合计预期**：记录态附加功耗 **250–500 mW → 60–110 mW（降 70–80%）**；后台静置 **→ 接近 0**；数据量 **降 80%（1 Hz）**；同时修复 4 个影响测量可信度的正确性缺陷。
