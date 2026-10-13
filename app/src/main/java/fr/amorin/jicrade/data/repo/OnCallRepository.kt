package fr.amorin.jicrade.data.repo

import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import fr.amorin.jicrade.data.local.ScheduleDao
import fr.amorin.jicrade.data.local.ScheduleEntity
import fr.amorin.jicrade.data.remote.OpsApi
import fr.amorin.jicrade.data.remote.api
import fr.amorin.jicrade.data.remote.dto.ScheduleDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class ScheduleOnCall(
    val schedule: ScheduleEntity,
    val periods: List<OnCallPeriodEntity>,
)

data class OnCallSnapshot(
    val schedules: List<ScheduleOnCall>,
    val names: Map<String, String>,
) {
    fun name(id: String): String = names[id] ?: id.takeLast(6)
}

@Singleton
class OnCallRepository @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val opsApi: OpsApi,
    private val scheduleDao: ScheduleDao,
    private val users: UserRepository,
    private val json: Json,
) {
    fun snapshot(): Flow<OnCallSnapshot> = combine(
        scheduleDao.observeSchedules(),
        scheduleDao.observePeriods(),
        users.names(),
    ) { schedules, periods, names ->
        val byId = periods.groupBy { it.scheduleId }
        OnCallSnapshot(schedules.map { ScheduleOnCall(it, byId[it.id].orEmpty()) }, names)
    }

    suspend fun refresh() {
        val session = sessionHolder.require()
        val cloudId = session.cloudId
        val all = mutableListOf<ScheduleDto>()
        var offset = 0
        while (true) {
            val page = api(json) { opsApi.schedules(cloudId, size = 50, offset = offset) }
            all += page.values
            if (page.values.isEmpty() || page.links?.next == null) break
            offset += page.values.size
        }
        val mine = all.filter { it.teamId == session.teamId && it.enabled }
        val now = System.currentTimeMillis()
        val ids = mutableSetOf<String>()
        val entities = mine.map { s ->
            val onCall = runCatching { api(json) { opsApi.onCalls(cloudId, s.id).onCallUsers } }.getOrDefault(emptyList())
            val next = runCatching { api(json) { opsApi.nextOnCalls(cloudId, s.id).nextOnCallUsers } }.getOrDefault(emptyList())
            ids += onCall; ids += next
            val timeline = runCatching { api(json) { opsApi.timeline(cloudId, s.id, date = formatIso(now)) } }.getOrNull()
            val periods = timeline?.finalTimeline?.rotations.orEmpty().flatMap { rot ->
                rot.periods.mapNotNull { p ->
                    val start = parseInstant(p.startDate) ?: return@mapNotNull null
                    val end = parseInstant(p.endDate) ?: return@mapNotNull null
                    val responders = (p.flattenedResponders.ifEmpty { listOfNotNull(p.responder) })
                        .filter { it.type == "user" && it.id != null }
                        .map { it.id!! }
                    ids += responders
                    OnCallPeriodEntity(
                        scheduleId = s.id,
                        rotationName = rot.name ?: "",
                        start = start,
                        end = end,
                        type = p.type ?: "base",
                        responderIds = responders,
                    )
                }
            }
            scheduleDao.replacePeriods(s.id, periods)
            ScheduleEntity(
                id = s.id,
                name = s.name ?: s.id,
                teamId = s.teamId,
                timezone = s.timezone,
                enabled = s.enabled,
                onCallIds = onCall,
                nextOnCallIds = next,
                fetchedAt = now,
            )
        }
        scheduleDao.upsertSchedules(entities)
        scheduleDao.deleteSchedulesOthers(entities.map { it.id })
        users.ensure(ids + session.accountId)
    }
}

fun List<OnCallPeriodEntity>.currentShift(accountId: String, now: Long = System.currentTimeMillis()): OnCallPeriodEntity? =
    firstOrNull { accountId in it.responderIds && it.start <= now && now < it.end }

fun List<OnCallPeriodEntity>.nextShift(accountId: String, now: Long = System.currentTimeMillis()): OnCallPeriodEntity? =
    filter { accountId in it.responderIds && it.start > now }.minByOrNull { it.start }
