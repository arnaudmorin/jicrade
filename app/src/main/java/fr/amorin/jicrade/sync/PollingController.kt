package fr.amorin.jicrade.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.amorin.jicrade.data.local.PollingMode
import fr.amorin.jicrade.data.local.SettingsStore
import fr.amorin.jicrade.data.repo.OnCallRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PollingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val onCall: OnCallRepository,
) {
    private val mutex = Mutex()

    suspend fun evaluate() = mutex.withLock {
        val prefs = settings.current()
        val onCallNow = prefs.pollingMode == PollingMode.AUTO && runCatching { onCall.amIOnCall() }.getOrDefault(false)
        val wanted = NotificationPolicy.pollingWanted(prefs.pollingMode, onCallNow)
        when {
            wanted && !OnCallPollingService.running.value -> start()
            !wanted -> {
                OnCallPollingService.stop(context)
                Notifications.cancelShiftStarted(context)
            }
        }
        scheduleShiftAlarm()
    }

    private fun start() {
        try {
            OnCallPollingService.start(context)
            Notifications.cancelShiftStarted(context)
        } catch (e: IllegalStateException) {
            // Android 12+ refuses foreground-service starts from the background; ask the user to tap.
            Notifications.showShiftStarted(context)
        }
    }

    private suspend fun scheduleShiftAlarm() {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, ShiftAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
        val boundary = onCall.nextBoundary() ?: return
        val triggerAt = boundary + 30_000L
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }
}
