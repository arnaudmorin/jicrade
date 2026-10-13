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

data class SyncResult(val newAlerts: List<AlertEntity>, val acked: List<AlertEntity>, val total: Int)

@Singleton
class AlertRepository @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val opsApi: OpsApi,
    private val alertDao: AlertDao,
    private val settings: SettingsStore,
    private val json: Json,
) {
    private val syncMutex = Mutex()

    fun alerts(): Flow<List<AlertEntity>> = alertDao.observeAll()
    fun alert(id: String): Flow<AlertEntity?> = alertDao.observe(id)

    private fun teamQuery(): String {
        val team = sessionHolder.require().teamName.replace("\"", "\\\"")
        return "teams: \"$team\""
    }

    suspend fun sync(): SyncResult = syncMutex.withLock {
        val base = teamQuery()
        val active = fetchAll("$base AND NOT status: closed", maxItems = 500)
        val closed = fetchAll("$base AND status: closed", maxItems = 100)
        val now = System.currentTimeMillis()
        val fetched = (active + closed).distinctBy { it.id }.map { it.toEntity(now) }

        val previous = alertDao.ids().toSet()
        val lastSync = settings.current().lastSyncAt
        val previousAcked = fetched.filter { it.isAcked && it.id in previous }
            .filter { alertDao.get(it.id)?.isAcked == false }

        alertDao.replaceAll(fetched)
        settings.setLastSyncAt(now)

        val fresh = if (lastSync == 0L) emptyList() else fetched.filter { it.isOpen && it.id !in previous }
        SyncResult(newAlerts = fresh, acked = previousAcked, total = fetched.size)
    }

    private suspend fun fetchAll(query: String, maxItems: Int): List<AlertDto> {
        val cloudId = sessionHolder.require().cloudId
        val out = mutableListOf<AlertDto>()
        var offset = 0
        while (out.size < maxItems) {
            val page = api(json) { opsApi.listAlerts(cloudId, query = query, size = 100, offset = offset) }
            out += page.values
            if (page.values.isEmpty() || page.links?.next == null) break
            offset += page.values.size
        }
        return out.take(maxItems)
    }

    suspend fun fetchOne(id: String): AlertEntity {
        val cloudId = sessionHolder.require().cloudId
        val entity = api(json) { opsApi.getAlert(cloudId, id) }.toEntity()
        alertDao.upsert(entity)
        return entity
    }

    suspend fun acknowledge(id: String) = action(id, { it.copy(status = "acked", acknowledged = true) }) { c -> opsApi.acknowledge(c, id) }
    suspend fun unacknowledge(id: String) = action(id, { it.copy(status = "open", acknowledged = false) }) { c -> opsApi.unacknowledge(c, id) }
    suspend fun close(id: String) = action(id, { it.copy(status = "closed", closeTime = System.currentTimeMillis()) }) { c -> opsApi.close(c, id) }
    suspend fun snooze(id: String, untilEpochMillis: Long) =
        action(id, { it.copy(status = "snoozed", snoozed = true, snoozedUntil = untilEpochMillis) }) { c ->
            opsApi.snooze(c, id, SnoozeRequest(formatIso(untilEpochMillis)))
        }

    private suspend fun action(
        id: String,
        optimistic: (AlertEntity) -> AlertEntity,
        call: suspend (cloudId: String) -> Any,
    ) {
        val cloudId = sessionHolder.require().cloudId
        alertDao.get(id)?.let { alertDao.upsert(optimistic(it)) }
        try {
            api(json) { call(cloudId) }
        } catch (e: Exception) {
            runCatching { fetchOne(id) }
            throw e
        }
        delay(1500)
        runCatching { fetchOne(id) }
    }

    suspend fun notes(id: String): List<NoteDto> {
        val cloudId = sessionHolder.require().cloudId
        return api(json) { opsApi.notes(cloudId, id) }.values
    }

    suspend fun addNote(id: String, text: String): NoteDto {
        val cloudId = sessionHolder.require().cloudId
        return api(json) { opsApi.addNote(cloudId, id, NoteRequest(text)) }
    }

    suspend fun logs(id: String): List<LogDto> {
        val cloudId = sessionHolder.require().cloudId
        return api(json) { opsApi.logs(cloudId, id) }.values
    }
}
