package fr.amorin.jicrade.data.repo

import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnCallShiftTest {
    private val me = "me"
    private val hour = 3_600_000L
    private val now = 100 * hour

    private fun period(start: Long, end: Long, vararg users: String) =
        OnCallPeriodEntity(scheduleId = "s", rotationName = "r", start = start, end = end, type = "base", responderIds = users.toList())

    private val periods = listOf(
        period(now - 30 * hour, now - 20 * hour, me),
        period(now - 20 * hour, now + 4 * hour, "bob"),
        period(now + 4 * hour, now + 28 * hour, "alice"),
        period(now + 28 * hour, now + 52 * hour, me),
        period(now + 52 * hour, now + 76 * hour, me),
    )

    @Test
    fun nextShiftIsFirstFuturePeriodContainingMe() {
        assertEquals(now + 28 * hour, periods.nextShift(me, now)?.start)
    }

    @Test
    fun currentShiftIsNullWhenSomeoneElseIsOnCall() {
        assertNull(periods.currentShift(me, now))
        assertEquals("bob", periods.currentShift("bob", now)?.responderIds?.single())
    }

    @Test
    fun currentShiftFoundWhenInsideMyPeriod() {
        val t = now + 30 * hour
        assertEquals(now + 28 * hour, periods.currentShift(me, t)?.start)
        assertEquals(now + 52 * hour, periods.nextShift(me, t)?.start)
    }
}
