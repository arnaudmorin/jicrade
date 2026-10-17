package fr.amorin.jicrade.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.amorin.jicrade.data.auth.Session
import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.local.NotificationMode
import fr.amorin.jicrade.data.local.PollingMode
import fr.amorin.jicrade.data.local.Settings
import fr.amorin.jicrade.data.local.SettingsStore
import fr.amorin.jicrade.data.remote.dto.TeamDto
import fr.amorin.jicrade.data.remote.toUserMessage
import fr.amorin.jicrade.data.repo.AuthRepository
import fr.amorin.jicrade.data.repo.OnCallRepository
import fr.amorin.jicrade.data.repo.nextShift
import fr.amorin.jicrade.sync.Notifications
import fr.amorin.jicrade.sync.OnCallPollingService
import fr.amorin.jicrade.sync.PollingController
import fr.amorin.jicrade.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RingtoneOption(val uri: String?, val title: String)

data class SettingsState(
    val session: Session? = null,
    val settings: Settings = Settings(),
    val onCallNow: Boolean = false,
    val nextShiftStart: Long? = null,
    val pollingRunning: Boolean = false,
    val teams: List<TeamDto>? = null,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val settingsStore: SettingsStore,
    private val polling: PollingController,
    onCall: OnCallRepository,
    sessionHolder: SessionHolder,
) : ViewModel() {
    private val transient = MutableStateFlow(SettingsState())

    val state: StateFlow<SettingsState> = combine(
        sessionHolder.session, settingsStore.settings, onCall.onCallNow(), onCall.snapshot(),
        OnCallPollingService.running, transient,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val session = values[0] as Session?
        val prefs = values[1] as Settings
        val onCallNow = values[2] as Boolean
        val snap = values[3] as fr.amorin.jicrade.data.repo.OnCallSnapshot
        val running = values[4] as Boolean
        val t = values[5] as SettingsState
        t.copy(
            session = session,
            settings = prefs,
            onCallNow = onCallNow,
            nextShiftStart = session?.let { s -> snap.schedules.flatMap { it.periods }.nextShift(s.accountId)?.start },
            pollingRunning = running,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setNotificationMode(mode: NotificationMode) {
        viewModelScope.launch { settingsStore.setNotificationMode(mode) }
    }

    fun setOverrideDnd(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setOverrideDnd(enabled) }
    }

    fun setPollingMode(mode: PollingMode) {
        viewModelScope.launch {
            settingsStore.setPollingMode(mode)
            polling.evaluate()
        }
    }

    fun setPollInterval(seconds: Int) {
        viewModelScope.launch { settingsStore.setPollInterval(seconds) }
    }

    fun setMinPriority(priority: String) {
        viewModelScope.launch { settingsStore.setMinPriority(priority) }
    }

    fun setBackgroundSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setBackgroundSync(enabled)
            SyncScheduler.applyBackgroundSync(context, enabled)
        }
    }

    fun setNotifyOnAck(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setNotifyOnAck(enabled) }
    }

    val isPlayingPreview = MutableStateFlow(false)
    val ringtones = MutableStateFlow<List<RingtoneOption>?>(null)
    private var previewRingtone: android.media.Ringtone? = null

    fun loadRingtones() {
        if (ringtones.value != null) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val list = mutableListOf<RingtoneOption>()
            val defaultUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            val defaultTitle = runCatching { android.media.RingtoneManager.getRingtone(context, defaultUri)?.getTitle(context) }.getOrNull() ?: "Default alarm"
            list.add(RingtoneOption(uri = null, title = "Default alarm ($defaultTitle)"))

            val manager = android.media.RingtoneManager(context).apply {
                setType(android.media.RingtoneManager.TYPE_ALARM or android.media.RingtoneManager.TYPE_NOTIFICATION or android.media.RingtoneManager.TYPE_RINGTONE)
            }
            runCatching {
                val cursor = manager.cursor
                while (cursor.moveToNext()) {
                    val title = cursor.getString(android.media.RingtoneManager.TITLE_COLUMN_INDEX)
                    val uri = manager.getRingtoneUri(cursor.position)
                    if (title != null && uri != null) {
                        list.add(RingtoneOption(uri = uri.toString(), title = title))
                    }
                }
            }
            val distinct = list.distinctBy { it.title }
            ringtones.value = distinct
        }
    }

    fun setAlertSoundUri(uri: String?) {
        viewModelScope.launch {
            settingsStore.setAlertSoundUri(uri)
            Notifications.ensureAlertChannels(context, uri?.let { android.net.Uri.parse(it) })
        }
    }

    fun setLoopAlertSound(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setLoopAlertSound(enabled) }
    }

    fun setOverrideVolume(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setOverrideVolume(enabled) }
    }

    fun setAlertVolume(volume: Int) {
        viewModelScope.launch { settingsStore.setAlertVolume(volume) }
    }

    fun playSoundPreview(overrideUri: android.net.Uri? = null) {
        stopSoundPreview()
        val uri = overrideUri
            ?: state.value.settings.alertSoundUri?.let { android.net.Uri.parse(it) }
            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        val ringtone = runCatching { android.media.RingtoneManager.getRingtone(context, uri) }.getOrNull() ?: return
        ringtone.audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (state.value.settings.overrideVolume) {
            val audioManager = context.getSystemService(android.media.AudioManager::class.java)
            if (audioManager != null) {
                val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                val target = (max * (state.value.settings.alertVolume.coerceIn(10, 100) / 100f)).let { kotlin.math.round(it).toInt() }.coerceIn(1, max)
                runCatching { audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, target, 0) }
            }
        }

        runCatching {
            ringtone.play()
            previewRingtone = ringtone
            isPlayingPreview.value = true
        }

        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (previewRingtone == ringtone) {
                stopSoundPreview()
            }
        }
    }

    fun stopSoundPreview() {
        runCatching { previewRingtone?.stop() }
        previewRingtone = null
        isPlayingPreview.value = false
    }

    fun sendTestNotification() {
        val prefs = state.value.settings
        val dummyAlert = fr.amorin.jicrade.data.local.AlertEntity(
            id = "test-alert-${System.currentTimeMillis()}",
            tinyId = "999",
            message = "Test alert: notifications are working properly",
            status = "open",
            priority = "P1",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            source = "Test",
        )
        Notifications.showNewAlert(
            context = context,
            alert = dummyAlert,
            bypassDnd = prefs.overrideDnd,
            soundUri = prefs.alertSoundUri?.let { android.net.Uri.parse(it) },
            loop = false,
            overrideVolume = prefs.overrideVolume,
            alertVolume = prefs.alertVolume,
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopSoundPreview()
    }

    fun loadTeams() {
        viewModelScope.launch {
            try {
                transient.update { it.copy(teams = auth.teams()) }
            } catch (e: Exception) {
                transient.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }

    fun dismissTeams() = transient.update { it.copy(teams = null) }

    fun switchTeam(team: TeamDto) {
        viewModelScope.launch {
            auth.switchTeam(team)
            transient.update { it.copy(teams = null) }
            SyncScheduler.syncNow(context)
        }
    }

    fun logout() {
        viewModelScope.launch {
            OnCallPollingService.stop(context)
            auth.logout()
        }
    }

    fun clearError() = transient.update { it.copy(error = null) }
}
