package com.example.amanslauncher.launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * A shortcut ready for display in the long-press popup (label + resolved icon).
 */
data class ShortcutMenuItem(
    val info: ShortcutInfo,
    val label: String,
    val icon: ImageBitmap,
)

/**
 * Loads app shortcuts published by a package for display in the launcher long-press menu.
 * Uses only [LauncherApps] — no hardcoded per-app shortcuts.
 */
object AppShortcutLoader {

    private const val MATCH_FLAGS =
        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED

    private const val ICON_SIZE_DP = 40

    fun queryShortcutMenuItems(context: Context, app: LauncherApp): List<ShortcutMenuItem> {
        val shortcuts = queryShortcuts(context, app)
        if (shortcuts.isEmpty()) return emptyList()

        val densityDpi = context.resources.displayMetrics.densityDpi
        val sizePx = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val launcherApps = context.getSystemService(LauncherApps::class.java)

        return shortcuts.map { shortcut ->
            val icon = resolveShortcutIcon(
                launcherApps = launcherApps,
                shortcut = shortcut,
                densityDpi = densityDpi,
                sizePx = sizePx,
                fallback = app.icon,
            )
            ShortcutMenuItem(
                info = shortcut,
                label = shortcutLabel(shortcut),
                icon = icon,
            )
        }
    }

    fun queryShortcuts(context: Context, app: LauncherApp): List<ShortcutInfo> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val user = Process.myUserHandle()

        val forActivity = runCatching {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setActivity(app.componentName)
                .setQueryFlags(MATCH_FLAGS)
            launcherApps.getShortcuts(query, user)
        }.getOrNull()

        val shortcuts = if (!forActivity.isNullOrEmpty()) {
            forActivity
        } else {
            runCatching {
                val query = LauncherApps.ShortcutQuery()
                    .setPackage(app.packageName)
                    .setQueryFlags(MATCH_FLAGS)
                launcherApps.getShortcuts(query, user)
            }.getOrNull().orEmpty()
        }

        return shortcuts
            .filter { it.isEnabled }
            .sortedWith(
                compareBy<ShortcutInfo> { it.rank }
                    .thenBy { shortcutLabel(it) },
            )
    }

    fun shortcutLabel(shortcut: ShortcutInfo): String =
        shortcut.shortLabel?.toString()?.takeIf { it.isNotBlank() }
            ?: shortcut.longLabel?.toString()?.takeIf { it.isNotBlank() }
            ?: shortcut.id

    fun startShortcut(context: Context, shortcut: ShortcutInfo) {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return
        runCatching {
            launcherApps.startShortcut(shortcut, null, null)
        }.recoverCatching {
            launcherApps.startShortcut(
                shortcut.`package`,
                shortcut.id,
                null,
                null,
                Process.myUserHandle(),
            )
        }
    }

    private fun resolveShortcutIcon(
        launcherApps: LauncherApps?,
        shortcut: ShortcutInfo,
        densityDpi: Int,
        sizePx: Int,
        fallback: ImageBitmap,
    ): ImageBitmap {
        if (launcherApps == null) return fallback
        val drawable = runCatching {
            launcherApps.getShortcutBadgedIconDrawable(shortcut, densityDpi)
                ?: launcherApps.getShortcutIconDrawable(shortcut, densityDpi)
        }.getOrNull()
        return drawable?.toImageBitmap(sizePx) ?: fallback
    }
}

/**
 * Rasterizes any [Drawable], including [android.graphics.drawable.AdaptiveIconDrawable], to a bitmap.
 */
internal fun Drawable.toImageBitmap(sizePx: Int = intrinsicWidth.coerceAtLeast(1)): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null && !bitmap.isRecycled) {
        if (bitmap.width == sizePx && bitmap.height == sizePx) {
            return bitmap.asImageBitmap()
        }
    }
    val width = sizePx.coerceAtLeast(1)
    val height = sizePx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
