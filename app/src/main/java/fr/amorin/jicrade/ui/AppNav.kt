package fr.amorin.jicrade.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import fr.amorin.jicrade.ui.alerts.AlertDetailScreen
import fr.amorin.jicrade.ui.alerts.AlertListScreen
import fr.amorin.jicrade.ui.oncall.OnCallScreen
import fr.amorin.jicrade.ui.settings.SettingsScreen

object Routes {
    const val ALERTS = "alerts"
    const val ONCALL = "oncall"
    const val SETTINGS = "settings"
    const val ALERT = "alert/{id}"
    fun alert(id: String) = "alert/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.ALERTS, "Alerts", Icons.Default.Notifications),
    Tab(Routes.ONCALL, "On-call", Icons.Default.Schedule),
    Tab(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
fun AppNav(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = tabs.any { it.route == current }
    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.ALERTS, modifier = Modifier.padding(padding)) {
            composable(Routes.ALERTS) { AlertListScreen(onOpenAlert = { navController.navigate(Routes.alert(it)) }) }
            composable(Routes.ONCALL) { OnCallScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.ALERT, arguments = listOf(navArgument("id") { })) {
                AlertDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
