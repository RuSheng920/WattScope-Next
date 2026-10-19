package io.github.rusheng920.wattscope.data.repo

import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import io.github.rusheng920.wattscope.core.auxiliary.AuxReader
import io.github.rusheng920.wattscope.core.fps.FpsReader
import io.github.rusheng920.wattscope.core.qpt.QptPowerReader
import io.github.rusheng920.wattscope.core.root.RootManager
import io.github.rusheng920.wattscope.data.local.AuxSampleDao
import io.github.rusheng920.wattscope.data.local.ClusterFreqEntity
import io.github.rusheng920.wattscope.data.local.CoreUsageEntity
import io.github.rusheng920.wattscope.data.local.DdrFreqEntity
import io.github.rusheng920.wattscope.data.local.DevicePowerEntity
import io.github.rusheng920.wattscope.data.local.FpsEntity
import io.github.rusheng920.wattscope.data.local.GpuEntity
import io.github.rusheng920.wattscope.data.local.QptDatabase
import io.github.rusheng920.wattscope.data.local.RecordDao
import io.github.rusheng920.wattscope.data.local.RecordEntity
import io.github.rusheng920.wattscope.data.local.SampleDao
import io.github.rusheng920.wattscope.data.local.SampleEntity
import io.github.rusheng920.wattscope.data.local.TempEntity
import io.github.rusheng920.wattscope.domain.ClusterFreqSample
import io.github.rusheng920.wattscope.domain.AppPermissions
import io.github.rusheng920.wattscope.domain.BubbleSize
import io.github.rusheng920.wattscope.domain.CoreUsageSample
import io.github.rusheng920.wattscope.domain.GlassIntensity
import io.github.rusheng920.wattscope.domain.OsdMetric
import io.github.rusheng920.wattscope.domain.OsdSample
import io.github.rusheng920.wattscope.domain.RecordingState
import io.github.rusheng920.wattscope.domain.SamplingInterval
import io.github.rusheng920.wattscope.domain.TempSample
import io.github.rusheng920.wattscope.domain.ThemeMode
import io.github.rusheng920.wattscope.domain.ZoneSample
import io.github.rusheng920.wattscope.domain.ZoneStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.max

class MonitorRepository(
    context: Context,
    private val database: QptDatabase,
    private val recordDao: RecordDao,
    private val sampleDao: SampleDao,
    private val auxSampleDao: AuxSampleDao,
    private val qptReader: QptPowerReader,
    private val auxReader: AuxReader,
    private val rootManager: RootManager,
) {
    private val appInspector = AppInspector(context, rootManager)
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("qpt_settings", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var samplerJob: Job? = null
    private var liveJob: Job? = null
    private var activeRecord: RecordEntity? = null

    /** 采样缓冲：攒够约 1 秒再一次性提交。仅由采样协程（含其 finally）读写，无需加锁。 */
    private val pendingBatches = mutableListOf<PendingBatch>()

    // App 是否可见：由 Activity 的 onStart/onStop 驱动。不可见时预览降频而不是停止，保证后台曲线仍能滚动
    private var uiVisible = false

    // 设备拓扑扫描缓存：启动/首次记录扫一次，之后 5 分钟内不再重复（设备能力不会中途变化）
    private var scanned = false
    private var lastScanElapsedMs = 0L

    // FPS 图层偏好：null = 自动跟随前台应用；非空 = 用户在设置页手工指定
    private val _fpsLayer = MutableStateFlow(prefs.getString("fps_layer", null)?.takeIf { it.isNotBlank() })
    val fpsLayerFlow: StateFlow<String?> = _fpsLayer.asStateFlow()

    /** FPS 独立采集：SurfaceFlinger 为主、gfxinfo 回退；前台包名与自动停止检查共用 AppInspector 缓存。 */
    private val fpsReader = FpsReader(
        rootManager = rootManager,
        foregroundPackage = { maxAgeMs -> appInspector.foregroundPackage(maxAgeMs) },
        layerOverride = { _fpsLayer.value },
    )

    private val _state = MutableStateFlow(RecordingState(interval = loadSavedInterval()))
    val state: StateFlow<RecordingState> = _state.asStateFlow()
    val records = recordDao.observeRecords()

    /** 读取持久化的采样间隔（默认 200ms）。 */
    private fun loadSavedInterval(): SamplingInterval =
        prefs.getString("sampling_interval", null)
            ?.let { name -> SamplingInterval.entries.firstOrNull { it.name == name } }
            ?: SamplingInterval.Ms200

    // 悬浮窗开关：Compose 可观察状态（写偏好同时更新 StateFlow，开关 UI 立即响应）
    private val _bubbleEnabled = MutableStateFlow(prefs.getBoolean("bubble_enabled", true))
    val bubbleEnabledFlow: StateFlow<Boolean> = _bubbleEnabled.asStateFlow()

    /** 悬浮窗是否开启（持久化偏好，供通知控制面板与服务使用）。 */
    var bubbleEnabled: Boolean
        get() = _bubbleEnabled.value
        set(value) {
            prefs.edit().putBoolean("bubble_enabled", value).apply()
            _bubbleEnabled.value = value
        }

    /** 悬浮球尺寸：服务订阅该 Flow，切换档位后立即 resize，不必重启服务。 */
    private val _bubbleSize = MutableStateFlow(
        prefs.getString("bubble_size", null)
            ?.let { key -> BubbleSize.entries.firstOrNull { it.key == key } }
            ?: BubbleSize.STANDARD,
    )
    val bubbleSizeFlow: StateFlow<BubbleSize> = _bubbleSize.asStateFlow()

    var bubbleSize: BubbleSize
        get() = _bubbleSize.value
        set(value) {
            prefs.edit().putString("bubble_size", value.key).apply()
            _bubbleSize.value = value
        }

    // OSD 悬浮窗：开关 + 实时数据（不落库）
    private val _osdEnabled = MutableStateFlow(prefs.getBoolean("osd_enabled", false))
    val osdEnabledFlow: StateFlow<Boolean> = _osdEnabled.asStateFlow()

    private val _osdMetrics = MutableStateFlow(loadOsdMetrics())
    val osdMetricsFlow: StateFlow<List<OsdMetric>> = _osdMetrics.asStateFlow()

    // ---------- 外观与权限 ----------

    /** 外观模式（跟随系统 / 浅色 / 深色），Compose 直接订阅。 */
    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeModeFlow: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** 悬浮玻璃底栏：关闭后回落为贴底的 MIUIX 导航栏。 */
    private val _glassBottomBar = MutableStateFlow(prefs.getBoolean("glass_bottom_bar", true))
    val glassBottomBarFlow: StateFlow<Boolean> = _glassBottomBar.asStateFlow()

    /** 液态玻璃浓度（三档）。 */
    private val _glassIntensity = MutableStateFlow(loadGlassIntensity())
    val glassIntensityFlow: StateFlow<GlassIntensity> = _glassIntensity.asStateFlow()

    /**
     * 记录期间是否暂停实时模糊。
     *
     * 液态玻璃依赖 RenderEffect + RuntimeShader，逐帧模糊会持续占用 GPU；
     * 本应用的用途就是测量功耗，因此默认在记录时降级为静态玻璃，避免测量结果被自身 UI 污染。
     */
    private val _glassPauseRecording = MutableStateFlow(prefs.getBoolean("glass_pause_recording", true))
    val glassPauseRecordingFlow: StateFlow<Boolean> = _glassPauseRecording.asStateFlow()

    /**
     * 首次安装引导是否完成。
     * 未完成时不探测 root、不启动 QPT 采样，避免应用一打开就弹授权框。
     */
    private val _onboardingDone = MutableStateFlow(prefs.getBoolean("onboarding_done", false))
    val onboardingDoneFlow: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.key).apply()
        _themeMode.value = mode
    }

    fun setGlassBottomBar(enabled: Boolean) {
        prefs.edit().putBoolean("glass_bottom_bar", enabled).apply()
        _glassBottomBar.value = enabled
    }

    fun setGlassIntensity(intensity: GlassIntensity) {
        prefs.edit().putString("glass_intensity", intensity.key).apply()
        _glassIntensity.value = intensity
    }

    fun setGlassPauseRecording(paused: Boolean) {
        prefs.edit().putBoolean("glass_pause_recording", paused).apply()
        _glassPauseRecording.value = paused
    }

    private fun loadThemeMode(): ThemeMode =
        prefs.getString("theme_mode", null)
            ?.let { key -> ThemeMode.entries.firstOrNull { it.key == key } }
            ?: ThemeMode.SYSTEM

    private fun loadGlassIntensity(): GlassIntensity =
        prefs.getString("glass_intensity", null)
            ?.let { key -> GlassIntensity.entries.firstOrNull { it.key == key } }
            ?: GlassIntensity.STANDARD

    /** 完成首次引导；[probe] = true 时立即探测 root 并启动 QPT 能力扫描。 */
    suspend fun completeOnboarding(probe: Boolean) {
        prefs.edit().putBoolean("onboarding_done", true).apply()
        _onboardingDone.value = true
        if (probe) {
            _state.update { it.copy(status = "正在检查 QPT") }
            refreshDeviceState(force = true)
        } else {
            _state.update { it.copy(status = "尚未授予 Root，部分功能不可用") }
        }
    }

    /** 权限状态快照：Activity 每次 onResume 与从系统设置返回后刷新。 */
    private val _permissions = MutableStateFlow(AppPermissions())
    val permissions: StateFlow<AppPermissions> = _permissions.asStateFlow()

    fun refreshPermissions() {
        scope.launch { _permissions.value = readPermissions() }
    }

    /** 主动申请 root：会弹出 KernelSU / Magisk 授权框，返回用户选择结果。 */
    suspend fun requestRootAccess(): Boolean {
        val granted = rootManager.requestRoot()
        _permissions.value = readPermissions()
        return granted
    }

    private suspend fun readPermissions(): AppPermissions = withContext(Dispatchers.IO) {
        val notificationManager = NotificationManagerCompat.from(appContext)
        val powerManager = appContext.getSystemService(PowerManager::class.java)
        val appOps = appContext.getSystemService(AppOpsManager::class.java)
        val usageGranted = appOps?.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName,
        ) == AppOpsManager.MODE_ALLOWED
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        AppPermissions(
            root = rootManager.isRootGranted(),
            overlay = Settings.canDrawOverlays(appContext),
            notifications = notificationManager.areNotificationsEnabled(),
            batteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) ?: false,
            usageAccess = usageGranted,
            isMiui = manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco"),
        )
    }

    private val _osdSample = MutableStateFlow<OsdSample?>(null)
    val osdSample: StateFlow<OsdSample?> = _osdSample.asStateFlow()

    // OSD 上次成功采到的数据：某监控项刷新缓慢/瞬时读取失败时保持上次值继续显示，而不是该项缺失
    private var lastOsdSample: OsdSample? = null

    /** 当前选中的 FPS 图层；null 表示自动。 */
    var fpsLayer: String?
        get() = _fpsLayer.value
        set(value) {
            val normalized = value?.takeIf { it.isNotBlank() }
            prefs.edit().putString("fps_layer", normalized ?: "").apply()
            _fpsLayer.value = normalized
            fpsReader.reset()
        }

    var osdEnabled: Boolean
        get() = _osdEnabled.value
        set(value) {
            prefs.edit().putBoolean("osd_enabled", value).apply()
            _osdEnabled.value = value
            if (!value) {
                // 关闭 OSD：清掉快照，避免下次打开时先闪一帧旧数据
                _osdSample.value = null
                lastOsdSample = null
            }
            syncLive()
        }

    /** 设置 OSD 显示项及顺序（持久化）。 */
    fun setOsdMetrics(metrics: List<OsdMetric>) {
        prefs.edit().putString("osd_metrics", metrics.joinToString(",") { it.key }).apply()
        _osdMetrics.value = metrics
    }

    private fun loadOsdMetrics(): List<OsdMetric> {
        val saved = prefs.getString("osd_metrics", null) ?: return OsdMetric.entries
        return saved.split(',').mapNotNull { key -> OsdMetric.entries.firstOrNull { it.key == key } }
    }

    init {
        // 进程重启（崩溃/被杀）后终结上次未关闭的记录，避免悬空记录
        scope.launch { closeDanglingRecords() }
    }

    private suspend fun closeDanglingRecords() {
        val now = System.currentTimeMillis()
        recordDao.getOpenRecords().forEach { record ->
            val samples = sampleDao.getSamples(record.id)
            val stats = samples.toStats()
            recordDao.update(
                record.copy(
                    endedAtMs = now,
                    avgSocPowerUw = stats.avgFor("SOC"),
                    avgGpuPowerUw = stats.avgFor("GPU"),
                    avgCpuMPowerUw = stats.avgFor("CPU-M"),
                    avgCpuLPowerUw = stats.avgFor("CPU-L"),
                    avgNspPowerUw = stats.avgFor("NSP"),
                ),
            )
        }
    }

    /**
     * 重新探测设备能力。
     * @param force true 时忽略缓存（用户点刷新、切换 QPT 开关）；其余场景走 [SCAN_TTL_MS] 缓存，
     *              避免每次开始记录都在测量窗口里塞进 11 次 su 往返。
     */
    suspend fun refreshDeviceState(force: Boolean = false) = withContext(Dispatchers.IO) {
        // 首次引导未完成前不探测：hasRoot() 会拉起 su 授权框，必须由用户在权限页主动触发
        if (!_onboardingDone.value) {
            _state.update { it.copy(status = "请先完成权限设置") }
            return@withContext
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && scanned && now - lastScanElapsedMs < SCAN_TTL_MS) return@withContext

        val rootOk = qptReader.hasRoot()
        if (!rootOk) {
            _state.update { it.copy(qptAvailable = false, qptEnabled = false, status = "Root 失败，请检查 KernelSU 授权") }
            return@withContext
        }
        val available = qptReader.isAvailable()
        val enabled = if (available) qptReader.isEnabled() else false
        val zones = if (available) qptReader.scanZones() else emptyList()
        qptReader.cacheZones(zones)
        val clusters = auxReader.scanClusters()
        val tempSensors = auxReader.scanTempZones()
        val devicePowerAvailable = auxReader.isBatteryPowerAvailable()
        val gpuSensor = auxReader.scanGpuSensor()
        val ddrFreqPath = auxReader.scanDdrFreqPath()

        scanned = true
        lastScanElapsedMs = now
        _state.update {
            it.copy(
                qptAvailable = available,
                qptEnabled = enabled,
                zones = zones,
                clusters = clusters,
                tempSensors = tempSensors,
                devicePowerAvailable = devicePowerAvailable,
                gpuSensor = gpuSensor,
                ddrFreqPath = ddrFreqPath,
                status = if (available) {
                    if (enabled) "QPT 已开启，发现 ${zones.size} 个功耗分区" else "QPT 已关闭"
                } else {
                    "无 QPT 支持，仅记录频率/温度等数据"
                },
            )
        }
        // 应用启动 / 刷新后按需拉起实时循环（QPT 开、OSD 开或界面可见）
        syncLive()
    }

    /** 枚举 SurfaceFlinger 图层，供设置页手工指定 FPS 数据源。 */
    suspend fun listFpsLayers(): List<String> = fpsReader.listLayers()

    suspend fun setQptEnabled(enabled: Boolean) {
        if (!qptReader.setEnabled(enabled)) {
            _state.update { it.copy(status = "写入 enabled 失败，请检查 KernelSU 授权") }
            return
        }
        if (enabled) {
            refreshDeviceState(force = true)
            syncLive()
        } else {
            stopLive()
            stopRecording()
            refreshDeviceState(force = true)
        }
    }

    fun setInterval(interval: SamplingInterval) {
        // 采样间隔持久化：重启后恢复上次选择
        prefs.edit().putString("sampling_interval", interval.name).apply()
        _state.update { it.copy(interval = interval) }
    }

    /**
     * 实时采样循环：首页预览、OSD 浮层共用同一条链路。
     *
     * 旧实现拆成两条循环 —— 「轻量 preview」只读 QPT，「OSD」才读 QPT + aux + FPS。
     * 结果是：不开 OSD 时首页的整机功耗与 FPS 永远是 `--`（preview 根本不采）；
     * 而 OSD 若在设备扫描完成前被打开，`startOsd()` 会因 `canRecord == false` 直接返回且**不再重试**，
     * 之后两条循环都不跑，只有在开始记录后（record 循环自己填 StateFlow）才有数据。
     *
     * 现在合并为一条：QPT 按采样间隔读，aux（温度 / GPU / DDR / 整机功耗）与 FPS 按 1s 读，
     * 一次 su 往返同时刷新 latestSamples / latestDevicePowerUw / latestFps / osdSample。
     * 记录期间由 record 循环接管，本循环停止，避免双重采样。
     */
    private fun startLive() {
        if (liveJob?.isActive == true) return
        // 记录中由 record 循环负责，本循环不参与
        if (samplerJob?.isActive == true) return
        val current = _state.value
        if (!current.canRecord) return
        // 没人看就不采：UI 不可见、OSD 关闭、QPT 关闭时没有任何展示需求
        if (!uiVisible && !_osdEnabled.value && !current.qptEnabled) return

        val qptCommand = if (current.qptEnabled) qptReader.buildSampleCommand(current.zones) else ""
        val auxCommand = auxReader.buildAuxCommand(current.clusters, current.tempSensors, current.gpuSensor, current.ddrFreqPath)
        val fullCommand = joinShellCommands(qptCommand, auxCommand)
        if (fullCommand.isBlank()) return

        // 慢变量（温度 / 频率 / 整机功率 / 每核占用）按 ~1s 采样，QPT 功率保持用户设定的间隔
        val slowEvery = max(1L, ceil(1_000.0 / current.interval.millis).toLong())

        liveJob = scope.launch {
            var tick = 0L
            var lastFpsMs = 0L
            var lastUi = 0L
            var fps: Float? = null
            var devicePowerUw: Long? = null
            var nextAt = SystemClock.elapsedRealtime()
            while (isActive && samplerJob?.isActive != true) {
                val elapsed = SystemClock.elapsedRealtime()
                val wall = System.currentTimeMillis()
                val slow = tick % slowEvery == 0L
                // 没有 QPT 时只有慢变量，避免按 interval 反复执行同一条 aux 命令
                val command = when {
                    qptCommand.isBlank() -> if (slow) fullCommand else ""
                    slow -> fullCommand
                    else -> qptCommand
                }
                // 单次 su 失败（授权对话框、瞬时 IO 错误）不能让整条实时循环退出，否则首页会静默停更
                val lines = runCatching {
                    if (command.isBlank()) emptyList() else rootManager.execute(command)
                }.getOrElse { emptyList() }
                val zones = if (current.qptEnabled && lines.isNotEmpty()) {
                    qptReader.parseSampleLines(lines, wall, elapsed)
                } else {
                    emptyList()
                }
                val aux = if (slow && lines.isNotEmpty()) auxReader.parseAuxLines(lines, wall) else null
                aux?.devicePowerUw?.let { devicePowerUw = it }

                // FPS 走 SurfaceFlinger 呈现帧计数，1s 一次足够，成本远低于每 tick dumpsys
                if (elapsed - lastFpsMs >= FPS_INTERVAL_MS) {
                    lastFpsMs = elapsed
                    fpsReader.sample()?.fps?.let { fps = it }
                }

                // 一次 StateFlow 更新同时喂首页与 OSD，减少 CAS / 对象分配 / Compose 重组
                if (elapsed - lastUi >= UI_THROTTLE_MS) {
                    lastUi = elapsed
                    _state.update { s ->
                        s.copy(
                            latestSamples = if (zones.isNotEmpty()) zones else s.latestSamples,
                            latestDevicePowerUw = devicePowerUw ?: s.latestDevicePowerUw,
                            latestFps = fps ?: s.latestFps,
                        )
                    }
                    if (_osdEnabled.value) {
                        _osdSample.value = buildOsdSample(zones, aux ?: AuxReader.AuxBatch(), fps)
                    }
                }

                tick++
                // 没有 QPT（只读 1s 慢变量）时不必按高频率唤醒
                val period = if (current.qptEnabled && (uiVisible || _osdEnabled.value)) {
                    _state.value.interval.millis
                } else {
                    BACKGROUND_INTERVAL_MS
                }
                nextAt += period
                val sleep = nextAt - SystemClock.elapsedRealtime()
                if (sleep > 0) delay(sleep) else nextAt = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun stopLive() {
        liveJob?.cancel()
        liveJob = null
    }

    /**
     * 拼接多条 shell 片段。
     *
     * 每段先去掉末尾的分号再连接：任何一段以 `"; "` 收尾，直接拼出来就是 `; ;`，
     * shell 会把它当语法错误整条丢弃 —— 慢变量（温度 / GPU / DDR / 整机功耗）曾因此从未被采样到。
     */
    private fun joinShellCommands(vararg parts: String): String =
        parts.map { it.trim().trimEnd(';').trim() }
            .filter { it.isNotEmpty() }
            .joinToString("; ")

    /** 按当前需求启停实时循环：QPT 开 / OSD 开 / 界面可见 三者之一才需要采样。 */
    private fun syncLive() {
        val s = _state.value
        val needed = s.canRecord && (s.qptEnabled || _osdEnabled.value || uiVisible)
        if (!needed) {
            stopLive()
        } else if (samplerJob?.isActive != true) {
            startLive()
        }
    }

    /** Activity 可见性回调：只影响采样节奏与启停，记录与 OSD 不受影响。 */
    fun setUiVisible(visible: Boolean) {
        if (uiVisible == visible) return
        uiVisible = visible
        if (samplerJob?.isActive == true) return
        syncLive()
    }

    private fun buildOsdSample(zones: List<ZoneSample>, aux: AuxReader.AuxBatch, fps: Float?): OsdSample {
        // 一次遍历建立 zone 功耗与温度查找表，避免多次 firstOrNull 重复扫描
        val zonePower = zones.associate { it.zoneName.lowercase() to it.effectivePowerUw }
        val temps = aux.temps.associate { it.sensor to it.tempC }
        val prev = lastOsdSample
        return OsdSample(
            // 本轮读到新值用新值；未读到（采样周期慢/瞬时失败）保留上次值，避免该行消失
            socPowerUw = zonePower["soc"] ?: prev?.socPowerUw,
            cpuLPowerUw = zonePower["cpu-l"] ?: prev?.cpuLPowerUw,
            cpuMPowerUw = zonePower["cpu-m"] ?: prev?.cpuMPowerUw,
            gpuPowerUw = zonePower["gpu"] ?: prev?.gpuPowerUw,
            gpuFreqHz = aux.gpuFreqHz ?: prev?.gpuFreqHz,
            gpuUsagePercent = aux.gpuUsagePercent ?: prev?.gpuUsagePercent,
            fps = fps ?: prev?.fps,
            cpuTempC = temps["cpu"] ?: prev?.cpuTempC,
            gpuTempC = temps["gpu"] ?: prev?.gpuTempC,
            ddrTempC = temps["ddr"] ?: prev?.ddrTempC,
            devicePowerUw = aux.devicePowerUw ?: prev?.devicePowerUw,
        ).also { lastOsdSample = it }
    }

    /**
     * 开始记录。记录开始时读取当前前台应用并写入 record.appName。
     * 无需 QPT 支持：只要存在任意附加监控源（簇频率/温度/整机功耗/GPU/DDR）即可记录。
     * @param autoStopOnAppExit 悬浮球启动时传 true：前台应用离开被记录应用后自动停止记录。
     * @return 是否成功开始记录（无数据源/已在录制返回 false）
     */
    suspend fun startRecording(autoStopOnAppExit: Boolean = false): Boolean {
        refreshDeviceState()
        val current = _state.value
        if (!current.canRecord) {
            _state.update {
                it.copy(status = if (current.qptAvailable) "请开启 QPT 或检查监控源" else "无可用监控数据源")
            }
            return false
        }
        if (samplerJob?.isActive == true) return false

        val app = appInspector.currentForegroundApp()
        val startedAt = System.currentTimeMillis()
        val recordId = recordDao.insert(
            RecordEntity(
                appName = app.appName,
                packageName = app.packageName,
                iconPackage = app.iconPackage,
                startedAtMs = startedAt,
                endedAtMs = null,
            ),
        )
        activeRecord = RecordEntity(
            id = recordId,
            appName = app.appName,
            packageName = app.packageName,
            iconPackage = app.iconPackage,
            startedAtMs = startedAt,
            endedAtMs = null,
        )
        qptReader.resetDeltas()
        auxReader.resetDeltas()
        fpsReader.reset()
        val qptNote = if (current.qptEnabled) "" else "（无 QPT）"
        _state.update { it.copy(isRecording = true, status = "记录中$qptNote：${app.appName}") }
        // 记录期间由 record 循环接管首页刷新，暂停实时循环
        stopLive()

        // 采样循环内不变的量缓存到局部变量，避免每 tick 读取 StateFlow / 重建命令字符串
        val intervalMs = current.interval.millis
        val qptEnabled = current.qptEnabled
        val fastCommand = qptReader.buildSampleCommand(current.zones)
        val slowCommand = joinShellCommands(
            fastCommand,
            auxReader.buildAuxCommand(current.clusters, current.tempSensors, current.gpuSensor, current.ddrFreqPath),
        )
        // 慢变量（温度/频率/每核占用/整机功率）按 ~1s 节奏采样；QPT 功率保持原始间隔
        val slowEvery = max(1L, ceil(1_000.0 / intervalMs).toLong())

        samplerJob = scope.launch {
            val recordPackage = app.packageName
            var tick = 0L
            var nextAt = SystemClock.elapsedRealtime()
            var lastAppCheckMs = 0L
            var lastFpsSampleMs = 0L
            var lastUiUpdateMs = 0L
            var lastFps: Float? = null
            var lastDevicePowerUw: Long? = null
            try {
                while (isActive) {
                    val elapsed = SystemClock.elapsedRealtime()
                    val wall = System.currentTimeMillis()
                    val slow = tick % slowEvery == 0L
                    val command = if (slow) slowCommand else fastCommand

                    if (command.isNotBlank()) {
                        val lines = rootManager.execute(command)
                        val qptSamples = if (qptEnabled) qptReader.parseSampleLines(lines, wall, elapsed) else emptyList()
                        val auxBatch = if (slow) auxReader.parseAuxLines(lines, wall) else null
                        auxBatch?.devicePowerUw?.let { lastDevicePowerUw = it }

                        // 批量落库：攒够 ~1s 再提交，把 WAL fsync 从每天 5 次/秒降到 1 次/秒
                        pendingBatches += PendingBatch(wall, qptSamples, auxBatch)
                        if (pendingBatches.size >= slowEvery) flushBatches(recordId)

                        // OSD 悬浮窗：QPT 每 tick 都新，aux 用上次值兜底
                        if (_osdEnabled.value) {
                            _osdSample.value = buildOsdSample(qptSamples, auxBatch ?: AuxReader.AuxBatch(), lastFps)
                        }
                        // UI 节流：合并为一次 StateFlow 更新，减少 CAS + 对象分配 + Compose 重组
                        if (elapsed - lastUiUpdateMs >= UI_THROTTLE_MS) {
                            lastUiUpdateMs = elapsed
                            _state.update { s ->
                                s.copy(
                                    latestSamples = if (qptSamples.isNotEmpty()) qptSamples else s.latestSamples,
                                    latestDevicePowerUw = lastDevicePowerUw ?: s.latestDevicePowerUw,
                                )
                            }
                        }
                    }

                    // FPS：SurfaceFlinger 层帧时间戳（全屏游戏等价于游戏帧率），每 ~1s 一次。
                    // 前台包名走 AppInspector 缓存，不再额外 dumpsys activity。
                    if (elapsed - lastFpsSampleMs >= 1_000) {
                        lastFpsSampleMs = elapsed
                        val fps = fpsReader.sample()?.fps
                        if (fps != null) {
                            lastFps = fps
                            pendingBatches += PendingBatch(wall, emptyList(), null, fps)
                            if (elapsed - lastUiUpdateMs >= UI_THROTTLE_MS) {
                                lastUiUpdateMs = elapsed
                                _state.update { it.copy(latestFps = fps) }
                            }
                        }
                    }

                    // 悬浮窗自动停止：离开被记录应用即停（每 2s 检查一次，使用缓存的前台包名）
                    if (autoStopOnAppExit && elapsed - lastAppCheckMs >= 2_000) {
                        lastAppCheckMs = elapsed
                        if (!appInspector.isForeground(recordPackage)) break
                    }

                    tick++
                    nextAt += intervalMs
                    val sleep = nextAt - SystemClock.elapsedRealtime()
                    if (sleep > 0) delay(sleep) else nextAt = SystemClock.elapsedRealtime()
                }
            } finally {
                val naturalExit = isActive
                withContext(NonCancellable) {
                    // 收尾：把缓冲里最后不足 1s 的样本写完，再算均值
                    flushBatches(recordId)
                    // break 退出（应用离开）时自动终结记录。
                    // 注意不能调用 stopRecording()：其内部会取消当前协程自身。
                    if (naturalExit) {
                        samplerJob = null
                        finalizeActiveRecord()
                        // 记录结束：交回实时循环（QPT / OSD / 界面可见任一需要即恢复）
                        syncLive()
                    }
                }
            }
        }
        return true
    }

    /** 未提交的采样批次。仅由采样协程读写（含其 finally），无需加锁。 */
    private data class PendingBatch(
        val timestampMs: Long,
        val qpt: List<ZoneSample>,
        val aux: AuxReader.AuxBatch?,
        val fps: Float? = null,
    )

    private suspend fun flushBatches(recordId: Long) {
        if (pendingBatches.isEmpty()) return
        val batches = pendingBatches.toList()
        // 先写库再清空：若中途被取消（withTransaction 回滚），finally 里的 NonCancellable 重试还能保住这批数据
        persistBatches(recordId, batches)
        pendingBatches.clear()
    }

    private suspend fun persistBatches(recordId: Long, batches: List<PendingBatch>) {
        if (batches.isEmpty()) return
        database.withTransaction {
            batches.forEach { b ->
                if (b.qpt.isNotEmpty()) sampleDao.insertAll(b.qpt.map { it.toEntity(recordId) })
                b.aux?.let { insertAuxBatchInTransaction(recordId, b.timestampMs, it) }
                b.fps?.let { fps ->
                    auxSampleDao.insertFps(listOf(FpsEntity(recordId = recordId, timestampMs = b.timestampMs, fps = fps)))
                }
            }
        }
    }

    /** 事务内执行的附加监控项批量写入（不更新 UI 状态）。 */
    private suspend fun insertAuxBatchInTransaction(recordId: Long, timestampMs: Long, batch: AuxReader.AuxBatch) {
        if (batch.clusterFreqs.isNotEmpty()) {
            auxSampleDao.insertClusterFreqs(batch.clusterFreqs.map { it.toEntity(recordId) })
        }
        if (batch.temps.isNotEmpty()) {
            auxSampleDao.insertTemps(batch.temps.map { it.toEntity(recordId) })
        }
        batch.devicePowerUw?.let { power ->
            auxSampleDao.insertDevicePowers(listOf(DevicePowerEntity(recordId = recordId, timestampMs = timestampMs, powerUw = power)))
        }
        if (batch.coreUsages.isNotEmpty()) {
            auxSampleDao.insertCoreUsages(batch.coreUsages.map { it.toEntity(recordId) })
        }
        if (batch.gpuFreqHz != null) {
            auxSampleDao.insertGpus(
                listOf(GpuEntity(recordId = recordId, timestampMs = timestampMs, freqHz = batch.gpuFreqHz, usagePercent = batch.gpuUsagePercent)),
            )
        }
        if (batch.ddrFreqHz != null) {
            auxSampleDao.insertDdrFreqs(
                listOf(DdrFreqEntity(recordId = recordId, timestampMs = timestampMs, freqHz = batch.ddrFreqHz)),
            )
        }
    }

    suspend fun stopRecording() {
        // cancelAndJoin 等 finally 里的收尾落库完成，否则统计会漏掉最后一秒
        samplerJob?.cancelAndJoin()
        samplerJob = null
        finalizeActiveRecord()
        // 停止记录后交回实时循环（内部按 QPT / OSD / 可见性决定是否真的采样）
        syncLive()
    }

    private suspend fun finalizeActiveRecord() {
        val record = activeRecord ?: run {
            _state.update { it.copy(isRecording = false) }
            return
        }
        val samples = sampleDao.getSamples(record.id)
        val stats = samples.toStats()
        val avgFps = auxSampleDao.getFps(record.id).map { it.fps }.average().toFloat().takeIf { it > 0f }
        recordDao.update(
            record.copy(
                endedAtMs = System.currentTimeMillis(),
                avgSocPowerUw = stats.avgFor("SOC"),
                avgGpuPowerUw = stats.avgFor("GPU"),
                avgCpuMPowerUw = stats.avgFor("CPU-M"),
                avgCpuLPowerUw = stats.avgFor("CPU-L"),
                avgNspPowerUw = stats.avgFor("NSP"),
                avgFps = avgFps,
            ),
        )
        activeRecord = null
        _state.update { it.copy(isRecording = false, status = "记录已保存") }
    }

    fun observeRecord(id: Long) = recordDao.observeRecord(id)

    fun observeSamples(recordId: Long, stride: Int = 1) = sampleDao.observeSamples(recordId, stride)

    fun observeClusterFreqs(recordId: Long, stride: Int = 1) = auxSampleDao.observeClusterFreqs(recordId, stride)

    fun observeCoreUsages(recordId: Long, stride: Int = 1) = auxSampleDao.observeCoreUsages(recordId, stride)

    fun observeTemps(recordId: Long, stride: Int = 1) = auxSampleDao.observeTemps(recordId, stride)

    fun observeDevicePowers(recordId: Long, stride: Int = 1) = auxSampleDao.observeDevicePowers(recordId, stride)

    fun observeGpus(recordId: Long, stride: Int = 1) = auxSampleDao.observeGpus(recordId, stride)

    fun observeDdrFreqs(recordId: Long, stride: Int = 1) = auxSampleDao.observeDdrFreqs(recordId, stride)

    fun observeFps(recordId: Long, stride: Int = 1) = auxSampleDao.observeFps(recordId, stride)

    /** 详情页降采样步长：把任意长度的记录压到约 [DETAIL_TARGET_ROWS] 行。 */
    suspend fun detailStride(recordId: Long): Int {
        val rows = sampleDao.countSamples(recordId)
        return max(1, rows / DETAIL_TARGET_ROWS)
    }

    suspend fun deleteRecord(id: Long) = recordDao.delete(id)

    suspend fun deleteAll() = recordDao.deleteAll()

    /** 完整 JSON 导出：记录元数据 + 全部 8 类监控采样数据。 */
    suspend fun exportRecordJson(recordId: Long): String = withContext(Dispatchers.IO) {
        val record = recordDao.getRecord(recordId)
        val root = JSONObject()
        root.put("recordId", recordId)
        record?.let {
            root.put("appName", it.appName)
            root.put("packageName", it.packageName)
            root.put("startedAtMs", it.startedAtMs)
            root.put("endedAtMs", it.endedAtMs ?: JSONObject.NULL)
            root.put("avgSocPowerUw", it.avgSocPowerUw)
            root.put("avgGpuPowerUw", it.avgGpuPowerUw)
            root.put("avgCpuMPowerUw", it.avgCpuMPowerUw)
            root.put("avgCpuLPowerUw", it.avgCpuLPowerUw)
            root.put("avgNspPowerUw", it.avgNspPowerUw)
            root.put("avgFps", it.avgFps?.toDouble() ?: JSONObject.NULL)
        }
        root.put("samples", JSONArray(sampleDao.getSamples(recordId).map { it.toJson() }))
        root.put("clusterFreqs", JSONArray(auxSampleDao.getClusterFreqs(recordId).map {
            JSONObject().put("timestampMs", it.timestampMs).put("clusterName", it.clusterName).put("freqKhz", it.freqKhz)
        }))
        root.put("coreUsages", JSONArray(auxSampleDao.getCoreUsages(recordId).map {
            JSONObject().put("timestampMs", it.timestampMs).put("coreIndex", it.coreIndex).put("usagePercent", it.usagePercent.toDouble())
        }))
        root.put("temps", JSONArray(auxSampleDao.getTemps(recordId).map {
            JSONObject().put("timestampMs", it.timestampMs).put("sensor", it.sensor).put("tempC", it.tempC.toDouble())
        }))
        root.put("devicePowers", JSONArray(auxSampleDao.getDevicePowers(recordId).map {
            JSONObject().put("timestampMs", it.timestampMs).put("powerUw", it.powerUw)
        }))
        root.put("gpus", JSONArray(auxSampleDao.getGpus(recordId).map {
            JSONObject().put("timestampMs", it.timestampMs).put("freqHz", it.freqHz)
                .put("usagePercent", it.usagePercent?.toDouble() ?: JSONObject.NULL)
        }))
        root.put("ddrFreqs", JSONArray(auxSampleDao.getDdrFreqs(recordId).map {
            JSONObject().put("timestampMs", it.timestampMs).put("freqHz", it.freqHz)
        }))
        root.put("fps", JSONArray(auxSampleDao.getFps(recordId).map {
            JSONObject().put("timestampMs", it.timestampMs).put("fps", it.fps.toDouble())
        }))
        root.toString(2)
    }

    /** 完整 CSV 导出：长格式，type 列区分监控项。 */
    suspend fun exportRecordCsv(recordId: Long): String = withContext(Dispatchers.IO) {
        val record = recordDao.getRecord(recordId)
        buildString {
            // 元数据头部注释
            record?.let {
                appendLine("# recordId,$recordId")
                appendLine("# appName,${it.appName.replace(',', ';')}")
                appendLine("# packageName,${it.packageName}")
                appendLine("# startedAtMs,${it.startedAtMs}")
                appendLine("# endedAtMs,${it.endedAtMs ?: ""}")
            }
            appendLine("type,timestampMs,field1,field2,field3,field4")
            sampleDao.getSamples(recordId).forEach {
                appendLine("qpt,${it.timestampMs},${it.zoneName},${it.energyUj},${it.powerUw ?: ""},${it.computedPowerUw ?: ""}")
            }
            auxSampleDao.getClusterFreqs(recordId).forEach {
                appendLine("cluster_freq,${it.timestampMs},${it.clusterName},${it.freqKhz}")
            }
            auxSampleDao.getCoreUsages(recordId).forEach {
                appendLine("core_usage,${it.timestampMs},${it.coreIndex},${it.usagePercent}")
            }
            auxSampleDao.getTemps(recordId).forEach {
                appendLine("temp,${it.timestampMs},${it.sensor},${it.tempC}")
            }
            auxSampleDao.getDevicePowers(recordId).forEach {
                appendLine("device_power,${it.timestampMs},${it.powerUw}")
            }
            auxSampleDao.getGpus(recordId).forEach {
                appendLine("gpu,${it.timestampMs},${it.freqHz},${it.usagePercent ?: ""}")
            }
            auxSampleDao.getDdrFreqs(recordId).forEach {
                appendLine("ddr_freq,${it.timestampMs},${it.freqHz}")
            }
            auxSampleDao.getFps(recordId).forEach {
                appendLine("fps,${it.timestampMs},${it.fps}")
            }
        }
    }

    fun stats(samples: List<SampleEntity>): List<ZoneStats> = samples.toStats()

    private fun ZoneSample.toEntity(recordId: Long) = SampleEntity(
        recordId = recordId,
        zonePath = zonePath,
        zoneName = zoneName,
        timestampMs = timestampMs,
        energyUj = energyUj,
        powerUw = powerUw,
        computedPowerUw = computedPowerUw,
    )

    private fun ClusterFreqSample.toEntity(recordId: Long) = ClusterFreqEntity(
        recordId = recordId,
        timestampMs = timestampMs,
        clusterName = clusterName,
        freqKhz = freqKhz,
    )

    private fun CoreUsageSample.toEntity(recordId: Long) = CoreUsageEntity(
        recordId = recordId,
        timestampMs = timestampMs,
        coreIndex = coreIndex,
        usagePercent = usagePercent,
    )

    private fun TempSample.toEntity(recordId: Long) = TempEntity(
        recordId = recordId,
        timestampMs = timestampMs,
        sensor = sensor,
        tempC = tempC,
    )

    private fun List<SampleEntity>.toStats(): List<ZoneStats> = groupBy { it.zoneName }.mapNotNull { (zone, rows) ->
        val powers = rows.map { it.effectivePowerUw() }
        val peak = rows.maxByOrNull { it.effectivePowerUw() } ?: return@mapNotNull null
        val firstEnergy = rows.minByOrNull { it.timestampMs }?.energyUj ?: 0L
        val lastEnergy = rows.maxByOrNull { it.timestampMs }?.energyUj ?: firstEnergy
        ZoneStats(
            zoneName = zone,
            maxPowerUw = powers.maxOrNull() ?: 0L,
            minPowerUw = powers.minOrNull() ?: 0L,
            avgPowerUw = powers.average().toLong(),
            totalEnergyUj = (lastEnergy - firstEnergy).coerceAtLeast(0L),
            peakAtMs = peak.timestampMs,
        )
    }

    private fun List<ZoneStats>.avgFor(name: String): Long =
        firstOrNull { it.zoneName.equals(name, ignoreCase = true) }?.avgPowerUw ?: 0L

    private fun SampleEntity.effectivePowerUw(): Long = powerUw ?: computedPowerUw ?: 0L

    private fun SampleEntity.toJson(): JSONObject = JSONObject()
        .put("timestampMs", timestampMs)
        .put("zoneName", zoneName)
        .put("zonePath", zonePath)
        .put("energyUj", energyUj)
        .put("powerUw", powerUw)
        .put("computedPowerUw", computedPowerUw)
        .put("effectivePowerUw", effectivePowerUw())

    private companion object {
        /** 设备拓扑扫描缓存有效期：能力不会中途变化，5 分钟足够。 */
        const val SCAN_TTL_MS = 5 * 60_000L

        /** 首页/OSD 的 UI 刷新节流。 */
        const val UI_THROTTLE_MS = 500L

        /** 退到后台后的预览周期：保持曲线滚动，同时把开销压到 1 Hz。 */
        const val BACKGROUND_INTERVAL_MS = 1_000L

        /** FPS 采样周期：SurfaceFlinger 呈现帧计数，1 秒一次足够，且成本远低于每 tick 读。 */
        const val FPS_INTERVAL_MS = 1_000L

        /** 详情页目标行数，超过则按 stride 抽样。 */
        const val DETAIL_TARGET_ROWS = 3_000
    }
}
