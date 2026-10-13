package fr.amorin.jicrade.sync

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.amorin.jicrade.data.repo.AlertRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var alerts: AlertRepository

    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getStringExtra(Notifications.EXTRA_ALERT_ID) ?: return
        val action = intent.action ?: return
        Notifications.cancel(context, alertId)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_ACK -> alerts.acknowledge(alertId)
                    ACTION_CLOSE -> alerts.close(alertId)
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACK = "fr.amorin.jicrade.ACK"
        const val ACTION_CLOSE = "fr.amorin.jicrade.CLOSE"

        fun pendingIntent(context: Context, action: String, alertId: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                (action + alertId).hashCode(),
                Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(Notifications.EXTRA_ALERT_ID, alertId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
