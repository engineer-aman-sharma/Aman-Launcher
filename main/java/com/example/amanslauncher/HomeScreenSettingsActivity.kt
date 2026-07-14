package com.example.amanslauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.amanslauncher.ui.launcher.HomeScreenSettingsScreen
import com.example.amanslauncher.ui.theme.AmansLauncherTheme

class HomeScreenSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmansLauncherTheme(darkTheme = true, dynamicColor = false) {
                HomeScreenSettingsScreen(
                    onBack = { finish() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
