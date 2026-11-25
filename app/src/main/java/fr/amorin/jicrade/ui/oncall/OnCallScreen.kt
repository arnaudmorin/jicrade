package fr.amorin.jicrade.ui.oncall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.amorin.jicrade.ui.common.EmptyState
import fr.amorin.jicrade.ui.common.durationText
import fr.amorin.jicrade.ui.common.formatDateTime
import fr.amorin.jicrade.ui.common.relativeIn
import fr.amorin.jicrade.ui.theme.personColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnCallScreen(onOpenSchedule: (String) -> Unit, onOpenMyShifts: () -> Unit, vm: OnCallViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.clearError() } }

    Scaffold(topBar = { TopAppBar(title = { Text("On-call") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.schedules.isEmpty()) {
                EmptyState(
                    title = if (state.loadedOnce) "No schedule for this team" else "Loading schedules…",
                    subtitle = if (state.loadedOnce) "Pull down to refresh" else null,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item(key = "mine") { MyShiftsCard(state.mine, onClick = onOpenMyShifts) }
                    items(state.schedules, key = { it.id }) { card -> ScheduleCardView(card, state, onClick = { onOpenSchedule(card.id) }) }
                }
            }
        }
    }
}

@Composable
private fun MyShiftsCard(mine: MyNextShift?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (mine?.current == true) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("My on-calls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                when {
                    mine == null -> "No shift in the next 4 weeks"
                    mine.current -> "On call until ${formatDateTime(mine.period.end)} · ${mine.scheduleName}"
                    else -> "Next ${formatDateTime(mine.period.start)} (${relativeIn(mine.period.start)}) · ${mine.scheduleName}"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (mine?.current == true) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text("See next 6 months ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ScheduleCardView(card: ScheduleCard, state: OnCallState, onClick: () -> Unit) {
    val iAmOnCall = card.myCurrentShift != null || state.accountId in card.onCallNow
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (iAmOnCall) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            People("Now", card.onCallNow, card, state, bold = true)
            People("Next", card.onCallNext, card, state)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            when {
                card.myCurrentShift != null -> Line("You", "on call until ${formatDateTime(card.myCurrentShift.end)}", bold = true)
                iAmOnCall -> Line("You", "on call now", bold = true)
                card.myNextShift != null -> Line("You", "next ${formatDateTime(card.myNextShift.start)} (${relativeIn(card.myNextShift.start)}), ${durationText(card.myNextShift.start, card.myNextShift.end)}")
                else -> Line("You", "no shift in the next 4 weeks")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun People(label: String, ids: List<String>, card: ScheduleCard, state: OnCallState, bold: Boolean = false) {
    if (ids.isEmpty()) return Line(label, "nobody", bold)
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ids.forEach { id ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(personColors[card.colors[id] ?: 0])
                    Text(
                        state.name(id),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}
