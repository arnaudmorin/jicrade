package fr.amorin.jicrade.sync

import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarPlanTest {
    private val hour = 3_600_000L
    private val now = 1_000 * hour

    private fun desired(start: Long, end: Long, key: String = "s@$start", title: String = "On-call · S") = DesiredEvent(key, title, start, end)

    private fun existing(id: Long, start: Long, end: Long, key: String? = "s@$start", title: String = "On-call · S") =
        ExistingEvent(id, key, title, start, end)

    @Test
    fun newShiftIsInserted() {
        val changes = planCalendar(listOf(desired(now + hour, now + 13 * hour)), emptyList(), now)
        assertEquals(1, changes.inserts.size)
        assertTrue(changes.updates.isEmpty() && changes.deletes.isEmpty())
    }

    @Test
    fun unchangedShiftIsSkipped() {
        val changes = planCalendar(listOf(desired(now + hour, now + 13 * hour)), listOf(existing(1, now + hour, now + 13 * hour)), now)
        assertTrue(changes.isEmpty)
    }

    @Test
    fun extendedOrRenamedShiftIsUpdated() {
        val changes = planCalendar(
            listOf(desired(now + hour, now + 20 * hour), desired(now + 30 * hour, now + 40 * hour, title = "On-call · S (override)")),
            listOf(existing(1, now + hour, now + 13 * hour), existing(2, now + 30 * hour, now + 40 * hour)),
            now,
        )
        assertEquals(listOf(1L, 2L), changes.updates.map { it.first })
        assertTrue(changes.inserts.isEmpty() && changes.deletes.isEmpty())
    }

    @Test
    fun removedFutureShiftIsDeletedAndPastShiftKept() {
        val changes = planCalendar(
            emptyList(),
            listOf(existing(1, now + hour, now + 13 * hour), existing(2, now - 20 * hour, now - 8 * hour)),
            now,
        )
        assertEquals(listOf(1L), changes.deletes)
    }

    @Test
    fun keylessAndDuplicateEventsAreDeleted() {
        val changes = planCalendar(
            listOf(desired(now + hour, now + 13 * hour)),
            listOf(existing(1, now + hour, now + 13 * hour), existing(2, now + hour, now + 13 * hour), existing(3, now - 5 * hour, now - hour, key = null)),
            now,
        )
        assertEquals(setOf(2L, 3L), changes.deletes.toSet())
        assertTrue(changes.inserts.isEmpty() && changes.updates.isEmpty())
    }

    @Test
    fun desiredEventsUseScheduleNameAndType() {
        val shifts = listOf(
            OnCallPeriodEntity(scheduleId = "a", rotationName = "r", start = 10, end = 20, type = "base", responderIds = listOf("me")),
            OnCallPeriodEntity(scheduleId = "b", rotationName = "r", start = 30, end = 40, type = "override", responderIds = listOf("me")),
        )
        val events = desiredEvents(shifts, mapOf("a" to "Platform"))
        assertEquals(listOf("On-call · Platform", "On-call · b (override)"), events.map { it.title })
        assertEquals(listOf("a@10", "b@30"), events.map { it.key })
    }
}
