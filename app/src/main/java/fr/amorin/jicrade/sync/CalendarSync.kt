package fr.amorin.jicrade.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.amorin.jicrade.data.auth.NotLoggedInException
import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import fr.amorin.jicrade.data.local.ScheduleDao
import fr.amorin.jicrade.data.local.SettingsStore
import fr.amorin.jicrade.data.remote.ApiException
import fr.amorin.jicrade.data.remote.toUserMessage
import fr.amorin.jicrade.data.repo.OnCallRepository
import fr.amorin.jicrade.data.repo.mergeShifts
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSyncer @Inject constructor(
    private val settings: SettingsStore,
    private val onCall: OnCallRepository,
    private val scheduleDao: ScheduleDao,
    private val calendar: PhoneCalendar,
) {
    suspend fun sync() {
        if (!ready()) return
        syncFrom(mergeShifts(onCall.myShifts()))
    }

    suspend fun syncFrom(shifts: List<OnCallPeriodEntity>) {
        if (!ready()) return
        try {
            val names = scheduleDao.schedules().associate { it.id to it.name }
            val count = calendar.apply(desiredEvents(shifts, names))
            settings.setCalendarSynced(System.currentTimeMillis(), count)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            settings.setCalendarSyncError(e.toUserMessage())
            throw e
        }
    }

    suspend fun syncCached() {
        if (!ready()) return
        syncFrom(mergeShifts(onCall.cachedShifts()))
    }

    suspend fun disable() {
        settings.setCalendarSync(false)
        runCatching { calendar.deleteCalendar() }
    }

    private suspend fun ready(): Boolean {
        if (!settings.current().calendarSync) return false
        if (!calendar.hasPermission()) {
            settings.setCalendarSyncError("Calendar permission missing")
            return false
        }
        return true
    }
}

@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncer: CalendarSyncer,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        syncer.sync()
        Result.success()
    } catch (e: NotLoggedInException) {
        Result.success()
    } catch (e: ApiException) {
        if (e.isUnauthorized) Result.failure() else Result.retry()
    } catch (e: Exception) {
        Result.retry()
    }
}

object CalendarSyncScheduler {
    private const val PERIODIC = "calendar-sync-periodic"
    private const val ONCE = "calendar-sync-now"
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun apply(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(6, TimeUnit.HOURS).setConstraints(network).build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, request)
    }
}
