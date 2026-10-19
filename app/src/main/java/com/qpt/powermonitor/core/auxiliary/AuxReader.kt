package com.qpt.powermonitor.core.auxiliary

import com.qpt.powermonitor.core.root.RootManager
import com.qpt.powermonitor.core.root.escapeForShell
import com.qpt.powermonitor.domain.ClusterFreqSample
import com.qpt.powermonitor.domain.CoreUsageSample
import com.qpt.powermonitor.domain.CpuCluster
import com.qpt.powermonitor.domain.GpuSensor
import com.qpt.powermonitor.domain.TempSample
import com.qpt.powermonitor.domain.TempSensors
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
    )

    suspend fun scanClusters(): List<CpuCluster> {
        val lines = rootManager.execute(
            "for p in /sys/devices/system/cpu/cpufreq/policy*; do " +
                "[ -f \"\$p/scaling_cur_freq\" ] || continue; " +
                "c=\$(cat \"\$p/related_cpus\" 2>/dev/null); " +
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
                "t=\$(cat \"\$z/type\" 2>/dev/null); " +
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
        val root = "/sys/class/kgsl/kgsl-3d0"
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
                "n=\$(basename \"\$d\"); " +
                "case \"\$n\" in *ddr*|*memory*|*memlat*|*llcc*|*bw*) [ -f \"\$d/cur_freq\" ] && echo \"\$d\";; esac; done",
        )
        return lines.firstOrNull { it.isNotBlank() }?.trim()
    }

    /**
     * 整机功耗可用性：power_now 或 current_now+voltage_now **有非空内容**才算可用。
     * 注意不能用 `[ -f ]` / `[ -s ]` 判断：sysfs 属性的 st_size 恒为 4096，空文件也会通过，
     * 但读出来是空串（K90 Pro Max 的 power_now 就是这种状态）。
     */
    suspend fun isBatteryPowerAvailable(): Boolean = rootManager.execute(
        "v=; read -r v 2>/dev/null < /sys/class/power_supply/battery/power_now; " +
            "if [ -n \"\$v\" ]; then echo 1; else " +
            "c=; w=; read -r c 2>/dev/null < /sys/class/power_supply/battery/current_now; " +
            "read -r w 2>/dev/null < /sys/class/power_supply/battery/voltage_now; " +
            "if [ -n \"\$c\" ] && [ -n \"\$w\" ]; then echo 1; else echo 0; fi; fi",
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
        if (includeStat) commands += statCommand()
        return commands.joinToString("; ")
    }

    /** 生成「读取一个 sysfs 节点并打标签输出」的片段，全程走 shell 内建 read。 */
    private fun readThen(pathExpr: String, tag: String): String =
        "v=; read -r v 2>/dev/null < $pathExpr; printf '$tag%s\\n' \"\$v\""

    /**
     * 整机功耗：优先 power_now；为空则输出原始 current/voltage，由 Kotlin 用 Long 计算
     * （mksh 算术是 32 位，µA×µV 会溢出，旧实现靠 awk 兜底，现在一并去掉这个进程）。
     */
    private fun batteryCommand(): String =
        "v=; read -r v 2>/dev/null < /sys/class/power_supply/battery/power_now; " +
            "if [ -n \"\$v\" ]; then printf 'BAT|%s\\n' \"\$v\"; else " +
            "c=; w=; read -r c 2>/dev/null < /sys/class/power_supply/battery/current_now; " +
            "read -r w 2>/dev/null < /sys/class/power_supply/battery/voltage_now; " +
            "printf 'BATC|%s|%s\\n' \"\$c\" \"\$w\"; fi"

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

    fun parseAuxLines(lines: List<String>, timestampMs: Long): AuxBatch {
        val freqs = mutableListOf<ClusterFreqSample>()
        val cpuTemps = mutableListOf<Long>()
        var ddrTemp: Long? = null
        var gpuTemp: Long? = null
        var battTemp: Long? = null
        var devicePower: Long? = null
        var gpuFreqHz: Long? = null
        var gpuUsagePercent: Float? = null
        var ddrFreqHz: Long? = null
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
                    devicePower = line.substringAfter('|').trim().toLongOrNull()?.takeIf { it >= 0 }
                }
                // current_now × voltage_now：µA × µV = pW，÷1e6 = µW；充电时电流为负，取绝对值表示负载强度
                line.startsWith("BATC|") -> {
                    val parts = line.substringAfter('|').trim().split('|')
                    val current = parts.getOrNull(0)?.trim()?.toLongOrNull()
                    val voltage = parts.getOrNull(1)?.trim()?.toLongOrNull()
                    if (current != null && voltage != null && voltage > 0) {
                        devicePower = abs(current) * voltage / 1_000_000L
                    }
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
            devicePowerUw = devicePower,
            coreUsages = parseCoreUsages(statLines, timestampMs),
            gpuFreqHz = gpuFreqHz,
            gpuUsagePercent = gpuUsagePercent,
            ddrFreqHz = ddrFreqHz,
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
}
