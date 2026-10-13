package fr.amorin.jicrade.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

data class Settings(
    val pollIntervalSeconds: Int = 60,
    val onCallMode: Boolean = false,
    val lastSyncAt: Long = 0L,
    val notifyOnAck: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val backgroundSync: Boolean = true,
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val pollInterval = intPreferencesKey("poll_interval_seconds")
    private val onCallMode = booleanPreferencesKey("on_call_mode")
    private val lastSyncAt = longPreferencesKey("last_sync_at")
    private val notifyOnAck = booleanPreferencesKey("notify_on_ack")
    private val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
    private val backgroundSync = booleanPreferencesKey("background_sync")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            pollIntervalSeconds = p[pollInterval] ?: 60,
            onCallMode = p[onCallMode] ?: false,
            lastSyncAt = p[lastSyncAt] ?: 0L,
            notifyOnAck = p[notifyOnAck] ?: false,
            notificationsEnabled = p[notificationsEnabled] ?: true,
            backgroundSync = p[backgroundSync] ?: true,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setPollInterval(seconds: Int) = context.dataStore.edit { it[pollInterval] = seconds }
    suspend fun setOnCallMode(enabled: Boolean) = context.dataStore.edit { it[onCallMode] = enabled }
    suspend fun setLastSyncAt(epochMillis: Long) = context.dataStore.edit { it[lastSyncAt] = epochMillis }
    suspend fun setNotifyOnAck(enabled: Boolean) = context.dataStore.edit { it[notifyOnAck] = enabled }
    suspend fun setNotificationsEnabled(enabled: Boolean) = context.dataStore.edit { it[notificationsEnabled] = enabled }
    suspend fun setBackgroundSync(enabled: Boolean) = context.dataStore.edit { it[backgroundSync] = enabled }
    suspend fun clear() = context.dataStore.edit { it.clear() }
}
