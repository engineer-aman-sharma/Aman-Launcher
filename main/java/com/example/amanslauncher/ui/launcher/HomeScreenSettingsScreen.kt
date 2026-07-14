package com.example.amanslauncher.ui.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreenSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding(),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Home Screen Settings",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Customize how Aman's Launcher looks and behaves on your Home Screen.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
            )

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(20.dp))

            SettingsHint(
                title = "App icons",
                body = "Long-press an app on Home or in the dock for shortcuts, App Info, Uninstall, or Remove from Home.",
            )
            Spacer(modifier = Modifier.height(20.dp))
            SettingsHint(
                title = "Wallpaper",
                body = "Long-press empty space on the Home Screen, then choose Wallpaper to open the system wallpaper picker.",
            )
            Spacer(modifier = Modifier.height(20.dp))
            SettingsHint(
                title = "All apps",
                body = "Swipe up from the Home Screen or tap the grid button in the dock to open the app drawer.",
            )
        }
    }
}

@Composable
private fun SettingsHint(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}
