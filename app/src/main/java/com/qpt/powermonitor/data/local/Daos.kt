package com.qpt.powermonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert
    suspend fun insert(record: RecordEntity): Long

    @Update
    suspend fun update(record: RecordEntity)

    @Query("SELECT * FROM records ORDER BY startedAtMs DESC")
    fun observeRecords(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE id = :id")
    fun observeRecord(id: Long): Flow<RecordEntity?>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getRecord(id: Long): RecordEntity?

    @Query("SELECT * FROM records WHERE endedAtMs IS NULL ORDER BY startedAtMs ASC")
    suspend fun getOpenRecords(): List<RecordEntity>

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM records")
    suspend fun deleteAll()
}

@Dao
interface SampleDao {
    @Insert
    suspend fun insertAll(samples: List<SampleEntity>)

    /** [stride] > 1 时按行号等间隔抽样：详情页图表只需要趋势，不必把几十万行全拉进内存。导出走全量 [getSamples]。 */
    @Query("SELECT * FROM samples WHERE recordId = :recordId AND (:stride <= 1 OR id % :stride = 0) ORDER BY timestampMs ASC")
    fun observeSamples(recordId: Long, stride: Int = 1): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE recordId = :recordId ORDER BY timestampMs ASC")
    suspend fun getSamples(recordId: Long): List<SampleEntity>

    @Query("SELECT COUNT(*) FROM samples WHERE recordId = :recordId")
    suspend fun countSamples(recordId: Long): Int
}

@Dao
interface AuxSampleDao {
    @Insert
    suspend fun insertClusterFreqs(samples: List<ClusterFreqEntity>)

    @Insert
    suspend fun insertCoreUsages(samples: List<CoreUsageEntity>)

    @Insert
    suspend fun insertTemps(samples: List<TempEntity>)

    @Insert
    suspend fun insertDevicePowers(samples: List<DevicePowerEntity>)

    @Insert
    suspend fun insertGpus(samples: List<GpuEntity>)

    @Insert
    suspend fun insertDdrFreqs(samples: List<DdrFreqEntity>)

    @Insert
    suspend fun insertFps(samples: List<FpsEntity>)

    @Query("SELECT * FROM cluster_freq_samples WHERE recordId = :recordId AND (:stride <= 1 OR id % :stride = 0) ORDER BY timestampMs ASC")
    fun observeClusterFreqs(recordId: Long, stride: Int = 1): Flow<List<ClusterFreqEntity>>

    @Query("SELECT * FROM core_usage_samples WHERE recordId = :recordId AND (:stride <= 1 OR id % :stride = 0) ORDER BY timestampMs ASC")
    fun observeCoreUsages(recordId: Long, stride: Int = 1): Flow<List<CoreUsageEntity>>

    @Query("SELECT * FROM temp_samples WHERE recordId = :recordId AND (:stride <= 1 OR id % :stride = 0) ORDER BY timestampMs ASC")
    fun observeTemps(recordId: Long, stride: Int = 1): Flow<List<TempEntity>>

    @Query("SELECT * FROM device_power_samples WHERE recordId = :recordId AND (:stride <= 1 OR id % :stride = 0) ORDER BY timestampMs ASC")
    fun observeDevicePowers(recordId: Long, stride: Int = 1): Flow<List<DevicePowerEntity>>

    @Query("SELECT * FROM gpu_samples WHERE recordId = :recordId AND (:stride <= 1 OR id % :stride = 0) ORDER BY timestampMs ASC")
    fun observeGpus(recordId: Long, stride: Int = 1): Flow<List<GpuEntity>>

    @Query("SELECT * FROM ddr_freq_samples WHERE recordId = :recordId AND (:stride <= 1 OR id % :stride = 0) ORDER BY timestampMs ASC")
    fun observeDdrFreqs(recordId: Long, stride: Int = 1): Flow<List<DdrFreqEntity>>

    @Query("SELECT * FROM fps_samples WHERE recordId = :recordId AND (:stride <= 1 OR id % :stride = 0) ORDER BY timestampMs ASC")
    fun observeFps(recordId: Long, stride: Int = 1): Flow<List<FpsEntity>>

    @Query("SELECT * FROM fps_samples WHERE recordId = :recordId")
    suspend fun getFps(recordId: Long): List<FpsEntity>

    @Query("SELECT * FROM cluster_freq_samples WHERE recordId = :recordId ORDER BY timestampMs ASC")
    suspend fun getClusterFreqs(recordId: Long): List<ClusterFreqEntity>

    @Query("SELECT * FROM core_usage_samples WHERE recordId = :recordId ORDER BY timestampMs ASC")
    suspend fun getCoreUsages(recordId: Long): List<CoreUsageEntity>

    @Query("SELECT * FROM temp_samples WHERE recordId = :recordId ORDER BY timestampMs ASC")
    suspend fun getTemps(recordId: Long): List<TempEntity>

    @Query("SELECT * FROM device_power_samples WHERE recordId = :recordId ORDER BY timestampMs ASC")
    suspend fun getDevicePowers(recordId: Long): List<DevicePowerEntity>

    @Query("SELECT * FROM gpu_samples WHERE recordId = :recordId ORDER BY timestampMs ASC")
    suspend fun getGpus(recordId: Long): List<GpuEntity>

    @Query("SELECT * FROM ddr_freq_samples WHERE recordId = :recordId ORDER BY timestampMs ASC")
    suspend fun getDdrFreqs(recordId: Long): List<DdrFreqEntity>
}
