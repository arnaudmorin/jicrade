package fr.amorin.jicrade.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import fr.amorin.jicrade.MainActivity
import fr.amorin.jicrade.R
import fr.amorin.jicrade.data.local.AlertEntity
import fr.amorin.jicrade.data.local.Settings

object Notifications {
    private const val CHANNEL_NEW_PREFIX = "alerts_new"
    const val CHANNEL_UPDATE = "alerts_update"
    const val CHANNEL_SERVICE = "oncall_service"
    const val EXTRA_ALERT_ID = "alertId"
    const val EXTRA_EVALUATE_POLLING = "evaluatePolling"
    const val SHIFT_STARTED_ID = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATE, "Alert updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts acknowledged by someone else"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "On-call mode", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while fast polling is active"
                setShowBadge(false)
            }
        )
    }

    private fun alertChannelId(soundUri: String?, bypassDnd: Boolean): String {
        val base = if (bypassDnd) "alerts_new_v2" else "alerts_new_quiet_v2"
        return if (soundUri == null) base else "${base}_${soundUri.hashCode()}"
    }

    fun createAlertChannels(context: Context, soundUri: String?) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val current = setOf(alertChannelId(soundUri, true), alertChannelId(soundUri, false))
        nm.notificationChannels.map { it.id }
            .filter { it.startsWith(CHANNEL_NEW_PREFIX) && it !in current }
            .forEach { nm.deleteNotificationChannel(it) }
        val sound = soundUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        nm.createNotificationChannel(
            alertChannel(alertChannelId(soundUri, true), "New alerts", "A new alert was raised for your team", sound, AudioAttributes.USAGE_ALARM, bypassDnd = true)
        )
        nm.createNotificationChannel(
            alertChannel(alertChannelId(soundUri, false), "New alerts (respects Do Not Disturb)", "Same as New alerts but silenced by Do Not Disturb", sound, AudioAttributes.USAGE_NOTIFICATION_EVENT, bypassDnd = false)
        )
    }

    private fun alertChannel(id: String, name: String, desc: String, sound: Uri, usage: Int, bypassDnd: Boolean) =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = desc
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            setSound(sound, AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setBypassDnd(bypassDnd)
        }

    fun notificationId(alertId: String): Int = alertId.hashCode() and 0x7fffffff

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, id: Int, notification: Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    fun showNewAlert(context: Context, alert: AlertEntity, prefs: Settings, test: Boolean = false) {
        createAlertChannels(context, prefs.alertSoundUri)
        val meta = listOfNotNull(alert.priority, "#${alert.tinyId}", alert.source).joinToString(" · ")
        val body = alert.description?.takeIf { it.isNotBlank() }
            ?: alert.extraProperties.entries.take(8).joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { null }
        val open = PendingIntent.getActivity(
            context, notificationId(alert.id),
            Intent(context, MainActivity::class.java).apply {
                if (!test) putExtra(EXTRA_ALERT_ID, alert.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, alertChannelId(prefs.alertSoundUri, prefs.overrideDnd))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.message.ifBlank { alert.entity ?: alert.alias ?: "Alert #${alert.tinyId}" })
            .setContentText(meta)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body ?: meta).setSummaryText(if (body != null) meta else null))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setFullScreenIntent(open, alert.priority == "P1")
            .setAutoCancel(true)
            .setDeleteIntent(NotificationActionReceiver.pendingIntent(context, NotificationActionReceiver.ACTION_DISMISS, alert.id))
        if (!test) {
            builder
                .addAction(0, "Acknowledge", NotificationActionReceiver.pendingIntent(context, NotificationActionReceiver.ACTION_ACK, alert.id))
                .addAction(0, "Close", NotificationActionReceiver.pendingIntent(context, NotificationActionReceiver.ACTION_CLOSE, alert.id))
        }
        val notification = builder.build()
        if (prefs.overrideVolume && prefs.overrideDnd) AlarmVolume.raise(context, prefs.alertVolume)
        if (prefs.loopAlertSound) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        post(context, notificationId(alert.id), notification)
    }

    fun showTestAlert(context: Context, prefs: Settings) {
        val now = System.currentTimeMillis()
        val alert = AlertEntity(
            id = "test-alert", tinyId = "0", message = "Test alert", description = "Notifications are working",
            status = "open", priority = "P1", acknowledged = false, count = 1, owner = null, source = "Jicrade",
            entity = null, alias = null, tags = emptyList(), extraProperties = emptyMap(), responders = emptyList(),
            integrationName = null, createdAt = now, updatedAt = now, lastOccurredAt = null, ackTime = null,
            closeTime = null, snoozed = false, snoozedUntil = null, seen = false,
        )
        showNewAlert(context, alert, prefs, test = true)
    }

    fun showAcked(context: Context, alert: AlertEntity) {
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("#${alert.tinyId} acknowledged by ${alert.owner ?: "someone"}")
            .setContentText(alert.message)
            .setAutoCancel(true)
            .build()
        post(context, notificationId(alert.id), notification)
    }

    fun showShiftStarted(context: Context) {
        val open = PendingIntent.getActivity(
            context, SHIFT_STARTED_ID,
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_EVALUATE_POLLING, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your on-call shift started")
            .setContentText("Tap to start fast polling (Android blocked starting it in the background)")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        post(context, SHIFT_STARTED_ID, notification)
    }

    fun cancelShiftStarted(context: Context) = NotificationManagerCompat.from(context).cancel(SHIFT_STARTED_ID)

    fun cancel(context: Context, alertId: String) =
        NotificationManagerCompat.from(context).cancel(notificationId(alertId))
}
