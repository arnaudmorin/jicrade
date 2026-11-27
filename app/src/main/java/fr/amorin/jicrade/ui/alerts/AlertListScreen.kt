package fr.amorin.jicrade.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.amorin.jicrade.data.local.AlertEntity
import fr.amorin.jicrade.ui.common.EmptyState
import fr.amorin.jicrade.ui.common.PriorityBadge
import fr.amorin.jicrade.ui.common.formatTime
import fr.amorin.jicrade.ui.common.relativeAge
import fr.amorin.jicrade.ui.common.stripEmailDomain
import fr.amorin.jicrade.ui.theme.priorityColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val priorities = listOf("P1", "P2", "P3", "P4", "P5")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertListScreen(onOpenAlert: (String) -> Unit, vm: AlertListViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = AlertTab.OPEN.ordinal, pageCount = { AlertTab.entries.size })
    val coroutineScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showFilter by remember { mutableStateOf(false) }
    val filtered = state.maxPriority != priorities.last()

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.teamName.ifBlank { "Alerts" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                        if (state.lastSyncAt > 0 || state.status.label != null) {
                            Text(
                                listOfNotNull(
                                    state.lastSyncAt.takeIf { it > 0 }?.let { "Synced ${formatTime(it)}" },
                                    state.status.label,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    val description = if (filtered) "Filter priorities, P1 to ${state.maxPriority} shown" else "Filter priorities"
                    if (filtered) {
                        BadgedBox(badge = { Badge() }, modifier = Modifier.padding(end = 8.dp)) {
                            FilledTonalIconButton(onClick = { showFilter = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = description)
                            }
                        }
                    } else {
                        IconButton(onClick = { showFilter = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = description)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                AlertTab.entries.forEachIndexed { index, t ->
                    val count = state.forTab(t).size
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(t.label)
                                if (t == AlertTab.OPEN && count > 0) Badge { Text("$count") }
                                else if (t != AlertTab.OPEN) Text("$count", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                    )
                }
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val tab = AlertTab.entries[page]
                    val list = state.forTab(tab)
                    val hidden = state.hidden[tab] ?: 0
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (hidden > 0) {
                            item(key = "hidden") {
                                HiddenNotice(hidden, state.maxPriority, onShowAll = { vm.setMaxPriority(priorities.last()) })
                            }
                        }
                        if (list.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    title = if (tab == AlertTab.OPEN) "No open alerts" else "Nothing here",
                                    subtitle = "Pull down to refresh",
                                    modifier = Modifier.fillParentMaxSize(),
                                )
                            }
                        }
                        items(list, key = { it.id }) { alert ->
                            AlertRow(
                                alert = alert,
                                onClick = { onOpenAlert(alert.id) },
                                onAck = { vm.acknowledge(alert.id) },
                                onUnack = { vm.unacknowledge(alert.id) },
                                onClose = { vm.close(alert.id) },
                            )
                            HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    if (showFilter) {
        PriorityFilterSheet(
            current = state.maxPriority,
            hidden = state.hidden.values.sum(),
            onApply = vm::setMaxPriority,
            onDismiss = { showFilter = false },
        )
    }
}

@Composable
private fun HiddenNotice(hidden: Int, maxPriority: String, onShowAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "P1–$maxPriority · $hidden lower-priority hidden",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShowAll) { Text("Show all") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityFilterSheet(current: String, hidden: Int, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    var position by remember { mutableFloatStateOf(priorities.indexOf(current).coerceAtLeast(0).toFloat()) }
    val selected = priorities[position.roundToInt()]
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Show alerts up to", style = MaterialTheme.typography.headlineSmall)
            Text(
                when {
                    selected == priorities.last() -> "All priorities shown"
                    selected == current && hidden > 0 -> "P1 to $selected shown · $hidden lower-priority hidden"
                    else -> "P1 to $selected shown"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = position,
                onValueChange = { position = it },
                onValueChangeFinished = { position = position.roundToInt().toFloat() },
                valueRange = 0f..(priorities.size - 1).toFloat(),
                steps = priorities.size - 2,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                priorities.forEachIndexed { i, p ->
                    val included = i <= position.roundToInt()
                    Text(
                        p,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (included) FontWeight.Bold else FontWeight.Normal,
                        color = if (included) priorityColors(p).second else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { onApply(priorities.last()); onDismiss() }) { Text("Show all") }
                Button(onClick = { onApply(selected); onDismiss() }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: AlertEntity, onClick: () -> Unit, onAck: () -> Unit, onUnack: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PriorityBadge(alert.priority)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                alert.message,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (alert.isOpen) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    "#${alert.tinyId}",
                    relativeAge(alert.createdAt),
                    alert.count.takeIf { it > 1 }?.let { "×$it" },
                    alert.owner?.stripEmailDomain() ?: alert.source,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row {
            if (!alert.isClosed) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Cancel, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (alert.isOpen) {
                FilledTonalIconButton(onClick = onAck) { Icon(Icons.Default.Check, contentDescription = "Acknowledge") }
            }
            if (alert.isAcked) {
                IconButton(onClick = onUnack) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Unacknowledge", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
