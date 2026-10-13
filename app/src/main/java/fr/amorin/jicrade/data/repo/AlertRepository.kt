package fr.amorin.jicrade.data.repo

import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.local.AlertDao
import fr.amorin.jicrade.data.local.AlertEntity
import fr.amorin.jicrade.data.local.SettingsStore
import fr.amorin.jicrade.data.remote.OpsApi
import fr.amorin.jicrade.data.remote.api
import fr.amorin.jicrade.data.remote.dto.AlertDto
import fr.amorin.jicrade.data.remote.dto.LogDto
import fr.amorin.jicrade.data.remote.dto.NoteDto
import fr.amorin.jicrade.data.remote.dto.NoteRequest
import fr.amorin.jicrade.data.remote.dto.SnoozeRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** [resolved] = alerts that were open before this sync and are now acked or closed. */
data class SyncResult(val newAlerts: List<AlertEntity>, val resolved: List<AlertEntity>, val total: Int)

@Singleton
class AlertRepository @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val opsApi: OpsApi,
    private val alertDao: AlertDao,
    private val settings: SettingsStore,
    private val json: Json,
) {
    private val syncMutex = Mutex()
    private val cloudId: String get() = sessionHolder.require().cloudId

    fun alerts(): Flow<List<AlertEntity>> = alertDao.observeAll()
    fun alert(id: String): Flow<AlertEntity?> = alertDao.observe(id)

    suspend fun sync(): SyncResult = syncMutex.withLock {
        val team = sessionHolder.require().teamName.replace("\"", "\\\"")
        val query = "teams: \"$team\""
        val active = fetchAll("$query AND NOT status: closed", maxItems = 500)
        val closed = fetchAll("$query AND status: closed", maxItems = 100)
        val now = System.currentTimeMillis()
        val fetched = (active + closed).distinctBy { it.id }.map { it.toEntity(now) }

        val previous = alertDao.all().associateBy { it.id }
        val firstSync = settings.current().lastSyncAt == 0L
        alertDao.replaceAll(fetched)
        settings.setLastSyncAt(now)

        SyncResult(
            newAlerts = if (firstSync) emptyList() else fetched.filter { it.isOpen && it.id !in previous },
            resolved = fetched.filter { !it.isOpen && previous[it.id]?.isOpen == true },
            total = fetched.size,
        )
    }

    private suspend fun fetchAll(query: String, maxItems: Int): List<AlertDto> {
        val out = mutableListOf<AlertDto>()
        while (out.size < maxItems) {
            val page = api(json) { opsApi.listAlerts(cloudId, query = query, size = 100, offset = out.size) }
            out += page.values
            if (page.values.isEmpty() || page.links?.next == null) break
        }
        return out.take(maxItems)
    }

    suspend fun fetchOne(id: String): AlertEntity {
        val entity = api(json) { opsApi.getAlert(cloudId, id) }.toEntity()
        alertDao.upsert(entity)
        return entity
    }

    suspend fun acknowledge(id: String) = action(id, { it.copy(status = "acked", acknowledged = true) }) { opsApi.acknowledge(cloudId, id) }
    suspend fun unacknowledge(id: String) = action(id, { it.copy(status = "open", acknowledged = false) }) { opsApi.unacknowledge(cloudId, id) }
    suspend fun close(id: String) = action(id, { it.copy(status = "closed", closeTime = System.currentTimeMillis()) }) { opsApi.close(cloudId, id) }
    suspend fun snooze(id: String, untilEpochMillis: Long) =
        action(id, { it.copy(status = "snoozed", snoozed = true, snoozedUntil = untilEpochMillis) }) {
            opsApi.snooze(cloudId, id, SnoozeRequest(formatIso(untilEpochMillis)))
        }

    // Actions are async 202s: apply the optimistic state, then refetch once the server has caught up.
    private suspend fun action(id: String, optimistic: (AlertEntity) -> AlertEntity, call: suspend () -> Unit) {
        alertDao.get(id)?.let { alertDao.upsert(optimistic(it)) }
        try {
            api(json) { call() }
        } catch (e: Exception) {
            runCatching { fetchOne(id) }
            throw e
        }
        delay(1500)
        runCatching { fetchOne(id) }
    }

    suspend fun notes(id: String): List<NoteDto> = api(json) { opsApi.notes(cloudId, id) }.values

    suspend fun addNote(id: String, text: String): NoteDto = api(json) { opsApi.addNote(cloudId, id, NoteRequest(text)) }

    suspend fun logs(id: String): List<LogDto> = api(json) { opsApi.logs(cloudId, id) }.values
}
