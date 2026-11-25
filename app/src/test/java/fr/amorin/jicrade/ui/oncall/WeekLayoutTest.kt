package fr.amorin.jicrade.ui.oncall

import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WeekLayoutTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val monday = LocalDate.of(2026, 11, 23)

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(monday.plusDays(day.toLong()), java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun period(start: Long, end: Long, vararg users: String, rotation: String = "r") =
        OnCallPeriodEntity(scheduleId = "s", rotationName = rotation, start = start, end = end, type = "base", responderIds = users.toList())

    @Test
    fun weekStartIsMonday() {
        assertEquals(monday, weekStartOf(at(3, 12), zone))
        assertEquals(monday, weekStartOf(at(0, 0), zone))
        assertEquals(monday, weekStartOf(at(6, 23, 59), zone))
    }

    @Test
    fun periodIsSplitAtMidnight() {
        val blocks = layoutWeek(listOf(period(at(0, 20), at(1, 8), "ana")), monday, zone)
        assertEquals(2, blocks.size)
        assertEquals(listOf(0, 20 * 60, MINUTES_PER_DAY), listOf(blocks[0].day, blocks[0].startMin, blocks[0].endMin))
        assertEquals(listOf(1, 0, 8 * 60), listOf(blocks[1].day, blocks[1].startMin, blocks[1].endMin))
    }

    @Test
    fun periodIsClippedToWeek() {
        val blocks = layoutWeek(listOf(period(at(-2, 12), at(9, 12), "ana")), monday, zone)
        assertEquals((0..6).toList(), blocks.map { it.day })
        assertTrue(blocks.all { it.startMin == 0 && it.endMin == MINUTES_PER_DAY })
    }

    @Test
    fun hoursLabels() {
        val blocks = layoutWeek(
            listOf(period(at(0, 0), at(0, 8, 30), "ana"), period(at(0, 8, 30), at(0, 20), "bob"), period(at(0, 20), at(2, 8), "carl")),
            monday,
            zone,
        )
        assertEquals(listOf("until 08:30", "08:30 – 20:00", "from 20:00", "all day", "until 08:00"), blocks.map { hoursLabel(it) })
    }

    @Test
    fun emptyPeriodsAreSkipped() {
        assertTrue(layoutWeek(listOf(period(at(0, 8), at(0, 20))), monday, zone).isEmpty())
    }

    @Test
    fun overlappingPeriodsGetLanes() {
        val blocks = layoutWeek(
            listOf(
                period(at(0, 8), at(0, 20), "ana", rotation = "a"),
                period(at(0, 12), at(0, 14), "bob", rotation = "b"),
                period(at(0, 20), at(0, 22), "carl", rotation = "a"),
            ),
            monday,
            zone,
        ).associateBy { it.period.responderIds.first() }
        assertEquals(0 to 2, blocks.getValue("ana").let { it.lane to it.lanes })
        assertEquals(1 to 2, blocks.getValue("bob").let { it.lane to it.lanes })
        assertEquals(0 to 1, blocks.getValue("carl").let { it.lane to it.lanes })
    }

    @Test
    fun dstDaysDoNotBreakLayout() {
        val fallBack = LocalDate.of(2026, 10, 19)
        val start = fallBack.startMillis(zone)
        val blocks = layoutWeek(listOf(period(start, fallBack.plusDays(7).startMillis(zone), "ana")), fallBack, zone)
        assertEquals(7, blocks.size)
        assertTrue(blocks.all { it.startMin == 0 && it.endMin == MINUTES_PER_DAY })
    }

    @Test
    fun colorsAreStableAndDistinct() {
        val ids = (1..8).map { "user-$it" }
        val colors = assignColors(ids, 12)
        assertEquals(ids.size, colors.values.toSet().size)
        assertEquals(colors, assignColors(ids.reversed(), 12))
        val single = assignColors(listOf("user-3"), 12)
        assertEquals(Math.floorMod("user-3".hashCode(), 12), single.getValue("user-3"))
    }

    @Test
    fun extendingColorsKeepsExistingOnes() {
        val first = assignColors(listOf("user-5", "user-9"), 12)
        val extended = extendColors(first, listOf("user-1", "user-5", "user-2"), 12)
        assertEquals(first, extended.filterKeys { it in first })
        assertEquals(4, extended.values.toSet().size)
    }

    @Test
    fun initialsFromNames() {
        assertEquals("AM", initials("Alice Martin"))
        assertEquals("JP", initials("Jean-Pierre Dupont Durand"))
        assertEquals("B", initials("bob"))
    }

    @Test
    fun colorsWrapWhenPaletteIsFull() {
        val colors = assignColors((1..5).map { "u$it" }, 3)
        assertEquals(5, colors.size)
        assertTrue(colors.values.all { it in 0..2 })
    }
}
