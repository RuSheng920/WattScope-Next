package io.github.rusheng920.wattscope.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val packageName: String,
    val iconPackage: String?,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val avgSocPowerUw: Long = 0,
    val avgGpuPowerUw: Long = 0,
    val avgCpuMPowerUw: Long = 0,
    val avgCpuLPowerUw: Long = 0,
    val avgNspPowerUw: Long = 0,
    val avgFps: Float? = null,
    val avgSocTempC: Float? = null,
    /** 用户自定义名称；null 表示沿用 appName。 */
    val title: String? = null,
)

@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recordId"), Index("zoneName"), Index("timestampMs")],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val zonePath: String,
    val zoneName: String,
    val timestampMs: Long,
    val energyUj: Long,
    val powerUw: Long?,
    val computedPowerUw: Long?,
)

@Entity(
    tableName = "cluster_freq_samples",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recordId"), Index("clusterName"), Index("timestampMs")],
)
data class ClusterFreqEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val timestampMs: Long,
    val clusterName: String,
    val freqKhz: Long,
)

@Entity(
    tableName = "core_usage_samples",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recordId"), Index("coreIndex"), Index("timestampMs")],
)
data class CoreUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val timestampMs: Long,
    val coreIndex: Int,
    val usagePercent: Float,
)

@Entity(
    tableName = "temp_samples",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recordId"), Index("sensor"), Index("timestampMs")],
)
data class TempEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val timestampMs: Long,
    val sensor: String,
    val tempC: Float,
)

@Entity(
    tableName = "device_power_samples",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recordId"), Index("timestampMs")],
)
data class DevicePowerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val timestampMs: Long,
    val powerUw: Long,
)

@Entity(
    tableName = "gpu_samples",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recordId"), Index("timestampMs")],
)
data class GpuEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val timestampMs: Long,
    val freqHz: Long,
    val usagePercent: Float?,
)

@Entity(
    tableName = "ddr_freq_samples",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recordId"), Index("timestampMs")],
)
data class DdrFreqEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val timestampMs: Long,
    val freqHz: Long,
)

@Entity(
    tableName = "fps_samples",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recordId"), Index("timestampMs")],
)
data class FpsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val timestampMs: Long,
    val fps: Float,
)

/**
 * 记录期间的前台应用切换事件（时间轴数据）。
 *
 * 只在记录态采集：采样循环每 2 秒判定一次前台包名，变化时落一条。
 * 开着「离开应用自动停止」时通常只会有一条（离开即时结束记录），
 * 关掉自动停止才能看到完整的切换序列。
 */
@Entity(
    tableName = "app_switch_events",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recordId"), Index("timestampMs")],
)
data class AppSwitchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val timestampMs: Long,
    val packageName: String,
    val appName: String,
)
