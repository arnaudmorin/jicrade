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
