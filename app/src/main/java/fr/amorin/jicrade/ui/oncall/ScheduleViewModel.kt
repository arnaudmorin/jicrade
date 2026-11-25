package fr.amorin.jicrade.ui.oncall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.remote.toUserMessage
import fr.amorin.jicrade.data.repo.OnCallRepository
import fr.amorin.jicrade.ui.theme.personColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ScheduleState(
    val name: String = "",
    val timezone: String? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val thisWeek: LocalDate = weekStartOf(System.currentTimeMillis(), ZoneId.systemDefault()),
    val weeks: Map<LocalDate, List<ShiftBlock>> = emptyMap(),
    val loading: Set<LocalDate> = emptySet(),
    val colors: Map<String, Int> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
    val accountId: String = "",
    val error: String? = null,
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val onCall: OnCallRepository,
    sessionHolder: SessionHolder,
) : ViewModel() {
    private val scheduleId: String = checkNotNull(savedState["id"])
    private val transient = MutableStateFlow(ScheduleState())

    val state: StateFlow<ScheduleState> = combine(onCall.snapshot(), sessionHolder.session, transient) { snap, session, t ->
        val s = snap.schedules.firstOrNull { it.schedule.id == scheduleId }
        t.copy(
            name = s?.schedule?.name ?: t.name,
            timezone = s?.schedule?.timezone,
            names = snap.names,
            accountId = session?.accountId.orEmpty(),
            colors = extendColors(t.colors, s?.periods.orEmpty().flatMap { it.responderIds }, personColors.size),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleState())

    fun ensure(week: LocalDate) {
        val t = transient.value
        if (week !in t.weeks && week !in t.loading) load(week)
    }

    fun refresh(visible: List<LocalDate>) {
        transient.update { t -> t.copy(weeks = t.weeks.filterKeys { it in visible }) }
        visible.filter { it !in transient.value.loading }.forEach(::load)
    }

    fun clearError() = transient.update { it.copy(error = null) }

    private fun load(week: LocalDate) {
        transient.update { it.copy(loading = it.loading + week) }
        viewModelScope.launch {
            try {
                val periods = onCall.week(scheduleId, week.startMillis(transient.value.zone))
                val known = state.value.colors
                transient.update {
                    it.copy(
                        weeks = it.weeks + (week to layoutWeek(periods, week, it.zone)),
                        loading = it.loading - week,
                        colors = extendColors(known + it.colors, periods.flatMap { p -> p.responderIds }, personColors.size),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                transient.update { it.copy(loading = it.loading - week, error = it.error ?: e.toUserMessage()) }
            }
        }
    }
}
