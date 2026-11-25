package fr.amorin.jicrade.ui.oncall

import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class MonthGroup(val month: YearMonth, val shifts: List<OnCallPeriodEntity>, val totalMs: Long)

fun groupByMonth(shifts: List<OnCallPeriodEntity>, zone: ZoneId): List<MonthGroup> =
    shifts.sortedBy { it.start }
        .groupBy { YearMonth.from(Instant.ofEpochMilli(it.start).atZone(zone)) }
        .map { (month, list) -> MonthGroup(month, list, list.sumOf { it.end - it.start }) }

fun shiftLabel(start: Long, end: Long, zone: ZoneId): String {
    val s = Instant.ofEpochMilli(start).atZone(zone)
    val e = Instant.ofEpochMilli(end).atZone(zone)
    val days = ChronoUnit.DAYS.between(s.toLocalDate(), e.toLocalDate())
    val dayTime = DateTimeFormatter.ofPattern("EEE d MMM HH:mm")
    val endText = when (days) {
        0L -> DateTimeFormatter.ofPattern("HH:mm").format(e)
        1L -> DateTimeFormatter.ofPattern("EEE HH:mm").format(e)
        else -> dayTime.format(e)
    }
    return "${dayTime.format(s)} → $endText"
}

fun hoursText(ms: Long): String = "${ms / 3_600_000} h"
