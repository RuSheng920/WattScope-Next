package io.github.rusheng920.wattscope.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rusheng920.wattscope.data.local.ClusterFreqEntity
import io.github.rusheng920.wattscope.data.local.CoreUsageEntity
import io.github.rusheng920.wattscope.data.local.DdrFreqEntity
import io.github.rusheng920.wattscope.data.local.DevicePowerEntity
import io.github.rusheng920.wattscope.data.local.FpsEntity
import io.github.rusheng920.wattscope.data.local.GpuEntity
import io.github.rusheng920.wattscope.data.local.RecordEntity
import io.github.rusheng920.wattscope.data.local.SampleEntity
import io.github.rusheng920.wattscope.data.local.TempEntity
import io.github.rusheng920.wattscope.data.repo.MonitorRepository
import io.github.rusheng920.wattscope.data.repo.UpdateRepository
import io.github.rusheng920.wattscope.domain.AppPermissions
import io.github.rusheng920.wattscope.domain.BubbleSize
import io.github.rusheng920.wattscope.domain.DetailChartSection
import io.github.rusheng920.wattscope.domain.GlassIntensity
import io.github.rusheng920.wattscope.domain.OsdMetric
import io.github.rusheng920.wattscope.domain.SamplingInterval
import io.github.rusheng920.wattscope.domain.ThemeMode
import io.github.rusheng920.wattscope.domain.UpdateChannel
import io.github.rusheng920.wattscope.domain.UpdateState
import io.github.rusheng920.wattscope.core.fps.LayerInfo
import io.github.rusheng920.wattscope.core.update.ReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QptViewModel(
    private val repository: MonitorRepository,
    private val updateRepository: UpdateRepository,
) : ViewModel() {
    val state = repository.state
    val records: StateFlow<List<RecordEntity>> = repository.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 外观模式与权限状态。 */
    val themeMode: StateFlow<ThemeMode> = repository.themeModeFlow
    val permissions: StateFlow<AppPermissions> = repository.permissions

    /** 悬浮玻璃底栏与玻璃浓度。 */
    val glassBottomBar: StateFlow<Boolean> = repository.glassBottomBarFlow
    val glassIntensity: StateFlow<GlassIntensity> = repository.glassIntensityFlow
    val glassPauseRecording: StateFlow<Boolean> = repository.glassPauseRecordingFlow

    /** 首次安装引导是否完成。 */
    val onboardingDone: StateFlow<Boolean> = repository.onboardingDoneFlow

    /** 双电芯机型补偿开关。 */
    val dualCellMode: StateFlow<Boolean> = repository.dualCellModeFlow

    /** 悬浮窗开关状态（Compose 可观察，写入即响应）。 */
    val bubbleEnabled: StateFlow<Boolean> = repository.bubbleEnabledFlow

    /** 悬浮球尺寸档位。 */
    val bubbleSize: StateFlow<BubbleSize> = repository.bubbleSizeFlow

    /** 悬浮球松手后是否吸附到屏幕边缘（默认关闭，可自由放置）。 */
    val bubbleSnapToEdge: StateFlow<Boolean> = repository.bubbleSnapToEdgeFlow

    /** OSD 悬浮窗开关与显示项。 */
    val osdEnabled: StateFlow<Boolean> = repository.osdEnabledFlow
    val osdMetrics: StateFlow<List<OsdMetric>> = repository.osdMetricsFlow

    /** FPS 数据源：null = 自动跟随前台应用；非空 = 用户手工指定的 SurfaceFlinger 图层。 */
    val fpsLayer: StateFlow<String?> = repository.fpsLayerFlow
    private val _fpsLayers = MutableStateFlow<List<LayerInfo>>(emptyList())

    /** 扫描结果：按「属于前台应用 → 图层类型 → 名称长度」排序，带实测帧率。 */
    val fpsLayers: StateFlow<List<LayerInfo>> = _fpsLayers.asStateFlow()

    /** 扫描中：置灰按钮，避免用户连点触发十几次 dumpsys。 */
    private val _fpsScanning = MutableStateFlow(false)
    val fpsScanning: StateFlow<Boolean> = _fpsScanning.asStateFlow()

    /** 自动模式当前实际选中的图层，用于让用户知道「自动」到底选了谁。 */
    private val _autoFpsLayer = MutableStateFlow<String?>(null)
    val autoFpsLayer: StateFlow<String?> = _autoFpsLayer.asStateFlow()

    // ---------- 更新 ----------

    val updateState: StateFlow<UpdateState> = updateRepository.state
    val pendingUpdate: StateFlow<ReleaseInfo?> = updateRepository.pending
    val autoUpdatePrompt: StateFlow<Boolean> = updateRepository.autoPrompt
    val autoUpdateEnabled: StateFlow<Boolean> = updateRepository.autoCheckFlow
    val updateChannel: StateFlow<UpdateChannel> = updateRepository.channelFlow
    val lastUpdateCheckMs: StateFlow<Long> = updateRepository.lastCheckMs

    init {
        viewModelScope.launch { repository.refreshDeviceState() }
    }

    fun refresh() = viewModelScope.launch { repository.refreshDeviceState(force = true) }
    fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)
    fun setGlassBottomBar(enabled: Boolean) = repository.setGlassBottomBar(enabled)
    fun setGlassIntensity(intensity: GlassIntensity) = repository.setGlassIntensity(intensity)
    fun setGlassPauseRecording(paused: Boolean) = repository.setGlassPauseRecording(paused)
    fun setDualCellMode(enabled: Boolean) = repository.setDualCellMode(enabled)
    fun refreshPermissions() = repository.refreshPermissions()

    /** 申请 root：授权成功后立即重扫设备能力，不需要重启应用。 */
    fun requestRoot() = viewModelScope.launch {
        if (repository.requestRootAccess()) repository.refreshDeviceState(force = true)
    }

    /** 完成首次引导；[probe] = false 表示「稍后设置」，此时不弹 root 授权框。 */
    fun completeOnboarding(probe: Boolean) = viewModelScope.launch { repository.completeOnboarding(probe) }
    fun setEnabled(enabled: Boolean) = viewModelScope.launch { repository.setQptEnabled(enabled) }
    fun setInterval(interval: SamplingInterval) = repository.setInterval(interval)
    fun startRecording() = viewModelScope.launch { repository.startRecording() }
    fun stopRecording() = viewModelScope.launch { repository.stopRecording() }
    fun deleteRecord(id: Long) = viewModelScope.launch { repository.deleteRecord(id) }
    fun deleteAll() = viewModelScope.launch { repository.deleteAll() }
    fun setOsdEnabled(enabled: Boolean) { repository.osdEnabled = enabled }
    fun setBubbleSize(size: BubbleSize) { repository.bubbleSize = size }
    fun setBubbleSnapToEdge(enabled: Boolean) { repository.bubbleSnapToEdge = enabled }
    fun setOsdMetrics(metrics: List<OsdMetric>) = repository.setOsdMetrics(metrics)

    /** 详情页曲线区块顺序（全局，所有记录共用）。 */
    val detailChartOrder: StateFlow<List<DetailChartSection>> = repository.detailChartOrderFlow
    fun setDetailChartOrder(order: List<DetailChartSection>) = repository.setDetailChartOrder(order)
    fun setFpsLayer(layer: String?) { repository.fpsLayer = layer }

    /** 扫描 SurfaceFlinger 图层并实测帧率。单次点击可能耗时 2~5 秒（每个候选一次 dumpsys）。 */
    fun loadFpsLayers() = viewModelScope.launch {
        if (_fpsScanning.value) return@launch
        _fpsScanning.value = true
        try {
            _fpsLayers.value = repository.scanFpsLayers()
            _autoFpsLayer.value = repository.autoFpsPickedLayer()
        } finally {
            _fpsScanning.value = false
        }
    }

    /** 手动检查更新：与「自动检查」开关无关，随时可点。 */
    fun checkUpdate() = updateRepository.check(manual = true)
    fun setAutoUpdateEnabled(enabled: Boolean) = updateRepository.setAutoCheck(enabled)
    fun setUpdateChannel(channel: UpdateChannel) {
        updateRepository.channel = channel
    }
    fun downloadUpdate() = updateRepository.downloadAndInstall()
    fun dismissUpdatePrompt() = updateRepository.dismissPrompt()

    /** 生成完整导出内容（JSON / CSV），供 SAF 保存到用户选择的位置。 */
    suspend fun exportRecord(recordId: Long, asJson: Boolean): String =
        if (asJson) repository.exportRecordJson(recordId) else repository.exportRecordCsv(recordId)
    fun samples(recordId: Long): StateFlow<List<SampleEntity>> =
        repository.observeSamples(recordId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 详情页数据流：先算降采样步长（把超长记录压到约 3000 行），再订阅 8 张表的 Flow。
     * 聚合放在 Default 线程，避免录制中打开详情页时主线程被 groupBy/average 打满。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun stats(recordId: Long): StateFlow<DetailUiState> =
        flow { emit(repository.detailStride(recordId)) }
            .flatMapLatest { stride -> detailFlow(recordId, stride) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private fun detailFlow(recordId: Long, stride: Int): Flow<DetailUiState> {
        val auxA = combine(
            repository.observeClusterFreqs(recordId, stride),
            repository.observeCoreUsages(recordId, stride),
            repository.observeTemps(recordId, stride),
            repository.observeDevicePowers(recordId, stride),
        ) { freqs, cores, temps, powers ->
            AuxDataA(freqs, cores, temps, powers)
        }
        val auxB = combine(
            repository.observeGpus(recordId, stride),
            repository.observeDdrFreqs(recordId, stride),
            repository.observeFps(recordId, stride),
        ) { gpus, ddrFreqs, fpsList ->
            AuxDataB(gpus, ddrFreqs, fpsList)
        }
        return combine(
            repository.observeRecord(recordId),
            repository.observeSamples(recordId, stride),
            auxA,
            auxB,
        ) { record, samples, a, b ->
            DetailUiState(
                record = record,
                samples = samples,
                stats = repository.stats(samples),
                avgDevicePowerUw = a.powers.map { it.powerUw }.average().toLong().takeIf { a.powers.isNotEmpty() } ?: 0L,
                clusterAvgFreqs = a.freqs.groupBy { it.clusterName }
                    .mapValues { (_, v) -> v.map { it.freqKhz }.average().toLong() }
                    .toList().sortedBy { it.first },
                coreAvgUsages = a.cores.groupBy { it.coreIndex }
                    .mapValues { (_, v) -> v.map { it.usagePercent }.average().toFloat() }
                    .toList().sortedBy { it.first },
                sensorAvgTemps = a.temps.groupBy { it.sensor }
                    .mapValues { (_, v) -> v.map { it.tempC }.average().toFloat() }
                    .toList(),
                avgGpuFreqHz = b.gpus.map { it.freqHz }.average().toLong().takeIf { b.gpus.isNotEmpty() } ?: 0L,
                avgGpuUsage = b.gpus.mapNotNull { it.usagePercent }.average().toFloat().takeIf { b.gpus.any { it.usagePercent != null } } ?: 0f,
                avgDdrFreqHz = b.ddrFreqs.map { it.freqHz }.average().toLong().takeIf { b.ddrFreqs.isNotEmpty() } ?: 0L,
                avgFps = b.fps.map { it.fps }.average().toFloat().takeIf { b.fps.isNotEmpty() } ?: 0f,
                clusterFreqSamples = a.freqs,
                coreUsageSamples = a.cores,
                tempSamples = a.temps,
                devicePowerSamples = a.powers,
                gpuSamples = b.gpus,
                ddrFreqSamples = b.ddrFreqs,
                fpsSamples = b.fps,
            )
        }
            // 录制进行时其他记录的写入会触发 Room 表级失效、Flow 重发射；
            // 数据未变化时不向下游发射，避免详情页无谓重组与图表手势抖动
            .distinctUntilChanged()
    }

    private data class AuxDataA(
        val freqs: List<ClusterFreqEntity> = emptyList(),
        val cores: List<CoreUsageEntity> = emptyList(),
        val temps: List<TempEntity> = emptyList(),
        val powers: List<DevicePowerEntity> = emptyList(),
    )

    private data class AuxDataB(
        val gpus: List<GpuEntity> = emptyList(),
        val ddrFreqs: List<DdrFreqEntity> = emptyList(),
        val fps: List<FpsEntity> = emptyList(),
    )
}
