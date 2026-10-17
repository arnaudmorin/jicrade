package fr.amorin.jicrade.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class ResponderRef(val id: String, val type: String)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val tinyId: String,
    val message: String,
    val description: String? = null,
    val status: String,
    val priority: String,
    val acknowledged: Boolean = false,
    val count: Int = 1,
    val owner: String? = null,
    val source: String? = null,
    val entity: String? = null,
    val alias: String? = null,
    val tags: List<String> = emptyList(),
    val extraProperties: Map<String, String> = emptyMap(),
    val responders: List<ResponderRef> = emptyList(),
    val integrationName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOccurredAt: Long? = null,
    val ackTime: Long? = null,
    val closeTime: Long? = null,
    val snoozed: Boolean = false,
    val snoozedUntil: Long? = null,
    val seen: Boolean = false,
) {
    val isClosed: Boolean get() = status == "closed"
    val isAcked: Boolean get() = status == "acked" || (acknowledged && !isClosed)
    val isOpen: Boolean get() = !isClosed && !isAcked
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val accountId: String,
    val displayName: String,
    val avatarUrl: String?,
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val teamId: String?,
    val timezone: String?,
    val enabled: Boolean,
    val onCallIds: List<String>,
    val nextOnCallIds: List<String>,
    val fetchedAt: Long,
)

@Entity(tableName = "on_call_periods")
data class OnCallPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: String,
    val rotationName: String,
    val start: Long,
    val end: Long,
    val type: String,
    val responderIds: List<String>,
)
