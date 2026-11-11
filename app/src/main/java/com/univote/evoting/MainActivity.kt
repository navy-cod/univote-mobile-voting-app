package com.univote.evoting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.univote.evoting.ui.navigation.AppNavHost
import com.univote.evoting.ui.theme.UniVoteTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as UniVoteApp).container }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        setContent {
            UniVoteTheme {
                AppNavHost(container = container)
            }
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
