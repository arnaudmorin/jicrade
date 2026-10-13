package fr.amorin.jicrade.data.repo

import fr.amorin.jicrade.data.local.AlertEntity
import fr.amorin.jicrade.data.local.ResponderRef
import fr.amorin.jicrade.data.remote.dto.AlertDto
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

fun parseInstant(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(value).toEpochMilli() }
        .getOrNull()
}

private val isoUtc: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")

fun formatIso(epochMillis: Long): String =
    isoUtc.format(Instant.ofEpochMilli(epochMillis).atOffset(java.time.ZoneOffset.UTC))

fun AlertDto.toEntity(now: Long = System.currentTimeMillis()): AlertEntity = AlertEntity(
    id = id,
    tinyId = tinyId ?: id.take(8),
    message = message ?: "",
    description = description?.takeIf { it.isNotBlank() },
    status = status ?: if (acknowledged) "acked" else "open",
    priority = priority ?: "P3",
    acknowledged = acknowledged,
    count = count,
    owner = owner?.takeIf { it.isNotBlank() },
    source = source?.takeIf { it.isNotBlank() },
    entity = entity?.takeIf { it.isNotBlank() },
    alias = alias?.takeIf { it.isNotBlank() },
    tags = tags,
    extraProperties = extraProperties?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: v.toString() } ?: emptyMap(),
    responders = responders.mapNotNull { r -> r.id?.let { ResponderRef(it, r.type ?: "user") } },
    integrationName = integrationName,
    createdAt = parseInstant(createdAt) ?: now,
    updatedAt = parseInstant(updatedAt) ?: now,
    lastOccurredAt = parseInstant(lastOccuredAt),
    ackTime = parseInstant(ackTime),
    closeTime = parseInstant(closeTime),
    snoozed = snoozed,
    snoozedUntil = parseInstant(snoozedUntil),
    seen = seen,
)
