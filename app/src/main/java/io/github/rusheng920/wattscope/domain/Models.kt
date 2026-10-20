package io.github.rusheng920.wattscope.domain

data class PowerZone(
    val path: String,
    val name: String,
    val hasPowerNode: Boolean,
)

/**
 * QPT 分区的展示顺序：SOC → CPU-L → CPU-M → GPU → NSP → 其它。
 *
 * 注意 CPU-L 是**大核簇**、CPU-M 是**中/小核簇**（SM8750 / 8 Elite 一代起沿用该命名），
 * 而内核枚举 energy_uj 的顺序是路径字典序，经常把 CPU-M 排在前面。
 * 图表、分区列表、OSD 都按这个顺序渲染，横向比较才符合「大核 → 小核」的直觉。
 */
fun zoneDisplayRank(name: String): Int = when (name.trim().lowercase()) {
    "soc" -> 0
    "cpu-l" -> 1
    "cpu-m" -> 2
    "gpu" -> 3
    "nsp" -> 4
    else -> 10
}

/** 按 [zoneDisplayRank] 排序；同档位保持原有相对顺序（稳定排序）。 */
fun List<PowerZone>.sortedForDisplay(): List<PowerZone> =
    sortedWith(compareBy({ zoneDisplayRank(it.name) }, { it.name }))

data class ZoneSample(
    val zonePath: String,
    val zoneName: String,
    val timestampMs: Long,
    val energyUj: Long,
    val powerUw: Long?,
    val computedPowerUw: Long?,
) {
    val effectivePowerUw: Long get() = powerUw ?: computedPowerUw ?: 0L
}

data class ZoneStats(
    val zoneName: String,
    val maxPowerUw: Long,
    val minPowerUw: Long,
    val avgPowerUw: Long,
    val totalEnergyUj: Long,
    val peakAtMs: Long,
)

/** 外观模式：跟随系统 / 强制浅色 / 强制深色（持久化到 SharedPreferences）。 */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
}

/**
 * 液态玻璃浓度：只作用于应用内悬浮底栏。
 *
 * 由 [com.kyant.backdrop]（社区 Backdrop 库）渲染真实玻璃：
 * [tintAlpha] 是表面着色不透明度（保证文字可读），
 * [blurRadiusDp] 是背景模糊半径，[refractionHeightDp] / [refractionAmountDp]
 * 控制边缘折射带的高度与强度（AGSL RuntimeShader，API 33+）。
 *
 * 参数取向参考 Kyant0/AndroidLiquidGlass 的 LiquidButton / LiquidBottomTabs：
 * 玻璃「通透」来自**低模糊 + 低着色 + 边缘折射**，而不是高模糊 + 高白度。
 * 早先 6–17dp 模糊叠加 26%–56% 白色着色，透光被吃掉，观感就是磨砂亚克力；
 * 现在把模糊压到 2–8dp、着色压到 5%–18%，靠折射与高光表现玻璃厚度。
 */
enum class GlassIntensity(
    val key: String,
    val label: String,
    val tintAlpha: Float,
    val blurRadiusDp: Float,
    val refractionHeightDp: Float,
    val refractionAmountDp: Float,
) {
    LIGHT("light", "轻盈", 0.05f, 2f, 10f, 16f),
    STANDARD("standard", "标准", 0.10f, 4f, 14f, 24f),
    STRONG("strong", "浓郁", 0.18f, 8f, 18f, 32f),
}

/**
 * 悬浮球尺寸档位（窗口尺寸，含渐变出血的 5dp 边距，实际球体比它小 10dp）。
 *
 * 悬浮球是常驻浮层，默认 40dp 窗口 / 30dp 球体；需要更不挡内容时可切到「迷你」，
 * 手指粗或需要更大命中率时切到「大」。
 * 尺寸改动由服务监听偏好实时应用，无需重启。
 */
enum class BubbleSize(val key: String, val label: String, val sizeDp: Int) {
    MINI("mini", "迷你", 32),
    STANDARD("standard", "标准", 40),
    LARGE("large", "大", 48),
}

enum class SamplingInterval(val millis: Long, val label: String) {
    Ms100(100, "100 毫秒"),
    Ms200(200, "200 毫秒"),
    Ms500(500, "500 毫秒"),
    Ms1000(1_000, "1000 毫秒"),
}

data class RecordingState(
    val isRecording: Boolean = false,
    val qptAvailable: Boolean = false,
    val qptEnabled: Boolean = false,
    val status: String = "正在检查 QPT",
    val zones: List<PowerZone> = emptyList(),
    val latestSamples: List<ZoneSample> = emptyList(),
    val interval: SamplingInterval = SamplingInterval.Ms200,
    val clusters: List<CpuCluster> = emptyList(),
    val tempSensors: TempSensors = TempSensors(),
    val devicePowerAvailable: Boolean = false,
    val latestDevicePowerUw: Long? = null,
    val gpuSensor: GpuSensor? = null,
    val ddrFreqPath: String? = null,
    val latestFps: Float? = null,
) {
    /** 能否开始记录：QPT 可用已启用，或存在任意附加监控源（无 QPT 的设备仅记录 freq/温度等）。 */
    val canRecord: Boolean
        get() = (qptAvailable && qptEnabled) ||
            clusters.isNotEmpty() ||
            !tempSensors.isEmpty ||
            devicePowerAvailable ||
            gpuSensor != null ||
            ddrFreqPath != null
}

data class CpuCluster(
    val path: String,
    val name: String,
)

data class GpuSensor(
    val freqPath: String,
    val busyPath: String?,
    val busyIsPercent: Boolean,
)

data class TempSensors(
    val cpuZones: List<String> = emptyList(),
    val ddrZone: String? = null,
    val gpuZone: String? = null,
    val hasBatteryTemp: Boolean = false,
) {
    val isEmpty: Boolean get() = cpuZones.isEmpty() && ddrZone == null && gpuZone == null && !hasBatteryTemp
}

data class ClusterFreqSample(
    val timestampMs: Long,
    val clusterName: String,
    val freqKhz: Long,
)

data class CoreUsageSample(
    val timestampMs: Long,
    val coreIndex: Int,
    val usagePercent: Float,
)

data class TempSample(
    val timestampMs: Long,
    val sensor: String,
    val tempC: Float,
)

data class DevicePowerSample(
    val timestampMs: Long,
    val powerUw: Long,
)

data class GpuSample(
    val timestampMs: Long,
    val freqHz: Long,
    val usagePercent: Float?,
)

data class DdrFreqSample(
    val timestampMs: Long,
    val freqHz: Long,
)

data class FpsSample(
    val timestampMs: Long,
    val fps: Float,
)

/** OSD 悬浮窗可选监控项（enum 声明顺序即默认显示顺序）。 */
enum class OsdMetric(val key: String, val label: String) {
    SOC_POWER("soc_power", "SOC"),
    CPU_L_POWER("cpu_l_power", "CPU-L"),
    CPU_M_POWER("cpu_m_power", "CPU-M"),
    GPU_POWER("gpu_power", "GPU"),
    GPU_FREQ("gpu_freq", "GPU频率"),
    FPS("fps", "FPS"),
    CPU_TEMP("cpu_temp", "CPU温度"),
    GPU_TEMP("gpu_temp", "GPU温度"),
    DDR_TEMP("ddr_temp", "DDR温度"),
    GPU_USAGE("gpu_usage", "GPU占用"),
    DEVICE_POWER("device_power", "整机"),
}

/**
 * 记录详情页曲线区块。声明顺序即默认排序（分析动线：总功率 → 性能 → 原因下钻 → 分区细分 → 温度）。
 * 用户可在详情页自定义排序，持久化到全局设置 —— 所有记录共用同一顺序。
 */
enum class DetailChartSection(val label: String) {
    SOC("SOC 功率"),
    FPS("FPS"),
    DEVICE_POWER("整机功耗"),
    CLUSTER_FREQ("CPU 簇频率"),
    CORE_USAGE("CPU 核心占用"),
    GPU("GPU 频率/占用"),
    DDR("DDR 频率"),
    ZONES("分区功率"),
    TEMP("温度"),
}

/** OSD 悬浮窗实时数据（不落库，仅内存展示）。 */
data class OsdSample(
    val socPowerUw: Long? = null,
    val cpuLPowerUw: Long? = null,
    val cpuMPowerUw: Long? = null,
    val gpuPowerUw: Long? = null,
    val gpuFreqHz: Long? = null,
    val gpuUsagePercent: Float? = null,
    val fps: Float? = null,
    val cpuTempC: Float? = null,
    val gpuTempC: Float? = null,
    val ddrTempC: Float? = null,
    val devicePowerUw: Long? = null,
) {
    /** 将指定监控项格式化为 "标签 值单位" 展示文本；无数据返回 null。 */
    fun format(metric: OsdMetric): String? = value(metric)?.let { "${metric.label} $it" }

    /**
     * 只取值与单位（不含标签）：OSD 面板把标签和数值分两列排版，
     * 数值列右对齐等宽显示，位数变化时不会带动标签列抖动。
     */
    fun value(metric: OsdMetric): String? = when (metric) {
        OsdMetric.SOC_POWER -> socPowerUw?.let(::uwToW)
        OsdMetric.CPU_L_POWER -> cpuLPowerUw?.let(::uwToW)
        OsdMetric.CPU_M_POWER -> cpuMPowerUw?.let(::uwToW)
        OsdMetric.GPU_POWER -> gpuPowerUw?.let(::uwToW)
        OsdMetric.GPU_FREQ -> gpuFreqHz?.let(::freqToStr)
        OsdMetric.FPS -> fps?.let { "%.0f fps".format(it) }
        OsdMetric.CPU_TEMP -> cpuTempC?.let { "%.0f℃".format(it) }
        OsdMetric.GPU_TEMP -> gpuTempC?.let { "%.0f℃".format(it) }
        OsdMetric.DDR_TEMP -> ddrTempC?.let { "%.0f℃".format(it) }
        OsdMetric.GPU_USAGE -> gpuUsagePercent?.let { "%.0f%%".format(it) }
        OsdMetric.DEVICE_POWER -> devicePowerUw?.let(::uwToW)
    }

    private fun uwToW(uw: Long): String = "%.2fW".format(uw / 1_000_000.0)
    private fun freqToStr(hz: Long): String =
        if (hz >= 1_000_000_000) "%.2fGHz".format(hz / 1_000_000_000.0)
        else if (hz >= 1_000_000) "%.0fMHz".format(hz / 1_000_000.0)
        else "%.0fkHz".format(hz / 1_000.0)
}
