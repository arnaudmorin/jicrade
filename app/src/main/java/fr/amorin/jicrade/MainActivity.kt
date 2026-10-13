package fr.amorin.jicrade

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import fr.amorin.jicrade.data.auth.SessionHolder
import fr.amorin.jicrade.sync.Notifications
import fr.amorin.jicrade.ui.AppNav
import fr.amorin.jicrade.ui.Routes
import fr.amorin.jicrade.ui.setup.SetupScreen
import fr.amorin.jicrade.ui.theme.JicradeTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionHolder: SessionHolder

    private var pendingAlertId by mutableStateOf<String?>(null)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAlertId = intent?.getStringExtra(Notifications.EXTRA_ALERT_ID)
        setContent {
            JicradeTheme {
                val session by sessionHolder.session.collectAsStateWithLifecycle()
                if (session == null) {
                    SetupScreen()
                } else {
                    LaunchedEffect(Unit) { requestNotificationPermission() }
                    Main()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingAlertId = intent.getStringExtra(Notifications.EXTRA_ALERT_ID)
    }

    @Composable
    private fun Main() {
        val navController = rememberNavController()
        val target = pendingAlertId
        LaunchedEffect(target) {
            if (target != null) {
                navController.navigate(Routes.alert(target)) { launchSingleTop = true }
                pendingAlertId = null
            }
        }
        AppNav(navController)
    }

    private fun requestNotificationPermission() {
        if (!Notifications.canPost(this)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
