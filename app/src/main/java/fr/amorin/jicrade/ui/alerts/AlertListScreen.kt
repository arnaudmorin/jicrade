package fr.amorin.jicrade.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.amorin.jicrade.data.local.AlertEntity
import fr.amorin.jicrade.ui.common.EmptyState
import fr.amorin.jicrade.ui.common.PriorityChip
import fr.amorin.jicrade.ui.common.formatTime
import fr.amorin.jicrade.ui.common.relativeAge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertListScreen(onOpenAlert: (String) -> Unit, vm: AlertListViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AlertTab.OPEN) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.teamName.ifBlank { "Alerts" })
                        if (state.lastSyncAt > 0 || state.muted) {
                            Text(
                                listOfNotNull(
                                    state.lastSyncAt.takeIf { it > 0 }?.let { "Synced at ${formatTime(it)}" },
                                    "notifications muted".takeIf { state.muted },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab.ordinal) {
                AlertTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text("${t.label} (${state.forTab(t).size})") },
                    )
                }
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                val list = state.forTab(tab)
                if (list.isEmpty()) {
                    EmptyState(
                        title = if (tab == AlertTab.OPEN) "No open alerts" else "Nothing here",
                        subtitle = "Pull down to refresh",
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.id }) { alert ->
                            AlertRow(
                                alert = alert,
                                onClick = { onOpenAlert(alert.id) },
                                onAck = if (alert.isOpen) ({ vm.acknowledge(alert.id) }) else null,
                                onUnack = if (alert.isAcked) ({ vm.unacknowledge(alert.id) }) else null,
                                onClose = if (!alert.isClosed) ({ vm.close(alert.id) }) else null,
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: AlertEntity, onClick: () -> Unit, onAck: (() -> Unit)?, onUnack: (() -> Unit)?, onClose: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PriorityChip(alert.priority)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                alert.message,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (alert.isOpen) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("#${alert.tinyId}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(relativeAge(alert.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (alert.count > 1) Text("×${alert.count}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                alert.owner?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    ?: alert.source?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (onUnack != null) ActionIcon("Unack", onUnack) { Icon(Icons.Outlined.Undo, contentDescription = "Unacknowledge", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (onAck != null) ActionIcon("Ack", onAck) { Icon(Icons.Default.Check, contentDescription = "Acknowledge", tint = MaterialTheme.colorScheme.primary) }
        if (onClose != null) ActionIcon("Close", onClose) { Icon(Icons.Outlined.Cancel, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ActionIcon(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp), colors = IconButtonDefaults.iconButtonColors()) { icon() }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
