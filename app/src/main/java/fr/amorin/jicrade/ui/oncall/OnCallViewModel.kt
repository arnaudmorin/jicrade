package fr.amorin.jicrade.ui.oncall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import fr.amorin.jicrade.data.local.SettingsStore
import fr.amorin.jicrade.data.remote.toUserMessage
import fr.amorin.jicrade.data.repo.OnCallRepository
import fr.amorin.jicrade.data.repo.OnCallSnapshot
import fr.amorin.jicrade.data.repo.currentShift
import fr.amorin.jicrade.data.repo.horizonEnd
import fr.amorin.jicrade.data.repo.nextShift
import fr.amorin.jicrade.sync.CalendarSyncer
import fr.amorin.jicrade.sync.PollingController
import fr.amorin.jicrade.ui.theme.personColors
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
    val colors: Map<String, Int>,
)

data class MyNextShift(val period: OnCallPeriodEntity, val scheduleName: String, val current: Boolean)

data class OnCallState(
    val schedules: List<ScheduleCard> = emptyList(),
    val mine: MyNextShift? = null,
    val names: Map<String, String> = emptyMap(),
    val accountId: String = "",
    val horizonMonths: Int = 12,
    val refreshing: Boolean = false,
    val loadedOnce: Boolean = false,
    val error: String? = null,
) {
    fun name(id: String): String = names[id] ?: id.takeLast(6)
}

@HiltViewModel
class OnCallViewModel @Inject constructor(
    private val onCall: OnCallRepository,
    private val polling: PollingController,
    private val calendarSyncer: CalendarSyncer,
    settings: SettingsStore,
    sessionHolder: SessionHolder,
) : ViewModel() {
    private val transient = MutableStateFlow(OnCallState())

    val state: StateFlow<OnCallState> = combine(onCall.snapshot(), sessionHolder.session, settings.settings, transient) { snap, session, prefs, t ->
        val me = session?.accountId.orEmpty()
        val until = horizonEnd(System.currentTimeMillis(), prefs.horizonMonths)
        t.copy(schedules = snap.toCards(me, until), mine = snap.myNext(me, until), names = snap.names, accountId = me, horizonMonths = prefs.horizonMonths)
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
                runCatching { calendarSyncer.syncCached() }
            } catch (e: Exception) {
                transient.update { it.copy(refreshing = false, loadedOnce = true, error = e.toUserMessage()) }
            }
        }
    }

    fun clearError() = transient.update { it.copy(error = null) }

    private fun OnCallSnapshot.myNext(me: String, until: Long): MyNextShift? {
        val now = System.currentTimeMillis()
        val current = schedules.firstNotNullOfOrNull { s -> s.periods.currentShift(me, now)?.let { MyNextShift(it, s.schedule.name, true) } }
        return current ?: schedules
            .mapNotNull { s -> s.periods.nextShift(me, now)?.takeIf { it.start < until }?.let { MyNextShift(it, s.schedule.name, false) } }
            .minByOrNull { it.period.start }
    }

    private fun OnCallSnapshot.toCards(me: String, until: Long): List<ScheduleCard> {
        val now = System.currentTimeMillis()
        return schedules.map { s ->
            ScheduleCard(
                id = s.schedule.id,
                name = s.schedule.name,
                timezone = s.schedule.timezone,
                onCallNow = s.schedule.onCallIds,
                onCallNext = s.schedule.nextOnCallIds,
                myCurrentShift = s.periods.currentShift(me, now),
                myNextShift = s.periods.nextShift(me, now)?.takeIf { it.start < until },
                colors = assignColors(s.schedule.onCallIds + s.schedule.nextOnCallIds + s.periods.flatMap { it.responderIds }, personColors.size),
            )
        }
    }
}
