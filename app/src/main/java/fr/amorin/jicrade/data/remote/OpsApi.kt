package fr.amorin.jicrade.data.remote

import fr.amorin.jicrade.data.remote.dto.AlertDto
import fr.amorin.jicrade.data.remote.dto.ListAlertsDto
import fr.amorin.jicrade.data.remote.dto.ListSchedulesDto
import fr.amorin.jicrade.data.remote.dto.LogsPageDto
import fr.amorin.jicrade.data.remote.dto.NextOnCallUsersDto
import fr.amorin.jicrade.data.remote.dto.NoteDto
import fr.amorin.jicrade.data.remote.dto.NoteRequest
import fr.amorin.jicrade.data.remote.dto.NotesPageDto
import fr.amorin.jicrade.data.remote.dto.OnCallUsersDto
import fr.amorin.jicrade.data.remote.dto.SnoozeRequest
import fr.amorin.jicrade.data.remote.dto.SuccessDto
import fr.amorin.jicrade.data.remote.dto.TimelineDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OpsApi {
    @GET("jsm/ops/api/{cloudId}/v1/teams")
    suspend fun teams(@Path("cloudId") cloudId: String): JsonElement

    @GET("jsm/ops/api/{cloudId}/v1/alerts")
    suspend fun listAlerts(
        @Path("cloudId") cloudId: String,
        @Query("query") query: String,
        @Query("sort") sort: String = "createdAt",
        @Query("order") order: String = "desc",
        @Query("size") size: Int = 100,
        @Query("offset") offset: Int = 0,
    ): ListAlertsDto

    @GET("jsm/ops/api/{cloudId}/v1/alerts/{id}")
    suspend fun getAlert(@Path("cloudId") cloudId: String, @Path("id") id: String): AlertDto

    @POST("jsm/ops/api/{cloudId}/v1/alerts/{id}/acknowledge")
    suspend fun acknowledge(@Path("cloudId") cloudId: String, @Path("id") id: String): SuccessDto

    @POST("jsm/ops/api/{cloudId}/v1/alerts/{id}/unacknowledge")
    suspend fun unacknowledge(@Path("cloudId") cloudId: String, @Path("id") id: String): SuccessDto

    @POST("jsm/ops/api/{cloudId}/v1/alerts/{id}/close")
    suspend fun close(@Path("cloudId") cloudId: String, @Path("id") id: String): SuccessDto

    @POST("jsm/ops/api/{cloudId}/v1/alerts/{id}/snooze")
    suspend fun snooze(@Path("cloudId") cloudId: String, @Path("id") id: String, @Body body: SnoozeRequest): SuccessDto

    @GET("jsm/ops/api/{cloudId}/v1/alerts/{id}/notes")
    suspend fun notes(
        @Path("cloudId") cloudId: String,
        @Path("id") id: String,
        @Query("size") size: Int = 100,
        @Query("after") after: String? = null,
    ): NotesPageDto

    @POST("jsm/ops/api/{cloudId}/v1/alerts/{id}/notes")
    suspend fun addNote(@Path("cloudId") cloudId: String, @Path("id") id: String, @Body body: NoteRequest): NoteDto

    @GET("jsm/ops/api/{cloudId}/v1/alerts/{id}/logs")
    suspend fun logs(
        @Path("cloudId") cloudId: String,
        @Path("id") id: String,
        @Query("size") size: Int = 100,
        @Query("after") after: String? = null,
    ): LogsPageDto

    @GET("jsm/ops/api/{cloudId}/v1/schedules")
    suspend fun schedules(
        @Path("cloudId") cloudId: String,
        @Query("size") size: Int = 50,
        @Query("offset") offset: Int = 0,
    ): ListSchedulesDto

    @GET("jsm/ops/api/{cloudId}/v1/schedules/{scheduleId}/on-calls")
    suspend fun onCalls(
        @Path("cloudId") cloudId: String,
        @Path("scheduleId") scheduleId: String,
        @Query("flat") flat: Boolean = true,
    ): OnCallUsersDto

    @GET("jsm/ops/api/{cloudId}/v1/schedules/{scheduleId}/next-on-calls")
    suspend fun nextOnCalls(
        @Path("cloudId") cloudId: String,
        @Path("scheduleId") scheduleId: String,
        @Query("flat") flat: Boolean = true,
    ): NextOnCallUsersDto

    @GET("jsm/ops/api/{cloudId}/v1/schedules/{scheduleId}/timeline")
    suspend fun timeline(
        @Path("cloudId") cloudId: String,
        @Path("scheduleId") scheduleId: String,
        @Query("interval") interval: Int = 4,
        @Query("intervalUnit") intervalUnit: String = "weeks",
        @Query("date") date: String? = null,
    ): TimelineDto
}
