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
import io.github.rusheng920.wattscope.domain.AppPermissions
import io.github.rusheng920.wattscope.domain.OsdMetric
import io.github.rusheng920.wattscope.domain.SamplingInterval
import io.github.rusheng920.wattscope.domain.ThemeMode
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

class QptViewModel(private val repository: MonitorRepository) : ViewModel() {
    val state = repository.state
    val records: StateFlow<List<RecordEntity>> = repository.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 外观模式与权限状态。 */
    val themeMode: StateFlow<ThemeMode> = repository.themeModeFlow
    val permissions: StateFlow<AppPermissions> = repository.permissions

    /** 悬浮窗开关状态（Compose 可观察，写入即响应）。 */
    val bubbleEnabled: StateFlow<Boolean> = repository.bubbleEnabledFlow

    /** OSD 悬浮窗开关与显示项。 */
    val osdEnabled: StateFlow<Boolean> = repository.osdEnabledFlow
    val osdMetrics: StateFlow<List<OsdMetric>> = repository.osdMetricsFlow

    /** FPS 数据源：null = 自动跟随前台应用；非空 = 用户手工指定的 SurfaceFlinger 图层。 */
    val fpsLayer: StateFlow<String?> = repository.fpsLayerFlow
    private val _fpsLayers = MutableStateFlow<List<String>>(emptyList())
    val fpsLayers: StateFlow<List<String>> = _fpsLayers.asStateFlow()

    init {
        viewModelScope.launch { repository.refreshDeviceState() }
    }

    fun refresh() = viewModelScope.launch { repository.refreshDeviceState(force = true) }
    fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)
    fun refreshPermissions() = repository.refreshPermissions()
    fun requestRoot() = viewModelScope.launch { repository.requestRootAccess() }
    fun setEnabled(enabled: Boolean) = viewModelScope.launch { repository.setQptEnabled(enabled) }
    fun setInterval(interval: SamplingInterval) = repository.setInterval(interval)
    fun startRecording() = viewModelScope.launch { repository.startRecording() }
    fun stopRecording() = viewModelScope.launch { repository.stopRecording() }
    fun deleteRecord(id: Long) = viewModelScope.launch { repository.deleteRecord(id) }
    fun deleteAll() = viewModelScope.launch { repository.deleteAll() }
    fun setOsdEnabled(enabled: Boolean) { repository.osdEnabled = enabled }
    fun setOsdMetrics(metrics: List<OsdMetric>) = repository.setOsdMetrics(metrics)
    fun setFpsLayer(layer: String?) { repository.fpsLayer = layer }
    fun loadFpsLayers() = viewModelScope.launch { _fpsLayers.value = repository.listFpsLayers() }

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
