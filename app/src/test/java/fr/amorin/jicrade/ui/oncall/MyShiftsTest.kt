package fr.amorin.jicrade.ui.oncall

import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import fr.amorin.jicrade.data.repo.mergeShifts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

class MyShiftsTest {
    private val zone = ZoneId.of("Europe/Paris")

    private fun at(month: Int, day: Int, hour: Int, year: Int = 2026): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun shift(start: Long, end: Long, schedule: String = "s", type: String = "base") =
        OnCallPeriodEntity(scheduleId = schedule, rotationName = "r", start = start, end = end, type = type, responderIds = listOf("me"))

    @Test
    fun adjacentAndOverlappingShiftsAreMerged() {
        val merged = mergeShifts(
            listOf(
                shift(at(12, 5, 20), at(12, 6, 0)),
                shift(at(12, 6, 0), at(12, 6, 8)),
                shift(at(12, 6, 7), at(12, 6, 9), type = "override"),
                shift(at(12, 10, 8), at(12, 10, 20)),
            ),
        )
        assertEquals(2, merged.size)
        assertEquals(at(12, 5, 20) to at(12, 6, 9), merged[0].start to merged[0].end)
        assertEquals("override", merged[0].type)
    }

    @Test
    fun shiftsOfDifferentSchedulesAreNotMerged() {
        val merged = mergeShifts(listOf(shift(at(12, 5, 8), at(12, 5, 20), "a"), shift(at(12, 5, 20), at(12, 6, 8), "b")))
        assertEquals(listOf("a", "b"), merged.map { it.scheduleId })
    }

    @Test
    fun shiftsAreGroupedByStartMonth() {
        val groups = groupByMonth(
            listOf(shift(at(12, 31, 20), at(1, 1, 8, 2027)), shift(at(12, 5, 8), at(12, 5, 20)), shift(at(1, 3, 8, 2027), at(1, 3, 20, 2027))),
            zone,
        )
        assertEquals(listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 1)), groups.map { it.month })
        assertEquals(24 * 3_600_000L, groups[0].totalMs)
        assertEquals(2, groups[0].shifts.size)
        assertEquals(at(12, 5, 8), groups[0].shifts[0].start)
    }

    @Test
    fun labels() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            assertEquals("Sat 5 Dec 08:00 → 20:00", shiftLabel(at(12, 5, 8), at(12, 5, 20), zone))
            assertEquals("Thu 17 Dec 20:00 → Fri 08:00", shiftLabel(at(12, 17, 20), at(12, 18, 8), zone))
            assertEquals("Fri 1 Jan 08:00 → Mon 4 Jan 08:00", shiftLabel(at(1, 1, 8, 2027), at(1, 4, 8, 2027), zone))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
