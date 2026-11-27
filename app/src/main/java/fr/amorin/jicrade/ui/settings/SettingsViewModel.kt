package fr.amorin.jicrade.ui.settings

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
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
import fr.amorin.jicrade.sync.AlarmVolume
import fr.amorin.jicrade.sync.CalendarSyncScheduler
import fr.amorin.jicrade.sync.CalendarSyncer
import fr.amorin.jicrade.sync.Notifications
import fr.amorin.jicrade.sync.OnCallPollingService
import fr.amorin.jicrade.sync.PollingController
import fr.amorin.jicrade.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    private val calendarSyncer: CalendarSyncer,
    private val onCall: OnCallRepository,
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

    fun setHorizonMonths(months: Int) {
        viewModelScope.launch {
            settingsStore.setHorizonMonths(months)
            if (settingsStore.current().calendarSync) {
                CalendarSyncScheduler.syncNow(context)
            } else {
                runCatching { onCall.refresh() }
            }
        }
    }

    fun setCalendarSync(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                settingsStore.setCalendarSync(true)
                CalendarSyncScheduler.apply(context, true)
                CalendarSyncScheduler.syncNow(context)
            } else {
                CalendarSyncScheduler.apply(context, false)
                calendarSyncer.disable()
            }
        }
    }

    fun setNotifyOnAck(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setNotifyOnAck(enabled) }
    }

    val ringtones = MutableStateFlow<List<RingtoneOption>?>(null)
    private var previewRingtone: Ringtone? = null

    val ringtoneTitle: StateFlow<String> = settingsStore.settings
        .map { it.alertSoundUri }
        .distinctUntilChanged()
        .map { uri -> if (uri == null) "Default alarm" else ringtoneTitle(Uri.parse(uri)) ?: "Custom sound" }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private fun ringtoneTitle(uri: Uri): String? =
        runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()

    fun loadRingtones() {
        if (ringtones.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val defaultTitle = ringtoneTitle(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            val list = mutableListOf(RingtoneOption(null, defaultTitle?.let { "Default alarm ($it)" } ?: "Default alarm"))
            val manager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_RINGTONE)
            }
            runCatching {
                val cursor = manager.cursor
                while (cursor.moveToNext()) {
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                    val uri = manager.getRingtoneUri(cursor.position) ?: continue
                    list += RingtoneOption(uri.toString(), title)
                }
            }
            ringtones.value = list.distinctBy { it.uri }
        }
    }

    fun setLoopAlertSound(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setLoopAlertSound(enabled) }
    }

    fun setOverrideVolume(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setOverrideVolume(enabled) }
    }

    fun setAlertVolume(percent: Int) {
        viewModelScope.launch { settingsStore.setAlertVolume(percent) }
        playSoundPreview(state.value.settings.alertSoundUri, percent)
    }

    fun setAlertSoundUri(uri: String?) {
        viewModelScope.launch {
            settingsStore.setAlertSoundUri(uri)
            Notifications.createAlertChannels(context, uri)
        }
    }

    fun playSoundPreview(uri: String?, volumePercent: Int? = null) {
        stopSoundPreview()
        val ringtone = RingtoneManager.getRingtone(context, uri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) ?: return
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val prefs = state.value.settings
        (volumePercent ?: prefs.alertVolume.takeIf { prefs.overrideVolume && prefs.overrideDnd })?.let { AlarmVolume.raise(context, it) }
        runCatching { ringtone.play() }
        previewRingtone = ringtone
        viewModelScope.launch {
            delay(3_000)
            if (previewRingtone === ringtone) stopSoundPreview()
        }
    }

    fun stopSoundPreview() {
        val ringtone = previewRingtone ?: return
        runCatching { ringtone.stop() }
        previewRingtone = null
        AlarmVolume.restore(context)
    }

    override fun onCleared() {
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
            CalendarSyncScheduler.apply(context, false)
            calendarSyncer.disable()
            auth.logout()
        }
    }

    fun clearError() = transient.update { it.copy(error = null) }
}
