package fr.amorin.jicrade.ui.settings

import android.Manifest
import android.content.Intent
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.amorin.jicrade.BuildConfig
import fr.amorin.jicrade.data.local.NotificationMode
import fr.amorin.jicrade.data.local.PollingMode
import fr.amorin.jicrade.ui.common.formatDateTime
import fr.amorin.jicrade.ui.common.stripEmailDomain
import fr.amorin.jicrade.sync.Notifications

import android.media.RingtoneManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Slider
import androidx.compose.runtime.DisposableEffect
import kotlin.math.roundToInt

private val intervals = listOf(30, 60, 120, 300)
private val priorities = listOf("P1", "P2", "P3", "P4", "P5")
private val notificationModes = listOf(NotificationMode.ON_CALL to "On call", NotificationMode.ALWAYS to "Always", NotificationMode.NEVER to "Never")
private val pollingModes = listOf(PollingMode.AUTO to "Auto", PollingMode.ON to "Always", PollingMode.OFF to "Off")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    var showRingtoneDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.clearError() } }

    DisposableEffect(Unit) {
        onDispose { vm.stopSoundPreview() }
    }

    val ringtoneTitle = remember(state.settings.alertSoundUri) {
        Notifications.ringtoneTitle(context, state.settings.alertSoundUri)
    }

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
            ListItem(
                headlineContent = { Text("Override Do Not Disturb") },
                supportingContent = { Text(if (state.settings.overrideDnd) "Alert notifications sound even in Do Not Disturb" else "Alert notifications respect Do Not Disturb") },
                trailingContent = { Switch(checked = state.settings.overrideDnd, onCheckedChange = vm::setOverrideDnd, enabled = notificationsOn) },
            )
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
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Sound & Volume", style = MaterialTheme.typography.labelLarge)
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
                modifier = Modifier.clickable {
                    vm.loadRingtones()
                    showRingtoneDialog = true
                },
            )
            ListItem(
                headlineContent = { Text("Repeat until acknowledged") },
                supportingContent = { Text("Loops sound and vibration until you open or acknowledge the alert") },
                trailingContent = {
                    Switch(
                        checked = state.settings.loopAlertSound,
                        onCheckedChange = vm::setLoopAlertSound,
                        enabled = notificationsOn,
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Override alarm volume") },
                supportingContent = { Text(if (state.settings.overrideVolume) "Force alert volume regardless of device volume" else "Uses system alarm volume") },
                trailingContent = {
                    Switch(
                        checked = state.settings.overrideVolume,
                        onCheckedChange = vm::setOverrideVolume,
                        enabled = notificationsOn,
                    )
                },
            )
            if (state.settings.overrideVolume) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Alert volume", style = MaterialTheme.typography.bodyMedium)
                        Text("${state.settings.alertVolume}%", style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = state.settings.alertVolume.toFloat(),
                        onValueChange = { vm.setAlertVolume(it.roundToInt()) },
                        onValueChangeFinished = { vm.playSoundPreview() },
                        valueRange = 10f..100f,
                        steps = 8,
                    )
                }
            }
            ListItem(
                headlineContent = { Text("Test alert notification") },
                supportingContent = { Text("Trigger a sample notification with current sound and volume") },
                trailingContent = {
                    FilledTonalButton(onClick = { vm.sendTestNotification() }, enabled = notificationsOn) {
                        Text("Test")
                    }
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
        val availableRingtones by vm.ringtones.collectAsStateWithLifecycle()
        var selectedUri by remember { mutableStateOf(state.settings.alertSoundUri) }

        AlertDialog(
            onDismissRequest = {
                vm.stopSoundPreview()
                showRingtoneDialog = false
            },
            title = { Text("Alert ringtone") },
            text = {
                if (availableRingtones == null) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        availableRingtones?.forEach { item ->
                            val isSelected = (item.uri == selectedUri) || (item.uri == null && selectedUri == null)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = isSelected,
                                        onClick = {
                                            selectedUri = item.uri
                                            val uriToPlay = item.uri?.let { Uri.parse(it) }
                                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                            vm.playSoundPreview(uriToPlay)
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
                                    text = item.title,
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
                        vm.stopSoundPreview()
                        vm.setAlertSoundUri(selectedUri)
                        showRingtoneDialog = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        vm.stopSoundPreview()
                        showRingtoneDialog = false
                    }
                ) { Text("Cancel") }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
    }
}
