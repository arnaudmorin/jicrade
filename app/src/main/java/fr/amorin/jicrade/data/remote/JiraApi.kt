package fr.amorin.jicrade.data.remote

import fr.amorin.jicrade.data.remote.dto.JiraUserDto
import fr.amorin.jicrade.data.remote.dto.TenantInfoDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface JiraApi {
    @GET
    suspend fun tenantInfo(@Url url: String): TenantInfoDto

    @GET
    suspend fun myself(@Url url: String): JiraUserDto

    @GET
    suspend fun usersBulk(@Url url: String, @Query("accountId") accountIds: List<String>, @Query("maxResults") max: Int = 128): JsonElement
}
