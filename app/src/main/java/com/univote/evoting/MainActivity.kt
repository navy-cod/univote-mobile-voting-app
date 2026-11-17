package com.univote.evoting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.univote.evoting.ui.navigation.AppNavHost
import com.univote.evoting.ui.theme.UniVoteTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as UniVoteApp).container }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no action needed */ }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniVoteTheme {
                AppNavHost(container = container, onSignedIn = ::requestNotificationPermissionIfNeeded)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        container.sessionManager.validate() // an expired session signs the user out
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.sessionManager.touch() // any tap or swipe counts as activity
    }
}