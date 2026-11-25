package fr.amorin.jicrade.data.repo

import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.local.Converters
import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import fr.amorin.jicrade.data.local.ScheduleDao
import fr.amorin.jicrade.data.local.ScheduleEntity
import fr.amorin.jicrade.data.remote.OpsApi
import fr.amorin.jicrade.data.remote.api
import fr.amorin.jicrade.data.remote.dto.ScheduleDto
import fr.amorin.jicrade.data.remote.dto.TimelineDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class ScheduleOnCall(
    val schedule: ScheduleEntity,
    val periods: List<OnCallPeriodEntity>,
)

data class OnCallSnapshot(
    val schedules: List<ScheduleOnCall>,
    val names: Map<String, String>,
)

@Singleton
class OnCallRepository @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val opsApi: OpsApi,
    private val scheduleDao: ScheduleDao,
    private val users: UserRepository,
    private val json: Json,
) {
    private val cloudId: String get() = sessionHolder.require().cloudId
    private val converters = Converters()

    fun snapshot(): Flow<OnCallSnapshot> = combine(
        scheduleDao.observeSchedules(),
        scheduleDao.observePeriods(),
        users.names(),
    ) { schedules, periods, names ->
        val byId = periods.groupBy { it.scheduleId }
        OnCallSnapshot(schedules.map { ScheduleOnCall(it, byId[it.id].orEmpty()) }, names)
    }

    fun onCallNow(): Flow<Boolean> = combine(sessionHolder.session, snapshot()) { session, snap ->
        session != null && isOnCall(session.accountId, snap.schedules.map { it.schedule }, snap.schedules.flatMap { it.periods })
    }

    suspend fun amIOnCall(maxAgeMs: Long = 10 * 60_000L): Boolean {
        val session = sessionHolder.require()
        val now = System.currentTimeMillis()
        var schedules = scheduleDao.schedules()
        if (schedules.isEmpty()) {
            runCatching { refresh() }
            schedules = scheduleDao.schedules()
        }
        val stale = schedules.filter { now - it.fetchedAt > maxAgeMs }
        if (stale.isNotEmpty()) {
            stale.forEach { s ->
                runCatching { api(json) { opsApi.onCalls(cloudId, s.id).onCallUsers } }
                    .onSuccess { ids -> scheduleDao.updateOnCall(s.id, converters.stringListToJson(ids), now) }
            }
            schedules = scheduleDao.schedules()
        }
        return isOnCall(session.accountId, schedules, scheduleDao.periods(), now)
    }

    suspend fun nextBoundary(now: Long = System.currentTimeMillis()): Long? {
        val session = sessionHolder.session.value ?: return null
        return scheduleDao.periods().nextBoundary(session.accountId, now)
    }

    suspend fun refresh() {
        val session = sessionHolder.require()
        val now = System.currentTimeMillis()
        val mine = fetchSchedules().filter { it.teamId == session.teamId && it.enabled }

        val schedules = mine.map { s ->
            ScheduleEntity(
                id = s.id,
                name = s.name ?: s.id,
                teamId = s.teamId,
                timezone = s.timezone,
                enabled = s.enabled,
                onCallIds = runCatching { api(json) { opsApi.onCalls(cloudId, s.id).onCallUsers } }.getOrDefault(emptyList()),
                nextOnCallIds = runCatching { api(json) { opsApi.nextOnCalls(cloudId, s.id).nextOnCallUsers } }.getOrDefault(emptyList()),
                fetchedAt = now,
            )
        }
        val periods = mine.flatMap { s ->
            runCatching { api(json) { opsApi.timeline(cloudId, s.id, date = formatIso(now)) } }
                .getOrNull()?.toPeriods(s.id).orEmpty()
        }
        scheduleDao.replaceAll(schedules, periods)

        val accountIds = schedules.flatMap { it.onCallIds + it.nextOnCallIds } + periods.flatMap { it.responderIds }
        users.ensure(accountIds + session.accountId)
    }

    suspend fun week(scheduleId: String, weekStart: Long): List<OnCallPeriodEntity> {
        val periods = api(json, "timeline") {
            opsApi.timeline(cloudId, scheduleId, interval = 7, intervalUnit = "days", date = formatIso(weekStart))
        }.toPeriods(scheduleId)
        users.ensure(periods.flatMap { it.responderIds })
        return periods
    }

    suspend fun myShifts(months: Int = 6, now: Long = System.currentTimeMillis()): List<OnCallPeriodEntity> {
        val me = sessionHolder.require().accountId
        var schedules = scheduleDao.schedules()
        if (schedules.isEmpty()) {
            refresh()
            schedules = scheduleDao.schedules()
        }
        val until = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).plusMonths(months.toLong()).toInstant().toEpochMilli()
        val results = coroutineScope {
            schedules.map { s ->
                async {
                    runCatching {
                        api(json, "timeline") {
                            opsApi.timeline(cloudId, s.id, interval = months + 1, intervalUnit = "months", date = formatIso(now))
                        }.toPeriods(s.id)
                    }
                }
            }.awaitAll()
        }
        if (results.isNotEmpty() && results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        return results.flatMap { it.getOrDefault(emptyList()) }
            .filter { me in it.responderIds && it.end > now && it.start < until }
    }

    private suspend fun fetchSchedules(): List<ScheduleDto> {
        val out = mutableListOf<ScheduleDto>()
        while (true) {
            val page = api(json) { opsApi.schedules(cloudId, size = 50, offset = out.size) }
            out += page.values
            if (page.values.isEmpty() || page.links?.next == null) return out
        }
    }

    private fun TimelineDto.toPeriods(scheduleId: String): List<OnCallPeriodEntity> =
        finalTimeline?.rotations.orEmpty().flatMap { rotation ->
            rotation.periods.mapNotNull { p ->
                val start = parseInstant(p.startDate) ?: return@mapNotNull null
                val end = parseInstant(p.endDate) ?: return@mapNotNull null
                val responders = p.flattenedResponders.ifEmpty { listOfNotNull(p.responder) }
                OnCallPeriodEntity(
                    scheduleId = scheduleId,
                    rotationName = rotation.name ?: "",
                    start = start,
                    end = end,
                    type = p.type ?: "base",
                    responderIds = responders.filter { it.type == "user" }.mapNotNull { it.id },
                )
            }
        }
}

// The on-calls endpoint reflects live overrides; the timeline is the offline fallback and errs on the side of notifying.
fun isOnCall(
    accountId: String,
    schedules: List<ScheduleEntity>,
    periods: List<OnCallPeriodEntity>,
    now: Long = System.currentTimeMillis(),
): Boolean = schedules.any { accountId in it.onCallIds } || periods.currentShift(accountId, now) != null

fun List<OnCallPeriodEntity>.currentShift(accountId: String, now: Long = System.currentTimeMillis()): OnCallPeriodEntity? =
    firstOrNull { accountId in it.responderIds && it.start <= now && now < it.end }

fun List<OnCallPeriodEntity>.nextShift(accountId: String, now: Long = System.currentTimeMillis()): OnCallPeriodEntity? =
    filter { accountId in it.responderIds && it.start > now }.minByOrNull { it.start }

fun List<OnCallPeriodEntity>.nextBoundary(accountId: String, now: Long = System.currentTimeMillis()): Long? =
    filter { accountId in it.responderIds }
        .flatMap { listOf(it.start, it.end) }
        .filter { it > now }
        .minOrNull()

fun mergeShifts(periods: List<OnCallPeriodEntity>): List<OnCallPeriodEntity> =
    periods.groupBy { it.scheduleId }.values.flatMap { list ->
        list.sortedBy { it.start }.fold(mutableListOf<OnCallPeriodEntity>()) { acc, p ->
            val last = acc.lastOrNull()
            if (last != null && p.start <= last.end) {
                acc[acc.lastIndex] = last.copy(
                    end = maxOf(last.end, p.end),
                    type = if (last.type == "base") p.type else last.type,
                )
            } else {
                acc += p
            }
            acc
        }
    }.sortedBy { it.start }
