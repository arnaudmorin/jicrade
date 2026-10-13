package fr.amorin.jicrade.data.repo

import fr.amorin.jicrade.data.auth.Credentials
import fr.amorin.jicrade.data.auth.Session
import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.data.local.JicradeDb
import fr.amorin.jicrade.data.local.SettingsStore
import fr.amorin.jicrade.data.remote.JiraApi
import fr.amorin.jicrade.data.remote.OpsApi
import fr.amorin.jicrade.data.remote.api
import fr.amorin.jicrade.data.remote.decodeTeams
import fr.amorin.jicrade.data.remote.dto.TeamDto
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class VerifiedAccount(
    val credentials: Credentials,
    val cloudId: String,
    val accountId: String,
    val displayName: String,
    val teams: List<TeamDto>,
)

@Singleton
class AuthRepository @Inject constructor(
    private val sessionHolder: SessionHolder,
    private val opsApi: OpsApi,
    private val jiraApi: JiraApi,
    private val json: Json,
    private val db: JicradeDb,
    private val settings: SettingsStore,
) {
    suspend fun verify(site: String, email: String, token: String, cloudIdOverride: String? = null): VerifiedAccount {
        val creds = Credentials(site.trim().removeSuffix(".atlassian.net"), email.trim(), token.trim())
        sessionHolder.pending = creds
        try {
            val cloudId = cloudIdOverride?.takeIf { it.isNotBlank() }
                ?: api(json, "cloud id lookup") { jiraApi.tenantInfo(creds.jiraBaseUrl + "_edge/tenant_info").cloudId }
            val me = api(json, "Jira profile") { jiraApi.myself(creds.jiraBaseUrl + "rest/api/3/myself") }
            return VerifiedAccount(creds, cloudId, me.accountId, me.displayName ?: me.emailAddress ?: creds.email, fetchTeams(cloudId))
        } finally {
            sessionHolder.pending = null
        }
    }

    fun login(account: VerifiedAccount, team: TeamDto) {
        sessionHolder.login(
            Session(
                credentials = account.credentials,
                cloudId = account.cloudId,
                accountId = account.accountId,
                displayName = account.displayName,
                teamId = team.teamId,
                teamName = team.teamName,
            )
        )
    }

    suspend fun teams(): List<TeamDto> = fetchTeams(sessionHolder.require().cloudId)

    private suspend fun fetchTeams(cloudId: String): List<TeamDto> =
        api(json, "Operations teams") { json.decodeTeams(opsApi.teams(cloudId)) }.sortedBy { it.teamName.lowercase() }

    suspend fun switchTeam(team: TeamDto) {
        sessionHolder.update { it.copy(teamId = team.teamId, teamName = team.teamName) }
        db.clearAll()
        settings.setLastSyncAt(0L)
    }

    suspend fun logout() {
        sessionHolder.logout()
        db.clearAll()
        settings.clear()
    }
}
