package fr.amorin.jicrade.ui.alerts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.amorin.jicrade.data.local.AlertEntity
import fr.amorin.jicrade.data.remote.dto.LogDto
import fr.amorin.jicrade.data.remote.dto.NoteDto
import fr.amorin.jicrade.data.remote.toUserMessage
import fr.amorin.jicrade.data.repo.AlertRepository
import fr.amorin.jicrade.data.repo.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertDetailState(
    val alert: AlertEntity? = null,
    val notes: List<NoteDto> = emptyList(),
    val logs: List<LogDto> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AlertDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val alerts: AlertRepository,
    private val users: UserRepository,
) : ViewModel() {
    val alertId: String = checkNotNull(savedState["id"])
    private val transient = MutableStateFlow(AlertDetailState())

    val state: StateFlow<AlertDetailState> = combine(alerts.alert(alertId), users.names(), transient) { alert, names, t ->
        t.copy(alert = alert, names = names)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertDetailState())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                coroutineScope {
                    val a = async { alerts.fetchOne(alertId) }
                    val n = async { runCatching { alerts.notes(alertId) }.getOrDefault(emptyList()) }
                    val l = async { runCatching { alerts.logs(alertId) }.getOrDefault(emptyList()) }
                    val alert = a.await()
                    users.ensure(alert.responders.filter { it.type == "user" }.map { it.id })
                    transient.update { it.copy(notes = n.await(), logs = l.await().sortedByDescending { it.logTime }, loading = false, error = null) }
                }
            } catch (e: Exception) {
                transient.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun acknowledge() = act { alerts.acknowledge(alertId) }
    fun unacknowledge() = act { alerts.unacknowledge(alertId) }
    fun close() = act { alerts.close(alertId) }
    fun snooze(hours: Int) = act { alerts.snooze(alertId, System.currentTimeMillis() + hours * 3_600_000L) }
    fun addNote(text: String) = act {
        alerts.addNote(alertId, text)
        transient.update { it.copy(notes = runCatching { alerts.notes(alertId) }.getOrDefault(it.notes)) }
    }

    fun clearError() = transient.update { it.copy(error = null) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            transient.update { it.copy(busy = true) }
            try {
                block()
                transient.update { it.copy(logs = runCatching { alerts.logs(alertId) }.getOrDefault(it.logs).sortedByDescending { l -> l.logTime }) }
            } catch (e: Exception) {
                transient.update { it.copy(error = e.toUserMessage()) }
            } finally {
                transient.update { it.copy(busy = false) }
            }
        }
    }
}
