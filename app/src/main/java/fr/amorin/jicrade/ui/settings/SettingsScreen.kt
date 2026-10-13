package fr.amorin.jicrade.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.amorin.jicrade.BuildConfig

private val intervals = listOf(30, 60, 120, 300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.clearError() } }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            val s = state.session
            ListItem(
                headlineContent = { Text(s?.displayName ?: "") },
                supportingContent = { Text("${s?.credentials?.email ?: ""} · ${s?.site ?: ""}.atlassian.net") },
            )
            ListItem(
                headlineContent = { Text("Team") },
                supportingContent = { Text(s?.teamName ?: "") },
                trailingContent = { TextButton(onClick = vm::loadTeams) { Text("Change") } },
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Alert notifications") },
                supportingContent = { Text(if (state.settings.notificationsEnabled) "You will be notified of new alerts" else "Muted. Alerts are still synced but nothing is shown") },
                trailingContent = {
                    Switch(checked = state.settings.notificationsEnabled, onCheckedChange = { if (it) ensureNotificationPermission(); vm.setNotificationsEnabled(it) })
                },
            )
            ListItem(
                headlineContent = { Text("On-call mode") },
                supportingContent = { Text("Keeps a foreground service polling for new alerts every few seconds. Turn on while you are on duty.") },
                trailingContent = {
                    Switch(checked = state.settings.onCallMode, onCheckedChange = { ensureNotificationPermission(); vm.setOnCallMode(it) })
                },
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Poll interval in on-call mode", style = MaterialTheme.typography.labelLarge)
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
                supportingContent = { Text(if (state.settings.backgroundSync) "Checks for alerts every 15 minutes when on-call mode is off" else "Off. Alerts are only fetched when you open the app or in on-call mode") },
                trailingContent = { Switch(checked = state.settings.backgroundSync, onCheckedChange = vm::setBackgroundSync) },
            )
            ListItem(
                headlineContent = { Text("Notify when someone else acknowledges") },
                trailingContent = { Switch(checked = state.settings.notifyOnAck, onCheckedChange = vm::setNotifyOnAck, enabled = state.settings.notificationsEnabled) },
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Notification permission") },
                supportingContent = { Text("Required to be alerted") },
                modifier = Modifier.clickable { ensureNotificationPermission() },
            )
            val pm = context.getSystemService<PowerManager>()
            val ignoring = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            ListItem(
                headlineContent = { Text("Battery optimisation") },
                supportingContent = { Text(if (ignoring) "Unrestricted, background polling is reliable" else "Tap to exempt the app so polling keeps running") },
                modifier = Modifier.clickable {
                    if (!ignoring) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
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
            ListItem(headlineContent = { Text("Jicrade ${BuildConfig.VERSION_NAME}") })
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
        AlertDialog(
            onDismissRequest = vm::dismissTeams,
            title = { Text("Choose team") },
            text = {
                Column {
                    teams.forEach { t ->
                        ListItem(headlineContent = { Text(t.teamName) }, modifier = Modifier.clickable { vm.switchTeam(t) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissTeams) { Text("Cancel") } },
        )
    }
}
