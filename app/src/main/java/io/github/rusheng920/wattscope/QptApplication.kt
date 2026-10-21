package io.github.rusheng920.wattscope

import android.app.Application
import io.github.rusheng920.wattscope.core.auxiliary.AuxReader
import io.github.rusheng920.wattscope.core.qpt.QptPowerReader
import io.github.rusheng920.wattscope.core.root.RootManager
import io.github.rusheng920.wattscope.data.local.QptDatabase
import io.github.rusheng920.wattscope.data.repo.MonitorRepository
import io.github.rusheng920.wattscope.data.repo.UpdateRepository

class QptApplication : Application() {
    lateinit var repository: MonitorRepository
        private set

    /** 更新检查：设置页与启动自动检测共用同一实例。 */
    lateinit var updateRepository: UpdateRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = QptDatabase.create(this)
        val rootManager = RootManager()
        repository = MonitorRepository(
            context = this,
            database = database,
            recordDao = database.recordDao(),
            sampleDao = database.sampleDao(),
            auxSampleDao = database.auxSampleDao(),
            qptReader = QptPowerReader(rootManager),
            auxReader = AuxReader(rootManager),
            rootManager = rootManager,
        )
        updateRepository = UpdateRepository(this)
    }
}
