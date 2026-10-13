package fr.amorin.jicrade

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import fr.amorin.jicrade.data.local.SettingsStore
import fr.amorin.jicrade.sync.Notifications
import fr.amorin.jicrade.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JicradeApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        CoroutineScope(Dispatchers.IO).launch {
            SyncScheduler.applyBackgroundSync(this@JicradeApp, settings.settings.first().backgroundSync)
        }
    }
}
