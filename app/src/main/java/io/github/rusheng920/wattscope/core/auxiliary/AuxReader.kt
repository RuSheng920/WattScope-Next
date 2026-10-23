package io.github.rusheng920.wattscope.core.auxiliary

import io.github.rusheng920.wattscope.core.root.RootManager
import io.github.rusheng920.wattscope.core.root.RootShellPool.Lane
import io.github.rusheng920.wattscope.core.root.escapeForShell
import io.github.rusheng920.wattscope.domain.ClusterFreqSample
import io.github.rusheng920.wattscope.domain.CoreUsageSample
import io.github.rusheng920.wattscope.domain.CpuCluster
import io.github.rusheng920.wattscope.domain.GpuSensor
import io.github.rusheng920.wattscope.domain.TempSample
import io.github.rusheng920.wattscope.domain.TempSensors
import kotlin.math.abs

/**
 * 附加监控项采集器：CPU 簇频率、每核占用、整机功耗、CPU/DDR/GPU 温度、GPU 频率/占用、DDR 频率。
 * 全部动态枚举 sysfs 节点（不硬编码路径，无节点则对应项自动缺失）。
 * 采样命令由 [buildAuxCommand] 构建（可与 QPT 命令合并为单次 su 往返），结果由 [parseAuxLines] 解析。
 *
 * 采样命令全部使用 shell 内建 `read` 读文件，不用 `$(cat ...)`：每条 `$(cat)` 都是一次 fork/exec，
 * 旧实现单 tick 约 30 个子进程，5 Hz 下 150 进程/秒，是记录态功耗与采样抖动的主要来源。
 * FPS 不在本类采集（gfxinfo 只统计 HWUI 帧，测不到游戏），见 core/fps/FpsReader。
 */
class AuxReader(private val rootManager: RootManager) {

    // /proc/stat 每核 (total, idle) 差值基准
    private val prevStat = mutableMapOf<Int, Pair<Long, Long>>()

    data class AuxBatch(
        val clusterFreqs: List<ClusterFreqSample> = emptyList(),
        val temps: List<TempSample> = emptyList(),
        val devicePowerUw: Long? = null,
        val coreUsages: List<CoreUsageSample> = emptyList(),
        val gpuFreqHz: Long? = null,
        val gpuUsagePercent: Float? = null,
        val ddrFreqHz: Long? = null,
        /** /proc/meminfo：OSD 的「内存」指标用，与概览页同一口径。 */
        val memTotalKb: Long? = null,
        val memAvailKb: Long? = null,
    )

    suspend fun scanClusters(): List<CpuCluster> {
        val lines = rootManager.execute(
            "for p in /sys/devices/system/cpu/cpufreq/policy*; do " +
                "[ -f \"\$p/scaling_cur_freq\" ] || continue; " +
                "c=; read -r c 2>/dev/null < \"\$p/related_cpus\"; " +
                "echo \"\$p|\$c\"; done",
        )
        return lines.mapNotNull { line ->
            val idx = line.lastIndexOf('|')
            if (idx <= 0) return@mapNotNull null
            val path = line.substring(0, idx).trim()
            val cores = line.substring(idx + 1).trim()
            if (cores.isBlank()) return@mapNotNull null
            CpuCluster(path = path, name = "CPU$cores")
        }
    }

    suspend fun scanTempZones(): TempSensors {
        val lines = rootManager.execute(
            "for z in /sys/class/thermal/thermal_zone*; do " +
                "t=; read -r t 2>/dev/null < \"\$z/type\"; " +
                "[ -n \"\$t\" ] && echo \"\$z|\$t\"; done",
        )
        val cpuZones = mutableListOf<String>()
        var ddrZone: String? = null
        var gpuZone: String? = null
        lines.forEach { line ->
            val idx = line.lastIndexOf('|')
            if (idx <= 0) return@forEach
            val path = line.substring(0, idx).trim()
            val type = line.substring(idx + 1).trim().lowercase()
            when {
                "ddr" in type || "dram" in type -> if (ddrZone == null) ddrZone = path
                "gpu" in type -> if (gpuZone == null) gpuZone = path
                // 排除硬件跳闸阈值传感器（如 cpu-hw-trip-*，恒值 105°C，非真实温度）
                "cpu" in type && "trip" !in type -> cpuZones += path
            }
        }
        // 电池温度：标准 Android 电源节点（0.1°C 单位），独立于 thermal zone
        val hasBatteryTemp = rootManager.execute(
            "[ -f /sys/class/power_supply/battery/temp ] && echo 1 || echo 0",
        ).firstOrNull()?.trim() == "1"
        return TempSensors(cpuZones = cpuZones.sorted(), ddrZone = ddrZone, gpuZone = gpuZone, hasBatteryTemp = hasBatteryTemp)
    }

    /** GPU 传感器：gpuclk 频率节点 + gpubusy(busy/total) 或 gpu_busy_percentage(百分比) 占用节点。 */
    suspend fun scanGpuSensor(): GpuSensor? {
        val root = GPU_ROOT
        val freqOk = rootManager.execute(
            "[ -f '$root/gpuclk' ] && echo 1 || echo 0",
        ).firstOrNull()?.trim() == "1"
        if (!freqOk) return null
        val busyIsPercent = rootManager.execute(
            "[ -f '$root/gpubusy' ] && echo 0 || { [ -f '$root/gpu_busy_percentage' ] && echo 1 || echo 2; }",
        ).firstOrNull()?.trim()
        val busyPath = when (busyIsPercent) {
            "0" -> "$root/gpubusy"
            "1" -> "$root/gpu_busy_percentage"
            else -> null
        }
        return GpuSensor(freqPath = "$root/gpuclk", busyPath = busyPath, busyIsPercent = busyIsPercent == "1")
    }

    /** DDR 频率：动态发现 devfreq 中名称含 ddr/memory/bw/llcc 且可读 cur_freq 的节点；无则返回 null。 */
    suspend fun scanDdrFreqPath(): String? {
        val lines = rootManager.execute(
            "for d in /sys/class/devfreq/*/; do " +
                "n=\${d%/}; n=\${n##*/}; " +
                "case \"\$n\" in *ddr*|*memory*|*memlat*|*llcc*|*bw*) [ -f \"\$d/cur_freq\" ] && echo \"\$d\";; esac; done",
        )
        return lines.firstOrNull { it.isNotBlank() }?.trim()
    }

    /** 一次扫描拿到的全部附加监控拓扑。字段与旧的分开扫描方法一一对应。 */
    data class ScanResult(
        val clusters: List<CpuCluster> = emptyList(),
        val sensors: TempSensors = TempSensors(),
        val gpu: GpuSensor? = null,
        val ddrFreqPath: String? = null,
        val batteryPowerAvailable: Boolean = false,
    )

    /**
     * 一次性扫描全部附加监控拓扑：簇、温区、GPU 传感器、DDR 节点、整机功耗可用性。
     *
     * 旧实现是 5 次独立 su 往返（scanClusters / scanTempZones / scanGpuSensor /
     * scanDdrFreqPath / isBatteryPowerAvailable），每次往返 50~300ms，启动路径与
     * 概览页首帧要串行等 0.5~2s。这里合并为一条批量命令，全程 shell 内建 read，零 fork。
     * 输出打标签解析，语义与旧方法完全一致（含 trip 温区过滤、量级判定、bms 候选目录）。
     */
    suspend fun scanAll(): ScanResult {
        val lines = rootManager.execute(scanAllCommand(), lane = Lane.QUERY)
        val clusters = mutableListOf<CpuCluster>()
        val cpuZones = mutableListOf<String>()
        var ddrZone: String? = null
        var gpuZone: String? = null
        var hasBatteryTemp = false
        var ddrFreqPath: String? = null
        var batteryPower = false
        var gpu: GpuSensor? = null
        lines.forEach { line ->
            when {
                line.startsWith("CLU|") -> {
                    val p = line.split('|')
                    val path = p.getOrNull(1)?.trim().orEmpty()
                    val cores = p.getOrNull(2)?.trim().orEmpty()
                    if (path.isNotEmpty() && cores.isNotEmpty()) clusters += CpuCluster(path = path, name = "CPU$cores")
                }

                line.startsWith("TZ|") -> {
                    val p = line.split('|')
                    val path = p.getOrNull(1)?.trim().orEmpty()
                    val type = p.getOrNull(2)?.trim()?.lowercase()
                    if (path.isEmpty() || type.isNullOrEmpty()) return@forEach
                    when {
                        "ddr" in type || "dram" in type -> if (ddrZone == null) ddrZone = path
                        "gpu" in type -> if (gpuZone == null) gpuZone = path
                        // 排除硬件跳闸阈值传感器（如 cpu-hw-trip-*，恒值 105°C，非真实温度）
                        "cpu" in type && "trip" !in type -> cpuZones += path
                    }
                }

                line.startsWith("GPUS|") -> {
                    val p = line.split('|')
                    if (p.getOrNull(1)?.trim() == "1") {
                        val busyPath = when (p.getOrNull(2)?.trim()) {
                            "0" -> "$GPU_ROOT/gpubusy"
                            "1" -> "$GPU_ROOT/gpu_busy_percentage"
                            else -> null
                        }
                        gpu = GpuSensor(
                            freqPath = "$GPU_ROOT/gpuclk",
                            busyPath = busyPath,
                            busyIsPercent = p.getOrNull(2)?.trim() == "1",
                        )
                    }
                }

                line.startsWith("DDR|") -> {
                    val path = line.substringAfter('|').trim()
                    if (path.isNotEmpty()) ddrFreqPath = path
                }

                line.startsWith("BPW|") -> batteryPower = line.substringAfter('|').trim() == "1"
                line.startsWith("BTMP|") -> hasBatteryTemp = line.substringAfter('|').trim() == "1"
            }
        }
        return ScanResult(
            clusters = clusters,
            sensors = TempSensors(
                cpuZones = cpuZones.sorted(),
                ddrZone = ddrZone,
                gpuZone = gpuZone,
                hasBatteryTemp = hasBatteryTemp,
            ),
            gpu = gpu,
            ddrFreqPath = ddrFreqPath,
            batteryPowerAvailable = batteryPower,
        )
    }

    /**
     * [scanAll] 的批量命令。输出标签：
     * CLU|path|related_cpus / TZ|path|type / GPUS|freqOk|busyKind(0=gpubusy 1=percent 2=none) /
     * DDR|path / BPW|0|1 / BTMP|0|1。
     */
    private fun scanAllCommand(): String = buildString {
        append("for p in /sys/devices/system/cpu/cpufreq/policy*; do ")
        append("[ -f \"\$p/scaling_cur_freq\" ] || continue; ")
        append("c=; read -r c 2>/dev/null < \"\$p/related_cpus\"; ")
        append("printf 'CLU|%s|%s\\n' \"\$p\" \"\$c\"; done; ")

        append("for z in /sys/class/thermal/thermal_zone*; do ")
        append("t=; read -r t 2>/dev/null < \"\$z/type\"; ")
        append("[ -n \"\$t\" ] && printf 'TZ|%s|%s\\n' \"\$z\" \"\$t\"; done; ")

        append("g=$GPU_ROOT; gf=0; bk=2; ")
        append("if [ -f \"\$g/gpuclk\" ]; then gf=1; ")
        append("if [ -f \"\$g/gpubusy\" ]; then bk=0; elif [ -f \"\$g/gpu_busy_percentage\" ]; then bk=1; fi; fi; ")
        append("printf 'GPUS|%s|%s\\n' \"\$gf\" \"\$bk\"; ")

        append("for d in /sys/class/devfreq/*/; do ")
        append("n=\${d%/}; n=\${n##*/}; ")
        append("case \"\$n\" in *ddr*|*memory*|*memlat*|*llcc*|*bw*) [ -f \"\$d/cur_freq\" ] && printf 'DDR|%s\\n' \"\$d\";; esac; done; ")

        // 整机功耗可用性：与 isBatteryPowerAvailable 同一判定（current_now+voltage_now 优先，power_now 非 0 兜底）
        append("b=; c=; w=; for n in battery Battery bms hisi_bk_battery; do ")
        append("d=/sys/class/power_supply/\$n; ")
        append("read -r c 2>/dev/null < \$d/current_now; ")
        append("read -r w 2>/dev/null < \$d/voltage_now; ")
        append("if [ -n \"\$c\" ] && [ -n \"\$w\" ]; then b=1; break; fi; c=; w=; done; ")
        append("if [ -n \"\$b\" ]; then printf 'BPW|1\\n'; else ")
        append("p=; read -r p 2>/dev/null < /sys/class/power_supply/battery/power_now; ")
        append("if [ -n \"\$p\" ] && [ \"\$p\" != \"0\" ]; then printf 'BPW|1\\n'; else printf 'BPW|0\\n'; fi; fi; ")

        append("bt=0; [ -f /sys/class/power_supply/battery/temp ] && bt=1; printf 'BTMP|%s\\n' \"\$bt\"")
    }

    /**
     * 整机功耗可用性：power_now 或 current_now+voltage_now **有非空内容**才算可用。
     * 注意不能用 `[ -f ]` / `[ -s ]` 判断：sysfs 属性的 st_size 恒为 4096，空文件也会通过，
     * 但读出来是空串（K90 Pro Max 的 power_now 就是这种状态）。
     *
     * 判定顺序与 [batteryCommand] 一致：先找有 `current_now` + `voltage_now` 的电池目录，
     * 都没有时才看 `power_now` 且必须非 0（0 代表「该节点未更新」，不是真实功耗）。
     */
    suspend fun isBatteryPowerAvailable(): Boolean = rootManager.execute(
        "b=; c=; w=; " +
            "for n in battery Battery bms hisi_bk_battery; do " +
            "d=/sys/class/power_supply/\$n; " +
            "read -r c 2>/dev/null < \$d/current_now; " +
            "read -r w 2>/dev/null < \$d/voltage_now; " +
            "if [ -n \"\$c\" ] && [ -n \"\$w\" ]; then b=1; break; fi; c=; w=; done; " +
            "if [ -n \"\$b\" ]; then echo 1; else " +
            "p=; read -r p 2>/dev/null < /sys/class/power_supply/battery/power_now; " +
            "if [ -n \"\$p\" ] && [ \"\$p\" != \"0\" ]; then echo 1; else echo 0; fi; fi",
    ).firstOrNull()?.trim() == "1"

    /**
     * 构建全部附加监控项的批量采样命令（打标签输出，可与 QPT 命令合并为一次 su 往返）。
     * 输出行：FREQ|簇名|kHz / TEMP|类别|毫摄氏度 / BAT|µW / BATC|µA|µV / GPUF|Hz / GPUB|busy|total /
     *         GPUBP|% / DDR|Hz / STAT|cpuN|user|nice|system|idle|iowait|irq|softirq|steal
     * @param includeStat 是否采样 /proc/stat（每核占用）。慢变量可与高频 tick 解耦。
     */
    fun buildAuxCommand(
        clusters: List<CpuCluster>,
        sensors: TempSensors,
        gpu: GpuSensor?,
        ddrFreqPath: String?,
        includeStat: Boolean = true,
    ): String {
        val commands = mutableListOf<String>()
        clusters.forEach {
            commands += readThen("'${it.path.escapeForShell()}/scaling_cur_freq'", "FREQ|${it.name}|")
        }
        sensors.cpuZones.forEach {
            commands += readThen("'${it.escapeForShell()}/temp'", "TEMP|cpu|")
        }
        sensors.ddrZone?.let {
            commands += readThen("'${it.escapeForShell()}/temp'", "TEMP|ddr|")
        }
        sensors.gpuZone?.let {
            commands += readThen("'${it.escapeForShell()}/temp'", "TEMP|gpu|")
        }
        if (sensors.hasBatteryTemp) {
            commands += readThen("/sys/class/power_supply/battery/temp", "TEMP|batt|")
        }
        commands += batteryCommand()
        gpu?.let {
            commands += readThen("'${it.freqPath.escapeForShell()}'", "GPUF|")
            it.busyPath?.let { busy ->
                commands += readThen("'${busy.escapeForShell()}'", if (it.busyIsPercent) "GPUBP|" else "GPUB|")
            }
        }
        ddrFreqPath?.let {
            commands += readThen("'${it.escapeForShell()}/cur_freq'", "DDR|")
        }
        // 内存只取两行：/proc/meminfo 是伪文件，整读一次约 2KB、零 fork，
        // 相对 5Hz 的 sysfs 批量读可以忽略；单独开一条低频通道反而多一次 shell 往返。
        commands += "mt=; ma=; st=; sf=; while read -r k v rest; do " +
            "case \"\$k\" in MemTotal:) mt=\$v;; MemAvailable:) ma=\$v;; " +
            "SwapTotal:) st=\$v;; SwapFree:) sf=\$v;; esac; " +
            "done < /proc/meminfo; printf 'MEM|%s|%s|%s|%s\\n' \"\$mt\" \"\$ma\" \"\$st\" \"\$sf\""
        if (includeStat) commands += statCommand()
        return commands.joinToString("; ")
    }

    /** 生成「读取一个 sysfs 节点并打标签输出」的片段，全程走 shell 内建 read。 */
    private fun readThen(pathExpr: String, tag: String): String =
        "v=; read -r v 2>/dev/null < $pathExpr; printf '$tag%s\\n' \"\$v\""

    /**
     * 整机功耗：按 `current_now × voltage_now` 计算，`power_now` 只作最后兜底。
     *
     * 参考 OpenMonitor（1orz/OpenMonitor）的 daemon 实现：它完全不用 `power_now`，
     * 而是依次探测 `/sys/class/power_supply/{battery,Battery,hisi_bk_battery}` 下的
     * `current_now` 与 `voltage_now`。原因是 `power_now` 在不少 BMS 驱动上只在充电时更新，
     * 放电时读到的是字面上的 `0`——旧实现把它当有效值，界面就恒定 0.00 W。
     *
     * 这里在它的候选目录基础上补了高通平台的 `bms`（fuel gauge 常单独挂在这里）。
     * 全程 shell 内建 read，不产生子进程。
     */
    private fun batteryCommand(): String =
        "b=; c=; w=; " +
            "for n in battery Battery bms hisi_bk_battery; do " +
            "d=/sys/class/power_supply/\$n; " +
            "read -r c 2>/dev/null < \$d/current_now; " +
            "read -r w 2>/dev/null < \$d/voltage_now; " +
            "if [ -n \"\$c\" ] && [ -n \"\$w\" ]; then b=1; break; fi; c=; w=; done; " +
            "if [ -n \"\$b\" ]; then printf 'BATC|%s|%s\\n' \"\$c\" \"\$w\"; else " +
            "p=; read -r p 2>/dev/null < /sys/class/power_supply/battery/power_now; " +
            "printf 'BAT|%s\\n' \"\$p\"; fi"

    /**
     * µA × µV → µW。
     *
     * 内核 power_supply ABI 规定 `current_now` 是 µA、`voltage_now` 是 µV，
     * 但个别厂商导出的是 mA / mV。这里按量级挑一个物理上合理的组合：
     * 手机整机功耗不会低于 0.05 W（屏幕+基带就不止），也不会超过 30 W，
     * 落进区间即采用；四种组合都不在区间时退回 ABI 标准换算。
     */
    private fun devicePowerUw(current: Long?, voltage: Long?): Long? {
        if (current == null || voltage == null || current == 0L || voltage <= 0L) return null
        val c = abs(current).toDouble()
        val v = voltage.toDouble()
        val candidates = listOf(
            c * v / 1e6,                       // µA × µV（ABI 标准）
            c * 1_000.0 * v / 1e6,             // mA × µV
            c * v * 1_000.0 / 1e6,             // µA × mV
            c * 1_000.0 * v * 1_000.0 / 1e6,   // mA × mV
        )
        val chosen = candidates.firstOrNull { it in 50_000.0..30_000_000.0 } ?: candidates.first()
        return chosen.toLong().takeIf { it > 0 }
    }

    /**
     * /proc/stat 每核 jiffies。旧实现是 `grep | sed` 两个进程 + 管道，
     * 现在用 while read 逐行解析（内建，0 fork）。
     * 只输出 user/nice/system/idle/iowait/irq/softirq/steal 八项，**丢弃 guest/guest_nice**——
     * 它们已计入 user/nice，重复累加会虚高 CPU 占用率。
     */
    private fun statCommand(): String =
        "while read -r c v1 v2 v3 v4 v5 v6 v7 v8 v9 v10; do " +
            "case \"\$c\" in cpu[0-9]*) printf 'STAT|%s|%s|%s|%s|%s|%s|%s|%s|%s\\n' " +
            "\"\${c#cpu}\" \"\$v1\" \"\$v2\" \"\$v3\" \"\$v4\" \"\$v5\" \"\$v6\" \"\$v7\" \"\$v8\";; esac; " +
            "done < /proc/stat"

    /**
     * @param dualCellMultiplier 双电芯机型补偿：部分 BMS 只上报单路电流，
     *                           开启后把最终整机功耗乘以该系数（通常为 2）。
     */
    fun parseAuxLines(lines: List<String>, timestampMs: Long, dualCellMultiplier: Int = 1): AuxBatch {
        val freqs = mutableListOf<ClusterFreqSample>()
        val cpuTemps = mutableListOf<Long>()
        var ddrTemp: Long? = null
        var gpuTemp: Long? = null
        var battTemp: Long? = null
        var devicePower: Long? = null
        var gpuFreqHz: Long? = null
        var gpuUsagePercent: Float? = null
        var ddrFreqHz: Long? = null
        var memTotal: Long? = null
        var memAvail: Long? = null
        val statLines = mutableListOf<String>()
        lines.forEach { line ->
            when {
                line.startsWith("FREQ|") -> {
                    val parts = line.split('|')
                    if (parts.size >= 3) {
                        parts[2].trim().toLongOrNull()?.let { freqs += ClusterFreqSample(timestampMs, parts[1], it) }
                    }
                }
                line.startsWith("TEMP|") -> {
                    val parts = line.split('|')
                    if (parts.size >= 3) {
                        val temp = parts[2].trim().toLongOrNull()
                        if (temp != null) {
                            when (parts[1]) {
                                "cpu" -> cpuTemps += temp
                                "ddr" -> ddrTemp = temp
                                "gpu" -> gpuTemp = temp
                                "batt" -> battTemp = temp
                            }
                        }
                    }
                }
                line.startsWith("BAT|") -> {
                    // 0 与负值都视为无效读数（充电时 power_now 可能为负，取绝对值表示负载强度）
                    devicePower = line.substringAfter('|').trim().toLongOrNull()
                        ?.let { abs(it) }
                        ?.takeIf { it > 0 }
                }
                // current_now × voltage_now：µA × µV = pW，÷1e6 = µW；充电时电流为负，取绝对值表示负载强度
                line.startsWith("BATC|") -> {
                    val parts = line.substringAfter('|').trim().split('|')
                    val current = parts.getOrNull(0)?.trim()?.toLongOrNull()
                    val voltage = parts.getOrNull(1)?.trim()?.toLongOrNull()
                    devicePowerUw(current, voltage)?.let { devicePower = it }
                }
                line.startsWith("GPUF|") -> {
                    gpuFreqHz = line.substringAfter('|').trim().toLongOrNull()
                }
                line.startsWith("GPUB|") -> {
                    val parts = line.substringAfter('|').trim().split(' ')
                    val busy = parts.getOrNull(0)?.trim()?.toLongOrNull()
                    val total = parts.getOrNull(1)?.trim()?.toLongOrNull()
                    if (busy != null && total != null && total > 0) {
                        gpuUsagePercent = (busy.toFloat() / total * 100f).coerceIn(0f, 100f)
                    }
                }
                line.startsWith("GPUBP|") -> {
                    val value = line.substringAfter('|').trim().removeSuffix("%").trim().toFloatOrNull()
                    if (value != null) gpuUsagePercent = value.coerceIn(0f, 100f)
                }
                line.startsWith("DDR|") -> {
                    ddrFreqHz = line.substringAfter('|').trim().toLongOrNull()
                }
                line.startsWith("STAT|") -> statLines += line
                line.startsWith("MEM|") -> {
                    val parts = line.split('|')
                    memTotal = parts.getOrNull(1)?.trim()?.toLongOrNull()
                    memAvail = parts.getOrNull(2)?.trim()?.toLongOrNull()
                }
            }
        }
        val temps = mutableListOf<TempSample>()
        // CPU 温度：排除异常恒值（如跳闸阈值 >=110°C），避免取到非真实传感器
        cpuTemps.filter { it < 110_000 }.maxOrNull()?.let { temps += TempSample(timestampMs, "cpu", it / 1000f) }
        ddrTemp?.let { temps += TempSample(timestampMs, "ddr", it / 1000f) }
        gpuTemp?.let { temps += TempSample(timestampMs, "gpu", it / 1000f) }
        // 电池温度：power_supply 节点单位为 0.1°C，过滤异常值（-10 ~ 100°C）
        battTemp?.takeIf { it in -100..1000 }?.let { temps += TempSample(timestampMs, "batt", it / 10f) }
        return AuxBatch(
            clusterFreqs = freqs,
            temps = temps,
            devicePowerUw = devicePower?.let { it * dualCellMultiplier.coerceAtLeast(1) },
            coreUsages = parseCoreUsages(statLines, timestampMs),
            gpuFreqHz = gpuFreqHz,
            gpuUsagePercent = gpuUsagePercent,
            ddrFreqHz = ddrFreqHz,
            memTotalKb = memTotal,
            memAvailKb = memAvail,
        )
    }

    private fun parseCoreUsages(statLines: List<String>, timestampMs: Long): List<CoreUsageSample> {
        val now = mutableMapOf<Int, Pair<Long, Long>>()
        statLines.forEach { line ->
            val parts = line.split('|')
            if (parts.size < 6) return@forEach
            val core = parts[1].removePrefix("cpu").toIntOrNull() ?: return@forEach
            val values = parts.drop(2).mapNotNull { it.trim().toLongOrNull() }
            if (values.size < 4) return@forEach
            val total = values.sum()
            val idle = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L } // idle + iowait
            now[core] = total to idle
        }
        val result = prevStat.mapNotNull { (core, prev) ->
            val cur = now[core] ?: return@mapNotNull null
            val deltaTotal = cur.first - prev.first
            val deltaIdle = cur.second - prev.second
            val usage = if (deltaTotal > 0) {
                ((deltaTotal - deltaIdle).toFloat() / deltaTotal * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }
            CoreUsageSample(timestampMs, core, usage)
        }
        prevStat.clear()
        prevStat.putAll(now)
        return result.sortedBy { it.coreIndex }
    }

    fun resetDeltas() {
        prevStat.clear()
    }

    private companion object {
        /** 高通 Adreno KGSL 节点根目录（SM8850 实测路径）。 */
        const val GPU_ROOT = "/sys/class/kgsl/kgsl-3d0"
    }
}
