package fr.amorin.jicrade.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.amorin.jicrade.BuildConfig
import fr.amorin.jicrade.data.local.NotificationMode
import fr.amorin.jicrade.data.local.PollingMode
import fr.amorin.jicrade.ui.common.formatDateTime
import fr.amorin.jicrade.ui.common.horizonText
import fr.amorin.jicrade.ui.common.stripEmailDomain
import fr.amorin.jicrade.sync.Notifications
import fr.amorin.jicrade.sync.PhoneCalendar

private val intervals = listOf(30, 60, 120, 300)
private val priorities = listOf("P1", "P2", "P3", "P4", "P5")
private val notificationModes = listOf(NotificationMode.ON_CALL to "On call", NotificationMode.ALWAYS to "Always", NotificationMode.NEVER to "Never")
private val horizons = listOf(1 to "1 month", 3 to "3 months", 6 to "6 months", 12 to "1 year")
private val pollingModes = listOf(PollingMode.AUTO to "Auto", PollingMode.ON to "Always", PollingMode.OFF to "Off")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    var showRingtoneDialog by remember { mutableStateOf(false) }
    val ringtoneTitle by vm.ringtoneTitle.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { vm.stopSoundPreview() } }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.clearError() } }

    var hasNotificationPermission by remember { mutableStateOf(Notifications.canPost(context)) }
    val pm = remember { context.getSystemService<PowerManager>() }
    var ignoringBattery by remember { mutableStateOf(pm?.isIgnoringBatteryOptimizations(context.packageName) == true) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasNotificationPermission = Notifications.canPost(context)
        ignoringBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotificationPermission = granted || Notifications.canPost(context)
    }
    var calendarDenied by remember { mutableStateOf(false) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.all { it }
        calendarDenied = !granted
        if (granted) vm.setCalendarSync(true)
    }
    fun enableCalendarSync() {
        val missing = PhoneCalendar.PERMISSIONS.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) vm.setCalendarSync(true) else calendarPermission.launch(missing.toTypedArray())
    }

    fun ensureNotificationPermission() {
        if (!Notifications.canPost(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasNotificationPermission = true
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            val s = state.session
            ListItem(
                headlineContent = { Text(s?.displayName?.stripEmailDomain() ?: "") },
                supportingContent = { Text("${s?.credentials?.email?.stripEmailDomain() ?: ""} · ${s?.site ?: ""}.atlassian.net") },
            )
            ListItem(
                headlineContent = { Text("Team") },
                supportingContent = { Text(s?.teamName ?: "") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Change team",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { vm.loadTeams() },
            )
            HorizontalDivider()

            val notificationsOn = state.settings.notificationMode != NotificationMode.NEVER
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Notifications", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp)) {
                    notificationModes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = state.settings.notificationMode == mode,
                            onClick = { if (mode != NotificationMode.NEVER) ensureNotificationPermission(); vm.setNotificationMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, notificationModes.size),
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Text(
                    when (state.settings.notificationMode) {
                        NotificationMode.NEVER -> "Muted. Alerts are still synced but nothing is shown"
                        NotificationMode.ALWAYS -> "Every new alert notifies, on call or not"
                        NotificationMode.ON_CALL -> when {
                            state.onCallNow -> "You are on call now, new alerts will notify"
                            state.nextShiftStart != null -> "Not on call, silent until your shift on ${formatDateTime(state.nextShiftStart!!)}. Alerts addressed to you personally still notify"
                            else -> "Not on call, silent. Alerts addressed to you personally still notify"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Notify for priority", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp)) {
                    priorities.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = state.settings.minPriority == p,
                            onClick = { vm.setMinPriority(p) },
                            shape = SegmentedButtonDefaults.itemShape(i, priorities.size),
                            enabled = notificationsOn,
                        ) { Text(p) }
                    }
                }
                Text(
                    if (state.settings.minPriority == "P1") "Only P1 alerts trigger a notification"
                    else "P1 to ${state.settings.minPriority} alerts trigger a notification, lower priorities are only listed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            ListItem(
                headlineContent = { Text("Alert ringtone") },
                supportingContent = { Text(ringtoneTitle) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Change ringtone",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable(enabled = notificationsOn) {
                    vm.loadRingtones()
                    showRingtoneDialog = true
                },
            )
            ListItem(
                headlineContent = { Text("Ring until handled") },
                supportingContent = { Text("Repeat the sound until the alert is opened, acknowledged or dismissed") },
                trailingContent = { Switch(checked = state.settings.loopAlertSound, onCheckedChange = vm::setLoopAlertSound, enabled = notificationsOn) },
            )
            ListItem(
                headlineContent = { Text("Minimum alarm volume") },
                supportingContent = {
                    Text(
                        if (state.settings.overrideVolume) "Raises the alarm volume for alerts while on call, restored once handled"
                        else "Uses the current alarm volume"
                    )
                },
                trailingContent = { Switch(checked = state.settings.overrideVolume, onCheckedChange = vm::setOverrideVolume, enabled = notificationsOn) },
            )
            if (state.settings.overrideVolume && notificationsOn) {
                var volume by remember(state.settings.alertVolume) { mutableFloatStateOf(state.settings.alertVolume.toFloat()) }
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        onValueChangeFinished = { vm.setAlertVolume(volume.roundToInt()) },
                        valueRange = 10f..100f,
                        steps = 8,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("${volume.roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
            ListItem(
                headlineContent = { Text("Test alert notification") },
                supportingContent = { Text("Shows a sample alert with the current sound and volume") },
                trailingContent = {
                    FilledTonalButton(onClick = vm::sendTestNotification, enabled = notificationsOn && hasNotificationPermission) { Text("Test") }
                },
            )
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Fast polling", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp)) {
                    pollingModes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = state.settings.pollingMode == mode,
                            onClick = { if (mode != PollingMode.OFF) ensureNotificationPermission(); vm.setPollingMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, pollingModes.size),
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Text(
                    (if (state.pollingRunning) "Running: a foreground service checks for alerts every few seconds. " else "Not running. ") +
                        when (state.settings.pollingMode) {
                            PollingMode.AUTO -> "Starts and stops with your on-call shifts."
                            PollingMode.ON -> "Kept running until you turn it off."
                            PollingMode.OFF -> "Only the 15-minute background sync applies."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Fast polling interval", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp)) {
                    intervals.forEachIndexed { i, sec ->
                        SegmentedButton(
                            selected = state.settings.pollIntervalSeconds == sec,
                            onClick = { vm.setPollInterval(sec) },
                            shape = SegmentedButtonDefaults.itemShape(i, intervals.size),
                        ) { Text(if (sec < 60) "${sec}s" else "${sec / 60}m") }
                    }
                }
            }
            ListItem(
                headlineContent = { Text("Background sync") },
                supportingContent = { Text(if (state.settings.backgroundSync) "Checks for alerts every 15 minutes when fast polling is not running" else "Off. Alerts are only fetched when you open the app or while fast polling runs") },
                trailingContent = { Switch(checked = state.settings.backgroundSync, onCheckedChange = vm::setBackgroundSync) },
            )
            ListItem(
                headlineContent = { Text("Notify when someone else acknowledges") },
                trailingContent = { Switch(checked = state.settings.notifyOnAck, onCheckedChange = vm::setNotifyOnAck, enabled = notificationsOn) },
            )
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Show my shifts for", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp)) {
                    horizons.forEachIndexed { i, (months, label) ->
                        SegmentedButton(
                            selected = state.settings.horizonMonths == months,
                            onClick = { vm.setHorizonMonths(months) },
                            shape = SegmentedButtonDefaults.itemShape(i, horizons.size),
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Text(
                    "Used by My on-calls, the \"You\" line of each schedule and the calendar sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            ListItem(
                headlineContent = { Text("Sync my shifts to the phone calendar") },
                supportingContent = {
                    val c = state.settings
                    Text(
                        when {
                            calendarDenied -> "Calendar permission refused. Allow it to sync your shifts"
                            !c.calendarSync -> "Adds a \"Jicrade on-call\" calendar with your shifts for the ${horizonText(c.horizonMonths)}"
                            c.calendarSyncError != null -> "Last sync failed: ${c.calendarSyncError}"
                            c.calendarSyncedAt == 0L -> "Syncing…"
                            else -> "Last synced ${formatDateTime(c.calendarSyncedAt)} · ${c.calendarSyncedCount} shifts"
                        },
                        color = if (calendarDenied || (c.calendarSync && c.calendarSyncError != null)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.settings.calendarSync,
                        onCheckedChange = { on -> if (on) enableCalendarSync() else { calendarDenied = false; vm.setCalendarSync(false) } },
                    )
                },
            )
            HorizontalDivider()

            if (!hasNotificationPermission) {
                ListItem(
                    headlineContent = { Text("Notification permission", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Required to be alerted") },
                    modifier = Modifier.clickable { ensureNotificationPermission() },
                )
            }
            if (!ignoringBattery) {
                ListItem(
                    headlineContent = { Text("Battery optimisation") },
                    supportingContent = { Text("Tap to exempt the app so polling keeps running") },
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                )
            }
            ListItem(
                headlineContent = { Text("Notification channels") },
                supportingContent = { Text("Change sound, vibration and Do-Not-Disturb override") },
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Log out", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Removes the stored token and cached data") },
                modifier = Modifier.clickable { confirmLogout = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("Jicrade ${BuildConfig.VERSION_NAME}") },
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }

    state.teams?.let { teams ->
        var selectedTeam by remember(teams) {
            mutableStateOf(teams.firstOrNull { it.teamId == state.session?.teamId } ?: teams.firstOrNull())
        }
        AlertDialog(
            onDismissRequest = vm::dismissTeams,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            title = { Text("Choose team") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    teams.forEach { team ->
                        val isSelected = team.teamId == selectedTeam?.teamId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = { selectedTeam = team },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = team.teamName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val team = selectedTeam
                        if (team != null && team.teamId != state.session?.teamId) {
                            vm.switchTeam(team)
                        } else {
                            vm.dismissTeams()
                        }
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissTeams) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showRingtoneDialog) {
        val options by vm.ringtones.collectAsStateWithLifecycle()
        var selectedUri by remember { mutableStateOf(state.settings.alertSoundUri) }
        fun close() {
            vm.stopSoundPreview()
            showRingtoneDialog = false
        }
        AlertDialog(
            onDismissRequest = ::close,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            title = { Text("Alert ringtone") },
            text = {
                val list = options
                if (list == null) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        list.forEach { option ->
                            val isSelected = option.uri == selectedUri
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = isSelected,
                                        onClick = {
                                            selectedUri = option.uri
                                            vm.playSoundPreview(option.uri)
                                        },
                                        role = Role.RadioButton,
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.setAlertSoundUri(selectedUri)
                        close()
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = ::close) {
                    Text("Cancel")
                }
            },
        )
    }
}
