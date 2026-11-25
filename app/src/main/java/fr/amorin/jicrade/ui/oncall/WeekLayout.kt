package fr.amorin.jicrade.ui.oncall

import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

const val MINUTES_PER_DAY = 24 * 60

data class ShiftBlock(
    val day: Int,
    val startMin: Int,
    val endMin: Int,
    val lane: Int,
    val lanes: Int,
    val period: OnCallPeriodEntity,
)

fun weekStartOf(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun LocalDate.startMillis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

fun minuteOfDay(epochMillis: Long, zone: ZoneId): Int =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime().toSecondOfDay() / 60

fun layoutWeek(periods: List<OnCallPeriodEntity>, weekStart: LocalDate, zone: ZoneId): List<ShiftBlock> =
    (0 until 7).flatMap { d ->
        val dayStart = weekStart.plusDays(d.toLong()).startMillis(zone)
        val dayEnd = weekStart.plusDays(d + 1L).startMillis(zone)
        val raw = periods
            .filter { it.responderIds.isNotEmpty() && it.start < dayEnd && it.end > dayStart }
            .mapNotNull { p ->
                val startMin = if (p.start <= dayStart) 0 else minuteOfDay(p.start, zone)
                val endMin = if (p.end >= dayEnd) MINUTES_PER_DAY else minuteOfDay(p.end, zone)
                if (endMin > startMin) ShiftBlock(d, startMin, endMin, 0, 1, p) else null
            }
            .sortedWith(compareBy({ it.startMin }, { -it.endMin }))
        assignLanes(raw)
    }

private fun assignLanes(blocks: List<ShiftBlock>): List<ShiftBlock> {
    val out = mutableListOf<ShiftBlock>()
    var cluster = mutableListOf<ShiftBlock>()
    var clusterEnd = 0
    fun flush() {
        val laneEnds = mutableListOf<Int>()
        val placed = cluster.map { b ->
            val lane = laneEnds.indexOfFirst { it <= b.startMin }.takeIf { it >= 0 } ?: laneEnds.size.also { laneEnds += 0 }
            laneEnds[lane] = b.endMin
            b.copy(lane = lane)
        }
        out += placed.map { it.copy(lanes = laneEnds.size) }
        cluster = mutableListOf()
    }
    blocks.forEach { b ->
        if (cluster.isNotEmpty() && b.startMin >= clusterEnd) flush()
        clusterEnd = if (cluster.isEmpty()) b.endMin else maxOf(clusterEnd, b.endMin)
        cluster += b
    }
    if (cluster.isNotEmpty()) flush()
    return out
}

private fun hhmm(minutes: Int): String = "%02d:%02d".format(java.util.Locale.ROOT, minutes / 60, minutes % 60)

fun hoursLabel(b: ShiftBlock): String = when {
    b.startMin == 0 && b.endMin == MINUTES_PER_DAY -> "all day"
    b.startMin == 0 -> "until ${hhmm(b.endMin)}"
    b.endMin == MINUTES_PER_DAY -> "from ${hhmm(b.startMin)}"
    else -> "${hhmm(b.startMin)} – ${hhmm(b.endMin)}"
}

fun assignColors(ids: Collection<String>, paletteSize: Int): Map<String, Int> = extendColors(emptyMap(), ids, paletteSize)

fun extendColors(existing: Map<String, Int>, ids: Collection<String>, paletteSize: Int): Map<String, Int> {
    val fresh = ids.filter { it !in existing }.distinct().sorted()
    if (fresh.isEmpty()) return existing
    val used = BooleanArray(paletteSize).also { u -> existing.values.forEach { u[it] = true } }
    val out = LinkedHashMap(existing)
    fresh.forEach { id ->
        var slot = Math.floorMod(id.hashCode(), paletteSize)
        if (used.any { !it }) while (used[slot]) slot = (slot + 1) % paletteSize
        used[slot] = true
        out[id] = slot
    }
    return out
}

fun initials(name: String): String =
    name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
