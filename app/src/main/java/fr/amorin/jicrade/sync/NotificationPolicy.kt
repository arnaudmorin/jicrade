package fr.amorin.jicrade.sync

import fr.amorin.jicrade.data.local.AlertEntity
import fr.amorin.jicrade.data.local.NotificationMode
import fr.amorin.jicrade.data.local.PollingMode
import fr.amorin.jicrade.data.local.Settings

object NotificationPolicy {
    fun select(candidates: List<AlertEntity>, prefs: Settings, onCall: Boolean, accountId: String): List<AlertEntity> {
        val urgentEnough = candidates.filter { it.priority <= prefs.minPriority }
        return when (prefs.notificationMode) {
            NotificationMode.NEVER -> emptyList()
            NotificationMode.ALWAYS -> urgentEnough
            NotificationMode.ON_CALL -> urgentEnough.filter { onCall || it.addressedTo(accountId) }
        }
    }

    fun pollingWanted(mode: PollingMode, onCall: Boolean): Boolean = when (mode) {
        PollingMode.ON -> true
        PollingMode.OFF -> false
        PollingMode.AUTO -> onCall
    }

    private fun AlertEntity.addressedTo(accountId: String): Boolean =
        responders.any { it.type == "user" && it.id == accountId }
}
