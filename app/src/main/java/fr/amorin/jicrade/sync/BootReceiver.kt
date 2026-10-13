package fr.amorin.jicrade.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.amorin.jicrade.data.local.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: SettingsStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = runBlocking { settings.settings.first() }
        SyncScheduler.applyBackgroundSync(context, prefs.backgroundSync)
        if (prefs.onCallMode) OnCallPollingService.start(context)
    }
}
