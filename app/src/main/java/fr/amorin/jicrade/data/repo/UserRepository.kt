package fr.amorin.jicrade.data.repo

import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.local.UserDao
import fr.amorin.jicrade.data.local.UserEntity
import fr.amorin.jicrade.data.remote.JiraApi
import fr.amorin.jicrade.data.remote.api
import fr.amorin.jicrade.data.remote.decodeUsers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val jiraApi: JiraApi,
    private val userDao: UserDao,
    private val json: Json,
) {
    fun names(): Flow<Map<String, String>> = userDao.observeAll().map { users -> users.associate { it.accountId to it.displayName } }

    suspend fun ensure(accountIds: Collection<String>) {
        val wanted = accountIds.filter { it.isNotBlank() }.distinct()
        if (wanted.isEmpty()) return
        val missing = wanted - userDao.knownIds(wanted).toSet()
        if (missing.isEmpty()) return
        val session = sessionHolder.require()
        val url = session.jiraBaseUrl + "rest/api/3/user/bulk"
        val fetched = missing.chunked(50).flatMap { chunk ->
            runCatching { api(json) { json.decodeUsers(jiraApi.usersBulk(url, chunk)) } }.getOrDefault(emptyList())
        }
        userDao.insertAll(
            fetched.map { UserEntity(it.accountId, it.displayName ?: it.accountId, it.avatarUrls["48x48"]) }
        )
    }
}
