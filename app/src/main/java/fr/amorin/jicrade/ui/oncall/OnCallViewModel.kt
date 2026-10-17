package fr.amorin.jicrade.ui.oncall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import fr.amorin.jicrade.data.remote.toUserMessage
import fr.amorin.jicrade.data.repo.OnCallRepository
import fr.amorin.jicrade.data.repo.OnCallSnapshot
import fr.amorin.jicrade.data.repo.currentShift
import fr.amorin.jicrade.data.repo.nextShift
import fr.amorin.jicrade.sync.PollingController
import fr.amorin.jicrade.ui.common.stripEmailDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleCard(
    val id: String,
    val name: String,
    val timezone: String?,
    val onCallNow: List<String>,
    val onCallNext: List<String>,
    val myCurrentShift: OnCallPeriodEntity?,
    val myNextShift: OnCallPeriodEntity?,
    val upcoming: List<OnCallPeriodEntity>,
)

data class OnCallState(
    val schedules: List<ScheduleCard> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val accountId: String = "",
    val refreshing: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: String? = null,
) {
    fun name(id: String): String = (names[id] ?: id.takeLast(6)).stripEmailDomain()
}

@HiltViewModel
class OnCallViewModel @Inject constructor(
    private val onCall: OnCallRepository,
    private val polling: PollingController,
    sessionHolder: SessionHolder,
) : ViewModel() {
    private val transient = MutableStateFlow(OnCallState())

    val state: StateFlow<OnCallState> = combine(onCall.snapshot(), sessionHolder.session, transient) { snap, session, t ->
        val me = session?.accountId.orEmpty()
        t.copy(schedules = snap.toCards(me), names = snap.names, accountId = me)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnCallState())

    init {
        refresh()
    }

    fun refresh() {
        if (transient.value.refreshing) return
        transient.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            try {
                onCall.refresh()
                transient.update { it.copy(refreshing = false, loadedOnce = true) }
                polling.evaluate()
            } catch (e: Exception) {
                transient.update { it.copy(refreshing = false, loadedOnce = true, error = e.toUserMessage()) }
            }
        }
    }

    fun clearError() = transient.update { it.copy(error = null) }

    private fun OnCallSnapshot.toCards(me: String): List<ScheduleCard> {
        val now = System.currentTimeMillis()
        return schedules.map { s ->
            ScheduleCard(
                id = s.schedule.id,
                name = s.schedule.name,
                timezone = s.schedule.timezone,
                onCallNow = s.schedule.onCallIds,
                onCallNext = s.schedule.nextOnCallIds,
                myCurrentShift = s.periods.currentShift(me, now),
                myNextShift = s.periods.nextShift(me, now),
                upcoming = s.periods.filter { it.end > now }.sortedBy { it.start }.take(14),
            )
        }
    }
}
