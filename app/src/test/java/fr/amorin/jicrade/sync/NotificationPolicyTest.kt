package fr.amorin.jicrade.sync

import fr.amorin.jicrade.data.local.AlertEntity
import fr.amorin.jicrade.data.local.NotificationMode
import fr.amorin.jicrade.data.local.PollingMode
import fr.amorin.jicrade.data.local.ResponderRef
import fr.amorin.jicrade.data.local.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {
    private val me = "me"

    private fun alert(id: String, priority: String, vararg responders: ResponderRef) = AlertEntity(
        id = id, tinyId = id, message = id, description = null, status = "open", priority = priority,
        acknowledged = false, count = 1, owner = null, source = null, entity = null, alias = null,
        tags = emptyList(), extraProperties = emptyMap(), responders = responders.toList(), integrationName = null,
        createdAt = 0, updatedAt = 0, lastOccurredAt = null, ackTime = null, closeTime = null,
        snoozed = false, snoozedUntil = null, seen = false,
    )

    private val p1 = alert("a", "P1")
    private val p3 = alert("b", "P3")
    private val p5 = alert("c", "P5")
    private val forMe = alert("d", "P2", ResponderRef("team-1", "team"), ResponderRef(me, "user"))
    private val all = listOf(p1, p3, p5, forMe)

    @Test
    fun neverSelectsNothing() {
        assertTrue(NotificationPolicy.select(all, Settings(notificationMode = NotificationMode.NEVER), onCall = true, accountId = me).isEmpty())
    }

    @Test
    fun alwaysAppliesPriorityThresholdOnly() {
        val prefs = Settings(notificationMode = NotificationMode.ALWAYS, minPriority = "P3")
        assertEquals(listOf(p1, p3, forMe), NotificationPolicy.select(all, prefs, onCall = false, accountId = me))
        assertEquals(listOf(p1), NotificationPolicy.select(all, prefs.copy(minPriority = "P1"), onCall = false, accountId = me))
    }

    @Test
    fun onCallModeNotifiesEverythingWhileOnCall() {
        val prefs = Settings(notificationMode = NotificationMode.ON_CALL, minPriority = "P5")
        assertEquals(all, NotificationPolicy.select(all, prefs, onCall = true, accountId = me))
    }

    @Test
    fun onCallModeOffDutyOnlyKeepsAlertsAddressedToMe() {
        val prefs = Settings(notificationMode = NotificationMode.ON_CALL, minPriority = "P5")
        assertEquals(listOf(forMe), NotificationPolicy.select(all, prefs, onCall = false, accountId = me))
        assertTrue(NotificationPolicy.select(all, prefs, onCall = false, accountId = "someone-else").isEmpty())
    }

    @Test
    fun pollingFollowsMode() {
        assertTrue(NotificationPolicy.pollingWanted(PollingMode.ON, onCall = false))
        assertFalse(NotificationPolicy.pollingWanted(PollingMode.OFF, onCall = true))
        assertTrue(NotificationPolicy.pollingWanted(PollingMode.AUTO, onCall = true))
        assertFalse(NotificationPolicy.pollingWanted(PollingMode.AUTO, onCall = false))
    }
}
