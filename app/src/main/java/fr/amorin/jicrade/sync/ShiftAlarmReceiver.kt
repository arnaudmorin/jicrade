package fr.amorin.jicrade.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShiftAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var polling: PollingController

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                polling.evaluate()
                SyncScheduler.syncNow(context)
            } finally {
                pending.finish()
            }
        }
    }
}
