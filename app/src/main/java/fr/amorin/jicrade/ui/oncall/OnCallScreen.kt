package fr.amorin.jicrade.ui.oncall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.amorin.jicrade.ui.common.EmptyState
import fr.amorin.jicrade.ui.common.durationText
import fr.amorin.jicrade.ui.common.formatDateTime
import fr.amorin.jicrade.ui.common.relativeIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnCallScreen(vm: OnCallViewModel = hiltViewModel()) {
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
                    items(state.schedules, key = { it.id }) { card -> ScheduleCardView(card, state) }
                }
            }
        }
    }
}

@Composable
private fun ScheduleCardView(card: ScheduleCard, state: OnCallState) {
    val iAmOnCall = card.myCurrentShift != null || state.accountId in card.onCallNow
    Card(
        Modifier.fillMaxWidth(),
        colors = if (iAmOnCall) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Line("Now", card.onCallNow.joinToString { state.name(it) }.ifBlank { "nobody" }, bold = true)
            Line("Next", card.onCallNext.joinToString { state.name(it) }.ifBlank { "nobody" })
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            when {
                card.myCurrentShift != null -> Line("You", "on call until ${formatDateTime(card.myCurrentShift.end)}", bold = true)
                iAmOnCall -> Line("You", "on call now", bold = true)
                card.myNextShift != null -> Line("You", "next ${formatDateTime(card.myNextShift.start)} (${relativeIn(card.myNextShift.start)}), ${durationText(card.myNextShift.start, card.myNextShift.end)}")
                else -> Line("You", "no shift in the next 4 weeks")
            }
            if (card.upcoming.isNotEmpty()) {
                Text("Upcoming", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                card.upcoming.forEach { p ->
                    val mine = state.accountId in p.responderIds
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            formatDateTime(p.start),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(130.dp),
                        )
                        Text(
                            p.responderIds.joinToString { state.name(it) }.ifBlank { "nobody" } + if (p.type != "base") " (${p.type})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
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
