package io.github.rusheng920.wattscope.core.sysinfo

import android.os.Build
import io.github.rusheng920.wattscope.core.auxiliary.AuxReader
import io.github.rusheng920.wattscope.core.root.RootManager
import io.github.rusheng920.wattscope.core.root.RootShellPool.Lane
import io.github.rusheng920.wattscope.core.root.escapeForShell
import io.github.rusheng920.wattscope.domain.CpuCluster
import io.github.rusheng920.wattscope.domain.GpuSensor
import io.github.rusheng920.wattscope.domain.TempSensors

/** 一个 cpufreq policy（通常等于一个 CPU 簇）的静态属性。 */
data class ClusterDetail(
    val cores: String,
    val curKhz: Long,
    val minKhz: Long,
    val maxKhz: Long,
    val governor: String,
) {
    /** `0-3` / `0-1,4-5` 这类 related_cpus 描述的核数。 */
    val coreCount: Int get() = countCores(cores)

    /**
     * 展示用的紧凑写法的核列表：`0 1 2` → `0-2`。
     *
     * 高通内核的 `related_cpus` 是空格分隔（`0 1 2`），别的平台是连字符（`0-2`），
     * 两种都要能吃。注意 [cores] 原值仍是采样键（AuxReader 用 `CPU$cores` 命名簇），
     * 所以只改展示，不改数据。
     */
    val compactCores: String get() = compactCoreSpec(cores)
}

/**
 * CPU 缓存层级（来自 cpu0 的 cache/index*）。
 *
 * [size] 可能为空：不少 ARM64 内核（本机实测 SM8850 就是）只导出 level/type/shared_cpu_list，
 * 没有 `size` 属性。这时仍然展示层级与共享范围 —— 「哪些核共用这一级」本身就是有效信息，
 * 比整块显示「本设备未导出」有用。
 */
data class CacheLevel(
    val level: Int,
    val type: String,
    val size: String,
    val sharedCpus: String = "",
) {
    val label: String
        get() = when {
            level <= 0 -> type
            type.equals("Data", true) -> "L$level D"
            type.equals("Instruction", true) -> "L$level I"
            else -> "L$level ${type.take(1)}"
        }
}

/** 静态硬件档案：进入概览页扫一次，之后复用（设备能力不会中途变化）。 */
data class HardwareStatic(
    val deviceModel: String,
    val manufacturer: String,
    val socModel: String,
    val socVendor: String,
    val hardware: String,
    val board: String,
    val abi: String,
    val is64Bit: Boolean,
    val possibleCores: Int,
    val onlineCores: Int,
    val clusters: List<ClusterDetail>,
    val caches: List<CacheLevel>,
    val gpuModel: String?,
    val gpuMaxHz: Long?,
    val gpuGovernor: String?,
    val memTotalKb: Long,
    val swapTotalKb: Long,
    val zramOrigKb: Long,
) {
    /** 集群规格摘要，如 `4×2.0GHz · 3×2.8GHz · 2×3.6GHz`。 */
    val clusterSummary: String
        get() = clusters.joinToString(" · ") { "${it.coreCount}×${formatFreq(it.maxKhz)}" }

    val neonSupported: Boolean get() = is64Bit
}

/** 内存快照（/proc/meminfo + zram0）。 */
data class MemoryStatus(
    val totalKb: Long = 0,
    val availKb: Long = 0,
    val swapTotalKb: Long = 0,
    val swapFreeKb: Long = 0,
    val zramOrigKb: Long = 0,
    val zramUsedKb: Long = 0,
) {
    val usedKb: Long get() = (totalKb - availKb).coerceAtLeast(0)
    val usedPercent: Float get() = if (totalKb > 0) usedKb.toFloat() / totalKb * 100f else 0f
    val swapUsedKb: Long get() = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
    /** 压缩率：原始数据 / 实际占用，>1 表示省了内存。 */
    val zramRatio: Float get() = if (zramUsedKb > 0) zramOrigKb.toFloat() / zramUsedKb else 0f
}

data class BatteryStatus(
    val level: Int? = null,
    val voltageUv: Long? = null,
    val tempC: Float? = null,
    val status: String? = null,
)

/** 每秒刷新的实时状态。字段全部可空/带默认值，缺节点时对应项自动不显示。 */
data class LiveStatus(
    val coreUsages: List<Float> = emptyList(),
    val cpuUsagePercent: Float = 0f,
    val clusterCurKhz: Map<String, Long> = emptyMap(),
    val avgClusterKhz: Long? = null,
    val cpuTempC: Float? = null,
    val gpuTempC: Float? = null,
    val ddrTempC: Float? = null,
    val gpuFreqHz: Long? = null,
    val gpuUsagePercent: Float? = null,
    val devicePowerUw: Long? = null,
    val mem: MemoryStatus = MemoryStatus(),
    val battery: BatteryStatus = BatteryStatus(),
    val uptimeSec: Long = 0,
)

/**
 * 概览页数据源（对应 OpenMonitor 的 Overview Dashboard）。
 *
 * 与 [AuxReader] 的分工：记录态的高频采样仍由仓库里的那个实例负责，这里**另建一个**
 * [AuxReader] 实例，避免两个消费者共用 `prevStat` 差分基准导致每核占用率来回跳变。
 *
 * 静态属性走 [Lane.QUERY] 一次批量读取；实时值 1Hz 轮询，只在概览页可见时运行。
 */
class SysInfoReader(private val rootManager: RootManager) {

    private val aux = AuxReader(rootManager)
    private var clusters: List<CpuCluster>? = null
    private var sensors: TempSensors? = null
    private var gpu: GpuSensor? = null
    private var ddrPath: String? = null
    private var scanned = false

    @Volatile
    private var cached: HardwareStatic? = null

    private suspend fun ensureScanned() {
        if (scanned) return
        // 一次 su 往返拿完簇/温区/GPU/DDR 拓扑（旧实现 4 次串行扫描，概览页首帧要等 0.3~1s）
        val scan = runCatching { aux.scanAll() }.getOrDefault(AuxReader.ScanResult())
        clusters = scan.clusters
        sensors = scan.sensors
        gpu = scan.gpu
        ddrPath = scan.ddrFreqPath
        scanned = true
    }

    /** 静态硬件档案：首次读盘，之后直接返回缓存。 */
    suspend fun readStatic(force: Boolean = false): HardwareStatic {
        cached?.let { if (!force) return it }
        ensureScanned()
        val out = rootManager.execute(staticCommand(), Lane.QUERY)
        val cluList = mutableListOf<ClusterDetail>()
        val caches = mutableListOf<CacheLevel>()
        var gpuModel: String? = null
        var gpuMax: Long? = null
        var gpuGov: String? = null
        var memTotal = 0L
        var swapTotal = 0L
        var zramOrig = 0L
        var possible = 0
        var online = 0
        out.forEach { line ->
            when {
                line.startsWith("CLU|") -> {
                    val p = line.split('|')
                    if (p.size >= 6) {
                        cluList += ClusterDetail(
                            cores = p[1].trim(),
                            curKhz = p[2].trim().toLongOrNull() ?: 0L,
                            minKhz = p[3].trim().toLongOrNull() ?: 0L,
                            maxKhz = p[4].trim().toLongOrNull() ?: 0L,
                            governor = p[5].trim(),
                        )
                    }
                }

                line.startsWith("CACHE|") -> {
                    val p = line.split('|')
                    if (p.size >= 4) {
                        val level = p[1].trim().toIntOrNull() ?: 0
                        if (level > 0) {
                            caches += CacheLevel(
                                level = level,
                                type = p[2].trim(),
                                size = p[3].trim(),
                                sharedCpus = p.getOrNull(4)?.trim().orEmpty(),
                            )
                        }
                    }
                }

                line.startsWith("GPU|") -> gpuModel = line.substringAfter('|').trim().takeIf { it.isNotBlank() }
                line.startsWith("GPUMX|") -> gpuMax = line.substringAfter('|').trim().toLongOrNull()?.takeIf { it > 0 }
                line.startsWith("GPUGOV|") ->
                    gpuGov = line.substringAfter('|').trim().takeIf { it.isNotBlank() }

                line.startsWith("MEM|") -> {
                    val p = line.split('|')
                    memTotal = p.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
                    swapTotal = p.getOrNull(3)?.trim()?.toLongOrNull() ?: 0L
                }

                line.startsWith("ZRAM|") -> zramOrig = line.split('|').getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
                line.startsWith("POSS|") -> possible = countCores(line.substringAfter('|').trim())
                line.startsWith("ONL|") -> online = countCores(line.substringAfter('|').trim())
            }
        }
        val info = HardwareStatic(
            deviceModel = Build.MODEL.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            socModel = Build.SOC_MODEL.orEmpty().ifBlank { Build.HARDWARE.orEmpty() },
            socVendor = Build.SOC_MANUFACTURER.orEmpty().ifBlank { Build.MANUFACTURER.orEmpty() },
            hardware = Build.HARDWARE.orEmpty(),
            board = Build.BOARD.orEmpty(),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            possibleCores = if (possible > 0) possible else Runtime.getRuntime().availableProcessors(),
            onlineCores = if (online > 0) online else Runtime.getRuntime().availableProcessors(),
            clusters = cluList.sortedBy { it.cores.firstOrNull()?.digitToIntOrNull() ?: 9 },
            caches = caches.distinctBy { it.level to it.type },
            gpuModel = gpuModel,
            gpuMaxHz = gpuMax,
            gpuGovernor = gpuGov,
            memTotalKb = memTotal,
            swapTotalKb = swapTotal,
            zramOrigKb = zramOrig,
        )
        cached = info
        return info
    }

    /** 1Hz 实时快照：CPU 负载/频率/温度、GPU、整机功耗、内存、电池、运行时长。 */
    suspend fun readLive(): LiveStatus {
        ensureScanned()
        val clusterList = clusters.orEmpty()
        val auxCmd = aux.buildAuxCommand(
            clusters = clusterList,
            sensors = sensors ?: TempSensors(),
            gpu = gpu,
            ddrFreqPath = ddrPath,
            includeStat = true,
        )
        val lines = rootManager.execute("$auxCmd; ${liveExtraCommand()}", Lane.QUERY)
        val batch = aux.parseAuxLines(lines, System.currentTimeMillis())
        val mem = parseMem(lines)
        var battery = BatteryStatus()
        var uptime = 0L
        lines.forEach { line ->
            when {
                line.startsWith("BATT|") -> {
                    val p = line.split('|')
                    val cap = p.getOrNull(1)?.trim()?.toIntOrNull()
                    val vol = p.getOrNull(2)?.trim()?.toLongOrNull()
                    val tmp = p.getOrNull(3)?.trim()?.toLongOrNull()
                    val sta = p.getOrNull(4)?.trim()?.takeIf { it.isNotBlank() }
                    battery = BatteryStatus(
                        level = cap?.takeIf { it in 0..100 },
                        voltageUv = vol?.takeIf { it > 0 },
                        // power_supply 温度单位是 0.1°C
                        tempC = tmp?.takeIf { it in -100..1000 }?.let { it / 10f },
                        status = sta,
                    )
                }

                line.startsWith("UPT|") ->
                    uptime = line.substringAfter('|').trim().toDoubleOrNull()?.toLong() ?: 0L
            }
        }
        val usages = batch.coreUsages.map { it.usagePercent }
        return LiveStatus(
            coreUsages = usages,
            cpuUsagePercent = if (usages.isEmpty()) 0f else usages.average().toFloat(),
            clusterCurKhz = batch.clusterFreqs.associate { it.clusterName to it.freqKhz },
            avgClusterKhz = batch.clusterFreqs.map { it.freqKhz }.average().takeIf { batch.clusterFreqs.isNotEmpty() }?.toLong(),
            cpuTempC = batch.temps.firstOrNull { it.sensor == "cpu" }?.tempC,
            gpuTempC = batch.temps.firstOrNull { it.sensor == "gpu" }?.tempC,
            ddrTempC = batch.temps.firstOrNull { it.sensor == "ddr" }?.tempC,
            gpuFreqHz = batch.gpuFreqHz,
            gpuUsagePercent = batch.gpuUsagePercent,
            devicePowerUw = batch.devicePowerUw,
            mem = mem,
            battery = battery,
            uptimeSec = uptime,
        )
    }

    private fun parseMem(lines: List<String>): MemoryStatus {
        var total = 0L
        var avail = 0L
        var swapTotal = 0L
        var swapFree = 0L
        var zOrig = 0L
        var zUsed = 0L
        lines.forEach { line ->
            when {
                line.startsWith("MEM|") -> {
                    val p = line.split('|')
                    total = p.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
                    avail = p.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L
                    swapTotal = p.getOrNull(3)?.trim()?.toLongOrNull() ?: 0L
                    swapFree = p.getOrNull(4)?.trim()?.toLongOrNull() ?: 0L
                }

                line.startsWith("ZRAM|") -> {
                    val p = line.split('|')
                    zOrig = p.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
                    zUsed = p.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L
                }
            }
        }
        return MemoryStatus(total, avail, swapTotal, swapFree, zOrig, zUsed)
    }

    private fun staticCommand(): String = buildString {
        append("for p in /sys/devices/system/cpu/cpufreq/policy*; do ")
        append("[ -d \"\$p\" ] || continue; ")
        append("cur=; mn=; mx=; gov=; rel=; ")
        append("read -r cur 2>/dev/null < \$p/scaling_cur_freq; ")
        append("read -r mn 2>/dev/null < \$p/cpuinfo_min_freq; ")
        append("read -r mx 2>/dev/null < \$p/cpuinfo_max_freq; ")
        append("read -r gov 2>/dev/null < \$p/scaling_governor; ")
        append("read -r rel 2>/dev/null < \$p/related_cpus; ")
        append("printf 'CLU|%s|%s|%s|%s|%s\\n' \"\$rel\" \"\$cur\" \"\$mn\" \"\$mx\" \"\$gov\"; done; ")

        append("for c in /sys/devices/system/cpu/cpu0/cache/index*; do ")
        append("[ -d \"\$c\" ] || continue; l=; t=; s=; sh=; ")
        append("read -r l 2>/dev/null < \$c/level; ")
        append("read -r t 2>/dev/null < \$c/type; ")
        append("read -r s 2>/dev/null < \$c/size; ")
        append("read -r sh 2>/dev/null < \$c/shared_cpu_list; ")
        append("printf 'CACHE|%s|%s|%s|%s\\n' \"\$l\" \"\$t\" \"\$s\" \"\$sh\"; done; ")

        append("g=/sys/class/kgsl/kgsl-3d0; ")
        append("gm=; read -r gm 2>/dev/null < \$g/gpu_model; printf 'GPU|%s\\n' \"\$gm\"; ")
        append("gx=; read -r gx 2>/dev/null < \$g/max_gpuclk; printf 'GPUMX|%s\\n' \"\$gx\"; ")
        append("gg=; read -r gg 2>/dev/null < \$g/devfreq/governor; printf 'GPUGOV|%s\\n' \"\$gg\"; ")

        append("mt=; ma=; st=; sf=; while read -r k v rest; do ")
        append("case \"\$k\" in MemTotal:) mt=\$v;; MemAvailable:) ma=\$v;; ")
        append("SwapTotal:) st=\$v;; SwapFree:) sf=\$v;; esac; done < /proc/meminfo; ")
        append("printf 'MEM|%s|%s|%s|%s\\n' \"\$mt\" \"\$ma\" \"\$st\" \"\$sf\"; ")

        append("zo=; read -r zo 2>/dev/null < /sys/block/zram0/orig_data_size; printf 'ZRAM|%s\\n' \"\$zo\"; ")

        append("cp=; read -r cp 2>/dev/null < /sys/devices/system/cpu/possible; printf 'POSS|%s\\n' \"\$cp\"; ")
        append("on=; read -r on 2>/dev/null < /sys/devices/system/cpu/online; printf 'ONL|%s\\n' \"\$on\"")
    }

    private fun liveExtraCommand(): String = buildString {
        // 内存已在 buildAuxCommand 里读过一次（同一轮 shell），这里只补 zram / 电池 / 运行时长
        append("zo=; zu=; read -r zo 2>/dev/null < /sys/block/zram0/orig_data_size; ")
        append("read -r zu 2>/dev/null < /sys/block/zram0/mem_used_total; ")
        append("printf 'ZRAM|%s|%s\\n' \"\$zo\" \"\$zu\"; ")

        append("b=/sys/class/power_supply/battery; cap=; vol=; tmp=; sta=; ")
        append("read -r cap 2>/dev/null < \$b/capacity; ")
        append("read -r vol 2>/dev/null < \$b/voltage_now; ")
        append("read -r tmp 2>/dev/null < \$b/temp; ")
        append("read -r sta 2>/dev/null < \$b/status; ")
        append("printf 'BATT|%s|%s|%s|%s\\n' \"\$cap\" \"\$vol\" \"\$tmp\" \"\$sta\"; ")

        append("up=; rest=; read -r up rest 2>/dev/null < /proc/uptime; printf 'UPT|%s\\n' \"\$up\"")
    }
}

/**
 * 把 `0-3` / `0-1,4-5` / `0 1 2` 这类 cpu 列表展开成核数。
 * 内核的 possible / online 用连字符，高通的 related_cpus 用空格，两套都要支持。
 */
fun countCores(spec: String): Int = parseCoreIndices(spec).size

/** 解析 cpu 列表为升序去重后的下标集合。 */
fun parseCoreIndices(spec: String): List<Int> {
    if (spec.isBlank()) return emptyList()
    val result = sortedSetOf<Int>()
    spec.split(',', ' ', '\t').forEach { part ->
        val seg = part.trim()
        if (seg.isEmpty()) return@forEach
        val dash = seg.indexOf('-')
        if (dash <= 0) {
            seg.toIntOrNull()?.let { result += it }
        } else {
            val a = seg.substring(0, dash).trim().toIntOrNull() ?: return@forEach
            val b = seg.substring(dash + 1).trim().toIntOrNull() ?: return@forEach
            if (b >= a) for (i in a..b) result += i
        }
    }
    return result.toList()
}

/** 紧凑写法：`[0,1,2]` → `0-2`；`[0,1,4,5]` → `0-1,4-5`。 */
fun compactCoreSpec(spec: String): String {
    val cores = parseCoreIndices(spec)
    if (cores.isEmpty()) return spec.trim()
    val parts = mutableListOf<String>()
    var start = cores.first()
    var prev = start
    for (i in 1 until cores.size) {
        val cur = cores[i]
        if (cur == prev + 1) {
            prev = cur
        } else {
            parts += if (start == prev) "$start" else "$start-$prev"
            start = cur
            prev = cur
        }
    }
    parts += if (start == prev) "$start" else "$start-$prev"
    return parts.joinToString(",")
}

/** kHz → `2.0GHz` / `800MHz`。 */
fun formatFreq(khz: Long): String = when {
    khz >= 1_000_000 -> "%.1fGHz".format(khz / 1_000_000.0)
    khz >= 1_000 -> "%.0fMHz".format(khz / 1_000.0)
    else -> "${khz}kHz"
}

/** kB → 人类可读（二进制单位）。 */
fun formatKb(kb: Long): String = when {
    kb >= 1_048_576 -> "%.1f GB".format(kb / 1_048_576.0)
    kb >= 1_024 -> "%.0f MB".format(kb / 1_024.0)
    else -> "$kb KB"
}

/** 运行时长 → `3天 04:12`。 */
fun formatUptime(sec: Long): String {
    val d = sec / 86_400
    val h = (sec % 86_400) / 3_600
    val m = (sec % 3_600) / 60
    return if (d > 0) "%d天 %02d:%02d".format(d, h, m) else "%02d:%02d".format(h, m)
}
