package fr.amorin.jicrade.ui.oncall

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.amorin.jicrade.data.local.OnCallPeriodEntity
import fr.amorin.jicrade.ui.common.EmptyState
import fr.amorin.jicrade.ui.common.durationText
import fr.amorin.jicrade.ui.theme.personColors
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MyShiftsScreen(onBack: () -> Unit, vm: MyShiftsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    val monthTitle = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = {
                    Column {
                        Text("My on-calls")
                        val count = state.shifts?.size
                        Text(
                            if (count == null) "Next 6 months" else "Next 6 months · ${shiftsText(count)} · ${hoursText(state.totalMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = vm::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            val groups = state.groups
            when {
                state.shifts == null -> EmptyState("Loading your shifts…")
                groups.isEmpty() -> EmptyState("No on-call shift in the next 6 months", "Pull down to refresh")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    groups.forEach { g ->
                        stickyHeader(key = g.month.toString()) {
                            Text(
                                "${monthTitle.format(g.month).replaceFirstChar { it.uppercase() }} · ${shiftsText(g.shifts.size)} · ${hoursText(g.totalMs)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        items(g.shifts, key = { "${it.scheduleId}-${it.start}" }) { ShiftItem(it, state) }
                    }
                }
            }
        }
    }
}

private fun shiftsText(n: Int) = if (n == 1) "1 shift" else "$n shifts"

@Composable
private fun ShiftItem(p: OnCallPeriodEntity, state: MyShiftsState) {
    val now = System.currentTimeMillis()
    val current = p.start <= now && now < p.end
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(RoundedCornerShape(8.dp))
            .background(if (current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ColorDot(personColors[state.colors[p.scheduleId] ?: 0], 12)
        Column(Modifier.weight(1f)) {
            Text(shiftLabel(p.start, p.end, state.zone), style = MaterialTheme.typography.bodyMedium, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
            Text(
                (state.scheduleNames[p.scheduleId] ?: p.scheduleId) + if (p.type != "base") " · ${p.type}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (current) Text("now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(durationText(p.start, p.end), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
