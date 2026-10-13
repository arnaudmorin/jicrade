package fr.amorin.jicrade.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.amorin.jicrade.data.auth.Session
import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.local.Settings
import fr.amorin.jicrade.data.local.SettingsStore
import fr.amorin.jicrade.data.remote.dto.TeamDto
import fr.amorin.jicrade.data.remote.toUserMessage
import fr.amorin.jicrade.data.repo.AuthRepository
import fr.amorin.jicrade.sync.OnCallPollingService
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
    val teams: List<TeamDto>? = null,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val settingsStore: SettingsStore,
    sessionHolder: SessionHolder,
) : ViewModel() {
    private val transient = MutableStateFlow(SettingsState())

    val state: StateFlow<SettingsState> = combine(sessionHolder.session, settingsStore.settings, transient) { s, prefs, t ->
        t.copy(session = s, settings = prefs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setOnCallMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setOnCallMode(enabled)
            if (enabled) OnCallPollingService.start(context) else OnCallPollingService.stop(context)
        }
    }

    fun setPollInterval(seconds: Int) {
        viewModelScope.launch { settingsStore.setPollInterval(seconds) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setNotificationsEnabled(enabled) }
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
