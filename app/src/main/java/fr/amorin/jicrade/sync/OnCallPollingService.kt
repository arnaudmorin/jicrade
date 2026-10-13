package fr.amorin.jicrade.sync

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import fr.amorin.jicrade.MainActivity
import fr.amorin.jicrade.R
import fr.amorin.jicrade.data.auth.NotLoggedInException
import fr.amorin.jicrade.data.local.PollingMode
import fr.amorin.jicrade.data.local.SettingsStore
import fr.amorin.jicrade.data.remote.ApiException
import fr.amorin.jicrade.data.repo.AlertRepository
import fr.amorin.jicrade.data.repo.OnCallRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class OnCallPollingService : LifecycleService() {
    @Inject lateinit var alerts: AlertRepository
    @Inject lateinit var notifier: NewAlertNotifier
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var onCall: OnCallRepository

    private var loop: Job? = null

    override fun onCreate() {
        super.onCreate()
        _running.value = true
    }

    override fun onDestroy() {
        _running.value = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification("Starting…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (loop == null) loop = lifecycleScope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        while (true) {
            val prefs = settings.settings.first()
            val interval = prefs.pollIntervalSeconds.coerceIn(15, 600)
            val status = try {
                val result = alerts.sync()
                notifier.notify(result)
                if (prefs.pollingMode == PollingMode.AUTO && !onCall.amIOnCall()) {
                    stopSelf(); return
                }
                "Last check ${LocalTime.now().format(TIME)} · ${result.total} alerts"
            } catch (e: NotLoggedInException) {
                stopSelf(); return
            } catch (e: ApiException) {
                if (e.isUnauthorized) { stopSelf(); return }
                "Error: ${e.message}"
            } catch (e: Exception) {
                "Error: ${e.message ?: e.javaClass.simpleName}"
            }
            Notifications.post(this, NOTIFICATION_ID, buildNotification("$status · every ${interval}s"))
            delay(interval * 1000L)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OnCallPollingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("On-call mode active")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "fr.amorin.jicrade.STOP_POLLING"
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, OnCallPollingService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, OnCallPollingService::class.java))
    }
}
