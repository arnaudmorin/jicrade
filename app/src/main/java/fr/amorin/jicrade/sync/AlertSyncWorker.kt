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
import fr.amorin.jicrade.data.remote.ApiException
import fr.amorin.jicrade.data.repo.AlertRepository
import java.util.concurrent.TimeUnit

@HiltWorker
class AlertSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val alerts: AlertRepository,
    private val notifier: NewAlertNotifier,
    private val polling: PollingController,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        notifier.notify(alerts.sync())
        polling.evaluate()
        Result.success()
    } catch (e: NotLoggedInException) {
        Result.success()
    } catch (e: ApiException) {
        if (e.isUnauthorized) Result.failure() else Result.retry()
    } catch (e: Exception) {
        Result.retry()
    }
}

object SyncScheduler {
    private const val PERIODIC = "alert-sync-periodic"
    private const val ONCE = "alert-sync-now"

    fun applyBackgroundSync(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<AlertSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<AlertSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, request)
    }
}
