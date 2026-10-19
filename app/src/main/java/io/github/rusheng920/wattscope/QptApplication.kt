package io.github.rusheng920.wattscope

import android.app.Application
import io.github.rusheng920.wattscope.core.auxiliary.AuxReader
import io.github.rusheng920.wattscope.core.qpt.QptPowerReader
import io.github.rusheng920.wattscope.core.root.RootManager
import io.github.rusheng920.wattscope.data.local.QptDatabase
import io.github.rusheng920.wattscope.data.repo.MonitorRepository

class QptApplication : Application() {
    lateinit var repository: MonitorRepository
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
    }
}
