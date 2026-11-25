package fr.amorin.jicrade.ui.oncall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import fr.amorin.jicrade.data.remote.toUserMessage
import fr.amorin.jicrade.data.repo.OnCallRepository
import fr.amorin.jicrade.ui.theme.personColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val weekStart: LocalDate = weekStartOf(System.currentTimeMillis(), ZoneId.systemDefault()),
    val periods: List<OnCallPeriodEntity>? = null,
    val blocks: List<ShiftBlock> = emptyList(),
    val colors: Map<String, Int> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
    val accountId: String = "",
    val loading: Boolean = false,
    val error: String? = null,
) {
    fun name(id: String): String = names[id] ?: id.takeLast(6)
    fun colorIndex(id: String): Int = colors[id] ?: 0
    val weekIds: List<String> get() = blocks.flatMap { it.period.responderIds }.distinct().sortedBy { name(it).lowercase() }
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val onCall: OnCallRepository,
    sessionHolder: SessionHolder,
) : ViewModel() {
    private val scheduleId: String = checkNotNull(savedState["id"])
    private val transient = MutableStateFlow(ScheduleState())
    private val cache = mutableMapOf<LocalDate, List<OnCallPeriodEntity>>()
    private var job: Job? = null

    val state: StateFlow<ScheduleState> = combine(onCall.snapshot(), sessionHolder.session, transient) { snap, session, t ->
        val s = snap.schedules.firstOrNull { it.schedule.id == scheduleId }
        val periods = t.periods.orEmpty()
        val knownIds = s?.periods.orEmpty().flatMap { it.responderIds } + periods.flatMap { it.responderIds }
        t.copy(
            name = s?.schedule?.name ?: t.name,
            timezone = s?.schedule?.timezone,
            names = snap.names,
            accountId = session?.accountId.orEmpty(),
            blocks = layoutWeek(periods, t.weekStart, t.zone),
            colors = assignColors(knownIds, personColors.size),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleState())

    init {
        load()
    }

    fun previous() = show(transient.value.weekStart.minusWeeks(1))

    fun next() = show(transient.value.weekStart.plusWeeks(1))

    fun today() = show(weekStartOf(System.currentTimeMillis(), transient.value.zone))

    fun refresh() {
        cache.remove(transient.value.weekStart)
        load()
    }

    fun clearError() = transient.update { it.copy(error = null) }

    private fun show(week: LocalDate) {
        if (week == transient.value.weekStart) return
        transient.update { it.copy(weekStart = week, periods = cache[week]) }
        load()
    }

    private fun load() {
        val week = transient.value.weekStart
        cache[week]?.let { cached -> transient.update { it.copy(periods = cached, loading = false) }; return }
        job?.cancel()
        transient.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val periods = onCall.week(scheduleId, week.startMillis(transient.value.zone))
                cache[week] = periods
                transient.update { if (it.weekStart == week) it.copy(periods = periods, loading = false) else it }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                transient.update { it.copy(loading = false, periods = it.periods ?: emptyList(), error = e.toUserMessage()) }
            }
        }
    }
}
