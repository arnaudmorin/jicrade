package fr.amorin.jicrade.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.local.AlertEntity
import fr.amorin.jicrade.data.local.NotificationMode
import fr.amorin.jicrade.data.local.SettingsStore
import fr.amorin.jicrade.data.repo.AlertRepository
import fr.amorin.jicrade.data.repo.OnCallRepository
import fr.amorin.jicrade.data.repo.SyncResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val onCallRepository: OnCallRepository,
    private val sessionHolder: SessionHolder,
    private val alerts: AlertRepository,
) {
    suspend fun notify(result: SyncResult) {
        val prefs = settings.current()
        result.resolved.forEach { Notifications.cancel(context, it.id) }
        if (prefs.notificationMode == NotificationMode.NEVER) return
        val accountId = sessionHolder.session.value?.accountId ?: return

        val candidates = result.newAlerts.filter { it.priority <= prefs.minPriority }
        val onCall = prefs.notificationMode == NotificationMode.ON_CALL && candidates.isNotEmpty() &&
            runCatching { onCallRepository.amIOnCall() }.getOrDefault(false)
        val soundUri = prefs.alertSoundUri?.let { android.net.Uri.parse(it) }
        NotificationPolicy.select(candidates, prefs, onCall, accountId)
            .map { withDetails(it) }
            .forEach {
                Notifications.showNewAlert(
                    context = context,
                    alert = it,
                    bypassDnd = prefs.overrideDnd,
                    soundUri = soundUri,
                    loop = prefs.loopAlertSound,
                    overrideVolume = prefs.overrideVolume,
                    alertVolume = prefs.alertVolume,
                )
            }

        if (prefs.notifyOnAck) {
            result.resolved.filter { it.isAcked }.forEach { Notifications.showAcked(context, it) }
        }
    }

    // The list endpoint omits description/details for some alerts; the detail endpoint has them.
    private suspend fun withDetails(alert: AlertEntity): AlertEntity =
        if (alert.description != null || alert.extraProperties.isNotEmpty()) alert
        else runCatching { alerts.fetchOne(alert.id) }.getOrDefault(alert)
}
