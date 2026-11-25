package fr.amorin.jicrade.sync

import fr.amorin.jicrade.data.local.OnCallPeriodEntity

data class DesiredEvent(val key: String, val title: String, val start: Long, val end: Long)

data class ExistingEvent(val id: Long, val key: String?, val title: String, val start: Long, val end: Long)

data class CalendarChanges(
    val inserts: List<DesiredEvent>,
    val updates: List<Pair<Long, DesiredEvent>>,
    val deletes: List<Long>,
) {
    val isEmpty: Boolean get() = inserts.isEmpty() && updates.isEmpty() && deletes.isEmpty()
}

fun desiredEvents(shifts: List<OnCallPeriodEntity>, scheduleNames: Map<String, String>): List<DesiredEvent> =
    shifts.map { s ->
        val name = scheduleNames[s.scheduleId] ?: s.scheduleId
        DesiredEvent(
            key = "${s.scheduleId}@${s.start}",
            title = "On-call · $name" + if (s.type != "base") " (${s.type})" else "",
            start = s.start,
            end = s.end,
        )
    }

fun planCalendar(desired: List<DesiredEvent>, existing: List<ExistingEvent>, now: Long): CalendarChanges {
    val byKey = existing.filter { it.key != null }.groupBy { it.key!! }
    val wanted = desired.associateBy { it.key }
    val inserts = mutableListOf<DesiredEvent>()
    val updates = mutableListOf<Pair<Long, DesiredEvent>>()
    val deletes = existing.filter { it.key == null }.map { it.id }.toMutableList()
    wanted.values.forEach { d ->
        val matches = byKey[d.key].orEmpty()
        val keep = matches.firstOrNull()
        when {
            keep == null -> inserts += d
            keep.title != d.title || keep.start != d.start || keep.end != d.end -> updates += keep.id to d
        }
        deletes += matches.drop(1).map { it.id }
    }
    byKey.filterKeys { it !in wanted }.values.flatten().filter { it.end > now }.forEach { deletes += it.id }
    return CalendarChanges(inserts, updates, deletes)
}
