package fr.amorin.jicrade.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.amorin.jicrade.data.local.AlertEntity
import fr.amorin.jicrade.data.repo.parseInstant
import fr.amorin.jicrade.ui.common.PriorityChip
import fr.amorin.jicrade.ui.common.formatDateTimeYear
import fr.amorin.jicrade.ui.common.stripEmailDomain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailScreen(onBack: () -> Unit, vm: AlertDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.clearError() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.alert?.let { "#${it.tinyId}" } ?: "Alert") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val alert = state.alert
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (alert == null) {
                if (!state.loading) Text("Alert not found", Modifier.padding(16.dp))
                return@Scaffold
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Header(alert) }
                item { ActionBar(alert, state.busy, vm) }
                if (alert.description != null) item { Section("Description") { Text(alert.description) } }
                item {
                    Section("Details") {
                        DetailLine("Status", alert.status)
                        DetailLine("Created", formatDateTimeYear(alert.createdAt))
                        alert.lastOccurredAt?.let { DetailLine("Last occurred", formatDateTimeYear(it)) }
                        alert.ackTime?.let { DetailLine("Acknowledged", formatDateTimeYear(it)) }
                        alert.closeTime?.let { DetailLine("Closed", formatDateTimeYear(it)) }
                        alert.snoozedUntil?.let { DetailLine("Snoozed until", formatDateTimeYear(it)) }
                        alert.owner?.let { DetailLine("Owner", it.stripEmailDomain()) }
                        alert.source?.let { DetailLine("Source", it) }
                        alert.entity?.let { DetailLine("Entity", it) }
                        alert.integrationName?.let { DetailLine("Integration", it) }
                        alert.alias?.let { DetailLine("Alias", it) }
                        if (alert.count > 1) DetailLine("Count", alert.count.toString())
                        if (alert.tags.isNotEmpty()) DetailLine("Tags", alert.tags.joinToString(", "))
                        if (alert.responders.isNotEmpty()) {
                            DetailLine("Responders", alert.responders.joinToString { r ->
                                if (r.type == "user") (state.names[r.id] ?: r.id.takeLast(6)).stripEmailDomain() else "${r.type}:${r.id.take(8)}"
                            })
                        }
                    }
                }
                if (alert.extraProperties.isNotEmpty()) {
                    item {
                        Section("Extra properties") {
                            alert.extraProperties.toSortedMap().forEach { (k, v) -> DetailLine(k, v, mono = true) }
                        }
                    }
                }
                item { NotesSection(state, vm) }
                if (state.logs.isNotEmpty()) {
                    item {
                        Section("Activity") {
                            state.logs.forEach { log ->
                                val t = parseInstant(log.logTime)?.let { formatDateTimeYear(it) } ?: ""
                                Text("$t · ${log.owner.orEmpty().stripEmailDomain()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(log.log.orEmpty().stripEmailDomain(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(alert: AlertEntity) {
    Row(verticalAlignment = Alignment.Top) {
        PriorityChip(alert.priority)
        Spacer(Modifier.width(12.dp))
        Text(alert.message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActionBar(alert: AlertEntity, busy: Boolean, vm: AlertDetailViewModel) {
    var snoozeMenu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (alert.isOpen) Button(onClick = vm::acknowledge, enabled = !busy) { Text("Acknowledge") }
        if (alert.isAcked) OutlinedButton(onClick = vm::unacknowledge, enabled = !busy) { Text("Unack") }
        if (!alert.isClosed) OutlinedButton(onClick = vm::close, enabled = !busy) { Text("Close") }
        if (!alert.isClosed) {
            Column {
                OutlinedButton(onClick = { snoozeMenu = true }, enabled = !busy) { Text("Snooze") }
                DropdownMenu(expanded = snoozeMenu, onDismissRequest = { snoozeMenu = false }) {
                    listOf(1, 4, 8, 24).forEach { h ->
                        DropdownMenuItem(text = { Text("$h h") }, onClick = { snoozeMenu = false; vm.snooze(h) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = if (mono) FontFamily.Monospace else null)
    }
}

@Composable
private fun NotesSection(state: AlertDetailState, vm: AlertDetailViewModel) {
    var draft by remember { mutableStateOf("") }
    Section("Notes") {
        state.notes.forEach { note ->
            val t = parseInstant(note.createdAt)?.let { formatDateTimeYear(it) } ?: ""
            Text("${note.owner.orEmpty().stripEmailDomain()} · $t", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(note.note.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
        }
        OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("Add a note") }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { vm.addNote(draft.trim()); draft = "" }, enabled = draft.isNotBlank() && !state.busy) { Text("Post note") }
    }
}
