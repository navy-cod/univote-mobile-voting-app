package com.univote.evoting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.univote.evoting.ui.navigation.AppNavHost
import com.univote.evoting.ui.theme.UniVoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as UniVoteApp).container
        setContent {
            UniVoteTheme {
                AppNavHost(container = container)
            }
        }
    }
}
