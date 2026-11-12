package com.univote.evoting

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.univote.evoting.core.notifications.NotificationIntents
import com.univote.evoting.ui.navigation.AppNavHost
import com.univote.evoting.ui.theme.UniVoteTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as UniVoteApp).container }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh launch: after rotation the same intent is redelivered and must not reopen the inbox.
        if (savedInstanceState == null) handleNotificationIntent(intent)

        setContent {
            UniVoteTheme {
                AppNavHost(container = container)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationIntents.EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            container.openNotificationsRequested.value = true
            intent.removeExtra(NotificationIntents.EXTRA_OPEN_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        container.sessionManager.validate()   // expired session -> signed out
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.sessionManager.touch()      // any tap/swipe counts as activity
    }
}
