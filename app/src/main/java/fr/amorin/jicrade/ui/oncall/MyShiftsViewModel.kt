package fr.amorin.jicrade.ui.oncall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import fr.amorin.jicrade.data.remote.toUserMessage
import fr.amorin.jicrade.data.repo.OnCallRepository
import fr.amorin.jicrade.data.repo.mergeShifts
import fr.amorin.jicrade.sync.CalendarSyncer
import fr.amorin.jicrade.ui.theme.personColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class MyShiftsState(
    val shifts: List<OnCallPeriodEntity>? = null,
    val scheduleNames: Map<String, String> = emptyMap(),
    val colors: Map<String, Int> = emptyMap(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val groups: List<MonthGroup> get() = groupByMonth(shifts.orEmpty(), zone)
    val totalMs: Long get() = shifts.orEmpty().sumOf { it.end - it.start }
}

@HiltViewModel
class MyShiftsViewModel @Inject constructor(
    private val onCall: OnCallRepository,
    private val calendarSyncer: CalendarSyncer,
) : ViewModel() {
    private val transient = MutableStateFlow(MyShiftsState())

    val state: StateFlow<MyShiftsState> = combine(onCall.snapshot(), transient) { snap, t ->
        val ids = snap.schedules.map { it.schedule.id }
        t.copy(
            scheduleNames = snap.schedules.associate { it.schedule.id to it.schedule.name },
            colors = assignColors(ids, personColors.size),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyShiftsState())

    init {
        refresh()
    }

    fun refresh() {
        if (transient.value.loading) return
        transient.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val shifts = mergeShifts(onCall.myShifts())
                transient.update { it.copy(shifts = shifts, loading = false) }
                runCatching { calendarSyncer.syncFrom(shifts) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                transient.update { it.copy(loading = false, shifts = it.shifts ?: emptyList(), error = e.toUserMessage()) }
            }
        }
    }

    fun clearError() = transient.update { it.copy(error = null) }
}
