package fr.amorin.jicrade.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.amorin.jicrade.data.local.SettingsStore
import fr.amorin.jicrade.data.repo.SyncResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {
    suspend fun notify(result: SyncResult) {
        val prefs = settings.current()
        result.acked.forEach { Notifications.cancel(context, it.id) }
        if (!prefs.notificationsEnabled) return
        result.newAlerts.forEach { Notifications.showNewAlert(context, it) }
        if (prefs.notifyOnAck) {
            result.acked.forEach { Notifications.showAcked(context, it) }
        }
    }
}
