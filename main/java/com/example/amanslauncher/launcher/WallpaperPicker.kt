package com.example.amanslauncher.launcher

import android.content.Context
import android.content.Intent

/** Opens the system wallpaper picker / chooser. */
fun openSystemWallpaperPicker(context: Context) {
    val intent = Intent(Intent.ACTION_SET_WALLPAPER)
    runCatching {
        context.startActivity(Intent.createChooser(intent, null))
    }
}
