package fr.amorin.jicrade.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.local.AlertEntity
import fr.amorin.jicrade.data.local.NotificationMode
import fr.amorin.jicrade.data.local.SettingsStore
import fr.amorin.jicrade.data.remote.toUserMessage
import fr.amorin.jicrade.data.repo.AlertRepository
import fr.amorin.jicrade.data.repo.OnCallRepository
import fr.amorin.jicrade.sync.NewAlertNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AlertTab(val label: String) { OPEN("Open"), ACKED("Acked"), CLOSED("Closed") }

enum class NotificationStatus(val label: String?) { ON_CALL("on call"), OFF_DUTY("not on call · silent"), ALWAYS(null), MUTED("muted") }

data class AlertListState(
    val open: List<AlertEntity> = emptyList(),
    val acked: List<AlertEntity> = emptyList(),
    val closed: List<AlertEntity> = emptyList(),
    val teamName: String = "",
    val refreshing: Boolean = false,
    val lastSyncAt: Long = 0L,
    val status: NotificationStatus = NotificationStatus.ALWAYS,
    val error: String? = null,
) {
    fun forTab(tab: AlertTab): List<AlertEntity> = when (tab) {
        AlertTab.OPEN -> open
        AlertTab.ACKED -> acked
        AlertTab.CLOSED -> closed
    }
}

@HiltViewModel
class AlertListViewModel @Inject constructor(
    private val alerts: AlertRepository,
    private val notifier: NewAlertNotifier,
    private val settings: SettingsStore,
    onCall: OnCallRepository,
    sessionHolder: SessionHolder,
) : ViewModel() {
    private val transient = MutableStateFlow(AlertListState())

    val state: StateFlow<AlertListState> = combine(
        alerts.alerts(), settings.settings, sessionHolder.session, onCall.onCallNow(), transient,
    ) { list, prefs, session, onCallNow, t ->
        val sorted = list.sortedWith(compareByDescending<AlertEntity> { it.createdAt }.thenBy { it.priority })
        t.copy(
            open = sorted.filter { it.isOpen },
            acked = sorted.filter { it.isAcked },
            closed = list.filter { it.isClosed }.sortedByDescending { it.closeTime ?: it.updatedAt },
            teamName = session?.teamName.orEmpty(),
            lastSyncAt = prefs.lastSyncAt,
            status = when (prefs.notificationMode) {
                NotificationMode.NEVER -> NotificationStatus.MUTED
                NotificationMode.ALWAYS -> NotificationStatus.ALWAYS
                NotificationMode.ON_CALL -> if (onCallNow) NotificationStatus.ON_CALL else NotificationStatus.OFF_DUTY
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertListState())

    init {
        viewModelScope.launch {
            if (System.currentTimeMillis() - settings.settings.first().lastSyncAt > 30_000) refresh()
        }
    }

    fun refresh() {
        if (transient.value.refreshing) return
        transient.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            try {
                notifier.notify(alerts.sync())
                transient.update { it.copy(refreshing = false) }
            } catch (e: Exception) {
                transient.update { it.copy(refreshing = false, error = e.toUserMessage()) }
            }
        }
    }

    fun acknowledge(id: String) = act { alerts.acknowledge(id) }
    fun unacknowledge(id: String) = act { alerts.unacknowledge(id) }
    fun close(id: String) = act { alerts.close(id) }

    fun clearError() = transient.update { it.copy(error = null) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                transient.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }
}
