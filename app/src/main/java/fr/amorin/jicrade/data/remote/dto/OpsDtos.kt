package fr.amorin.jicrade.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ResponderDto(val id: String? = null, val type: String? = null)

@Serializable
data class AlertDto(
    val id: String,
    val tinyId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val message: String? = null,
    val entity: String? = null,
    val source: String? = null,
    val status: String? = null,
    val alias: String? = null,
    val tags: List<String> = emptyList(),
    val extraProperties: JsonObject? = null,
    val description: String? = null,
    val acknowledged: Boolean = false,
    val ackTime: String? = null,
    val closeTime: String? = null,
    val count: Int = 0,
    val owner: String? = null,
    val snoozed: Boolean = false,
    val snoozedUntil: String? = null,
    val lastOccuredAt: String? = null,
    val integrationType: String? = null,
    val integrationName: String? = null,
    val priority: String? = null,
    val responders: List<ResponderDto> = emptyList(),
    val actions: List<String> = emptyList(),
    val seen: Boolean = false,
)

@Serializable
data class LinksDto(val next: String? = null)

@Serializable
data class ListAlertsDto(val values: List<AlertDto> = emptyList(), val count: Int? = null, val links: LinksDto? = null)

@Serializable
data class SuccessDto(val result: String? = null, val requestId: String? = null, val took: Double? = null)

@Serializable
data class RequestStatusDto(
    val action: String? = null,
    val processedAt: String? = null,
    val isSuccess: Boolean? = null,
    val status: String? = null,
    val alertId: String? = null,
)

@Serializable
data class NoteDto(
    val id: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val note: String? = null,
    val owner: String? = null,
)

@Serializable
data class NotesPageDto(val values: List<NoteDto> = emptyList(), val links: LinksDto? = null)

@Serializable
data class LogDto(
    val logTime: String? = null,
    val logType: String? = null,
    val log: String? = null,
    val owner: String? = null,
)

@Serializable
data class LogsPageDto(val values: List<LogDto> = emptyList(), val links: LinksDto? = null)

@Serializable
data class SnoozeRequest(val endTime: String)

@Serializable
data class NoteRequest(val note: String)

@Serializable
data class TeamDto(val teamId: String, val teamName: String)

@Serializable
data class TeamsDto(val platformTeams: List<TeamDto> = emptyList())

@Serializable
data class ScheduleDto(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val timezone: String? = null,
    val enabled: Boolean = true,
    val teamId: String? = null,
)

@Serializable
data class ListSchedulesDto(val values: List<ScheduleDto> = emptyList(), val links: LinksDto? = null)

@Serializable
data class OnCallUsersDto(val onCallUsers: List<String> = emptyList())

@Serializable
data class NextOnCallUsersDto(val nextOnCallUsers: List<String> = emptyList())

@Serializable
data class TimelineResponderDto(val id: String? = null, val type: String? = null, val deleted: Boolean = false)

@Serializable
data class TimelinePeriodDto(
    val startDate: String? = null,
    val endDate: String? = null,
    val type: String? = null,
    val responder: TimelineResponderDto? = null,
    val flattenedResponders: List<TimelineResponderDto> = emptyList(),
)

@Serializable
data class TimelineRotationDto(
    val id: String? = null,
    val name: String? = null,
    val order: Double? = null,
    val deleted: Boolean = false,
    val periods: List<TimelinePeriodDto> = emptyList(),
)

@Serializable
data class TimelineLayerDto(val rotations: List<TimelineRotationDto> = emptyList())

@Serializable
data class TimelineDto(
    val startDate: String? = null,
    val endDate: String? = null,
    val finalTimeline: TimelineLayerDto? = null,
)

@Serializable
data class ApiErrorDto(val title: String? = null, val code: String? = null)

@Serializable
data class ApiErrorsDto(val errors: List<ApiErrorDto> = emptyList(), val message: String? = null, val code: Int? = null)
