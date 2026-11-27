package fr.amorin.jicrade.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

enum class NotificationMode { ON_CALL, ALWAYS, NEVER }

enum class PollingMode { AUTO, ON, OFF }

data class Settings(
    val pollIntervalSeconds: Int = 60,
    val pollingMode: PollingMode = PollingMode.AUTO,
    val notificationMode: NotificationMode = NotificationMode.ON_CALL,
    val minPriority: String = "P3",
    val notifyOnAck: Boolean = false,
    val backgroundSync: Boolean = true,
    val lastSyncAt: Long = 0L,
    val horizonMonths: Int = 12,
    val calendarSync: Boolean = false,
    val calendarSyncedAt: Long = 0L,
    val calendarSyncedCount: Int = 0,
    val calendarSyncError: String? = null,
    val alertSoundUri: String? = null,
    val loopAlertSound: Boolean = true,
    val overrideVolume: Boolean = false,
    val alertVolume: Int = 100,
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val pollInterval = intPreferencesKey("poll_interval_seconds")
    private val pollingMode = stringPreferencesKey("polling_mode")
    private val notificationMode = stringPreferencesKey("notification_mode")
    private val minPriority = stringPreferencesKey("min_priority")
    private val notifyOnAck = booleanPreferencesKey("notify_on_ack")
    private val backgroundSync = booleanPreferencesKey("background_sync")
    private val lastSyncAt = longPreferencesKey("last_sync_at")
    private val horizonMonths = intPreferencesKey("horizon_months")
    private val calendarSync = booleanPreferencesKey("calendar_sync")
    private val calendarSyncedAt = longPreferencesKey("calendar_synced_at")
    private val calendarSyncedCount = intPreferencesKey("calendar_synced_count")
    private val calendarSyncError = stringPreferencesKey("calendar_sync_error")
    private val alertSoundUri = stringPreferencesKey("alert_sound_uri")
    private val loopAlertSound = booleanPreferencesKey("loop_alert_sound")
    private val overrideVolume = booleanPreferencesKey("override_volume")
    private val alertVolume = intPreferencesKey("alert_volume")
    private val legacyOnCallMode = booleanPreferencesKey("on_call_mode")
    private val legacyNotificationsEnabled = booleanPreferencesKey("notifications_enabled")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            pollIntervalSeconds = p[pollInterval] ?: 60,
            pollingMode = p[pollingMode]?.let { runCatching { PollingMode.valueOf(it) }.getOrNull() }
                ?: if (p[legacyOnCallMode] == true) PollingMode.ON else PollingMode.AUTO,
            notificationMode = p[notificationMode]?.let { runCatching { NotificationMode.valueOf(it) }.getOrNull() }
                ?: if (p[legacyNotificationsEnabled] == false) NotificationMode.NEVER else NotificationMode.ON_CALL,
            minPriority = p[minPriority] ?: "P3",
            notifyOnAck = p[notifyOnAck] ?: false,
            backgroundSync = p[backgroundSync] ?: true,
            lastSyncAt = p[lastSyncAt] ?: 0L,
            horizonMonths = p[horizonMonths] ?: 12,
            calendarSync = p[calendarSync] ?: false,
            calendarSyncedAt = p[calendarSyncedAt] ?: 0L,
            calendarSyncedCount = p[calendarSyncedCount] ?: 0,
            calendarSyncError = p[calendarSyncError],
            alertSoundUri = p[alertSoundUri],
            loopAlertSound = p[loopAlertSound] ?: true,
            overrideVolume = p[overrideVolume] ?: false,
            alertVolume = p[alertVolume] ?: 100,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setPollInterval(seconds: Int) = context.dataStore.edit { it[pollInterval] = seconds }
    suspend fun setPollingMode(mode: PollingMode) = context.dataStore.edit { it[pollingMode] = mode.name }
    suspend fun setNotificationMode(mode: NotificationMode) = context.dataStore.edit { it[notificationMode] = mode.name }
    suspend fun setMinPriority(priority: String) = context.dataStore.edit { it[minPriority] = priority }
    suspend fun setNotifyOnAck(enabled: Boolean) = context.dataStore.edit { it[notifyOnAck] = enabled }
    suspend fun setBackgroundSync(enabled: Boolean) = context.dataStore.edit { it[backgroundSync] = enabled }
    suspend fun setLastSyncAt(epochMillis: Long) = context.dataStore.edit { it[lastSyncAt] = epochMillis }
    suspend fun setHorizonMonths(months: Int) = context.dataStore.edit { it[horizonMonths] = months }
    suspend fun setCalendarSync(enabled: Boolean) = context.dataStore.edit { it[calendarSync] = enabled }
    suspend fun setCalendarSynced(at: Long, count: Int) = context.dataStore.edit {
        it[calendarSyncedAt] = at
        it[calendarSyncedCount] = count
        it.remove(calendarSyncError)
    }
    suspend fun setCalendarSyncError(message: String) = context.dataStore.edit { it[calendarSyncError] = message }
    suspend fun setAlertSoundUri(uri: String?) = context.dataStore.edit {
        if (uri != null) it[alertSoundUri] = uri else it.remove(alertSoundUri)
    }
    suspend fun setLoopAlertSound(enabled: Boolean) = context.dataStore.edit { it[loopAlertSound] = enabled }
    suspend fun setOverrideVolume(enabled: Boolean) = context.dataStore.edit { it[overrideVolume] = enabled }
    suspend fun setAlertVolume(percent: Int) = context.dataStore.edit { it[alertVolume] = percent.coerceIn(10, 100) }
    suspend fun clear() = context.dataStore.edit { it.clear() }
}
