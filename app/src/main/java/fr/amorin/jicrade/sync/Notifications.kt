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

import android.media.AudioManager
import android.net.Uri
import kotlin.math.roundToInt

object Notifications {
    const val CHANNEL_NEW_BASE = "alerts_new_v2"
    const val CHANNEL_NEW_QUIET_BASE = "alerts_new_quiet_v2"
    const val CHANNEL_UPDATE = "alerts_update"
    const val CHANNEL_SERVICE = "oncall_service"
    const val EXTRA_ALERT_ID = "alertId"
    const val EXTRA_EVALUATE_POLLING = "evaluatePolling"
    const val SHIFT_STARTED_ID = 2

    fun alertChannelId(soundUri: Uri?, bypassDnd: Boolean): String {
        val base = if (bypassDnd) CHANNEL_NEW_BASE else CHANNEL_NEW_QUIET_BASE
        val suffix = soundUri?.toString()?.hashCode()?.let { "_$it" } ?: ""
        return "$base$suffix"
    }

    fun ringtoneTitle(context: Context, uriString: String?): String {
        if (uriString.isNullOrBlank()) {
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            return runCatching {
                RingtoneManager.getRingtone(context, defaultUri)?.getTitle(context)
            }.getOrNull() ?: "Default alarm"
        }
        return runCatching {
            val uri = Uri.parse(uriString)
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull() ?: "Custom sound"
    }

    fun ensureAlertChannels(context: Context, soundUri: Uri? = null) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        runCatching {
            nm.deleteNotificationChannel("alerts_new")
            nm.deleteNotificationChannel("alerts_new_quiet")
        }

        val sound = soundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channelDnd = NotificationChannel(
            alertChannelId(soundUri, bypassDnd = true),
            "New alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "New incident alerts for your team"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            setSound(sound, alarmAttrs)
            setBypassDnd(true)
        }

        val channelQuiet = NotificationChannel(
            alertChannelId(soundUri, bypassDnd = false),
            "New alerts (respects Do Not Disturb)",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "New incident alerts for your team (silenced by Do Not Disturb)"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            setSound(sound, alarmAttrs)
            setBypassDnd(false)
        }

        nm.createNotificationChannel(channelDnd)
        nm.createNotificationChannel(channelQuiet)
    }

    fun createChannels(context: Context, soundUri: Uri? = null) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        ensureAlertChannels(context, soundUri)
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

    fun showNewAlert(
        context: Context,
        alert: AlertEntity,
        bypassDnd: Boolean,
        soundUri: Uri? = null,
        loop: Boolean = true,
        overrideVolume: Boolean = false,
        alertVolume: Int = 100,
    ) {
        ensureAlertChannels(context, soundUri)
        val channelId = alertChannelId(soundUri, bypassDnd)

        if (overrideVolume) {
            val audioManager = context.getSystemService<AudioManager>()
            if (audioManager != null) {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val target = (max * (alertVolume.coerceIn(10, 100) / 100f)).roundToInt().coerceIn(1, max)
                runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }
            }
        }

        val meta = listOfNotNull(alert.priority, "#${alert.tinyId}", alert.source).joinToString(" · ")
        val body = alert.description?.takeIf { it.isNotBlank() }
            ?: alert.extraProperties.entries.take(8).joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { null }
        val open = PendingIntent.getActivity(
            context, notificationId(alert.id),
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ALERT_ID, alert.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.message.ifBlank { alert.entity ?: alert.alias ?: "Alert #${alert.tinyId}" })
            .setContentText(meta)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body ?: meta).setSummaryText(if (body != null) meta else null))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setFullScreenIntent(open, alert.priority == "P1")
            .setAutoCancel(true)
            .addAction(0, "Acknowledge", NotificationActionReceiver.pendingIntent(context, NotificationActionReceiver.ACTION_ACK, alert.id))
            .addAction(0, "Close", NotificationActionReceiver.pendingIntent(context, NotificationActionReceiver.ACTION_CLOSE, alert.id))
            .build()

        if (loop) {
            notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        }

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
