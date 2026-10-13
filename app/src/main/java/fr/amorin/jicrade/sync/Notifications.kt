package fr.amorin.jicrade.sync

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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import fr.amorin.jicrade.MainActivity
import fr.amorin.jicrade.R
import fr.amorin.jicrade.data.local.AlertEntity

object Notifications {
    const val CHANNEL_NEW = "alerts_new"
    const val CHANNEL_UPDATE = "alerts_update"
    const val CHANNEL_SERVICE = "oncall_service"
    const val EXTRA_ALERT_ID = "alertId"

    fun createChannels(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NEW, "New alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A new alert was raised for your team"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAttrs)
                setBypassDnd(true)
            }
        )
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

    fun notificationId(alertId: String): Int = alertId.hashCode() and 0x7fffffff

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    fun showNewAlert(context: Context, alert: AlertEntity) {
        val open = PendingIntent.getActivity(
            context, notificationId(alert.id),
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ALERT_ID, alert.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_NEW)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${alert.priority} · #${alert.tinyId} ${alert.message}")
            .setContentText(listOfNotNull(alert.source, alert.description?.lineSequence()?.firstOrNull()).joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.description ?: alert.message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setFullScreenIntent(open, alert.priority == "P1")
            .setAutoCancel(true)
            .addAction(0, "Acknowledge", NotificationActionReceiver.pendingIntent(context, NotificationActionReceiver.ACTION_ACK, alert.id))
            .addAction(0, "Close", NotificationActionReceiver.pendingIntent(context, NotificationActionReceiver.ACTION_CLOSE, alert.id))
            .build()
        post(context, notificationId(alert.id), notification)
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

    fun cancel(context: Context, alertId: String) =
        NotificationManagerCompat.from(context).cancel(notificationId(alertId))
}
