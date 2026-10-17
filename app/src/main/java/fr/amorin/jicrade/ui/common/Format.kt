package fr.amorin.jicrade.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM HH:mm")
private val dateTimeYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")
private val timeOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatDateTime(epochMillis: Long): String =
    dateTime.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun formatDateTimeYear(epochMillis: Long): String =
    dateTimeYear.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun formatTime(epochMillis: Long): String =
    timeOnly.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun relativeAge(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - epochMillis).coerceAtLeast(0) / 1000
    return when {
        diff < 60 -> "${diff}s"
        diff < 3600 -> "${diff / 60}m"
        diff < 86400 -> "${diff / 3600}h"
        else -> "${diff / 86400}d"
    }
}

fun relativeIn(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (epochMillis - now).coerceAtLeast(0) / 1000
    return when {
        diff < 3600 -> "in ${diff / 60} min"
        diff < 86400 -> "in ${diff / 3600} h"
        else -> "in ${diff / 86400} d"
    }
}

fun durationText(startMillis: Long, endMillis: Long): String {
    val hours = (endMillis - startMillis) / 3_600_000
    return if (hours % 24 == 0L && hours >= 24) "${hours / 24} d" else "$hours h"
}

private val emailRegex = Regex("""([A-Za-z0-9._%+-]+)@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*""")

fun String.stripEmailDomain(): String = replace(emailRegex, "$1")

