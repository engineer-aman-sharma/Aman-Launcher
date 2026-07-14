package com.example.amanslauncher.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** This launcher's own package — never shown in its Home grids, dock, or app drawer. */
const val SELF_LAUNCHER_PACKAGE = "com.example.amanslauncher"

data class LauncherApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap,
) {
    val componentName: ComponentName
        get() = ComponentName(packageName, activityName)
}

private val DOCK_PACKAGE_PREFERENCES = listOf(
    "com.google.android.dialer",
    "com.android.dialer",
    "com.samsung.android.dialer",
    "com.google.android.apps.messaging",
    "com.android.mms",
    "com.samsung.android.messaging",
    "com.android.chrome",
    "com.android.browser",
    "com.sec.android.app.sbrowser",
    "com.google.android.GoogleCamera",
    "com.android.camera2",
    "com.android.camera",
    "com.sec.android.app.camera",
    "com.android.settings",
)

/**
 * Central PackageManager load for every Home grid, dock, and app drawer list.
 * Filters out [SELF_LAUNCHER_PACKAGE] (and [excludePackage]) before sorting/returning.
 */
fun PackageManager.loadLaunchableApps(excludePackage: String): List<LauncherApp> {
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val excluded = linkedSetOf(SELF_LAUNCHER_PACKAGE, excludePackage).filter { it.isNotBlank() }.toSet()

    return queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        .asSequence()
        .filterNot { resolveInfo -> resolveInfo.resolvedPackageName() in excluded }
        .map { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo
            val packageName = resolveInfo.resolvedPackageName()
            LauncherApp(
                label = resolveInfo.loadLabel(this).toString(),
                packageName = packageName,
                activityName = activityInfo.name,
                icon = resolveInfo.loadIcon(this).toImageBitmap(),
            )
        }
        .excludingSelfLauncher()
        .sortedBy { it.label.lowercase() }
        .toList()
}

/** Drops this launcher from any app list before state, dock, or UI use. */
fun Sequence<LauncherApp>.excludingSelfLauncher(): Sequence<LauncherApp> =
    filterNot { it.packageName == SELF_LAUNCHER_PACKAGE }

fun List<LauncherApp>.excludingSelfLauncher(): List<LauncherApp> =
    filterNot { it.packageName == SELF_LAUNCHER_PACKAGE }

fun List<LauncherApp>.pickDockApps(count: Int = 4): List<LauncherApp> {
    val visible = excludingSelfLauncher()
    val byPackage = visible.associateBy { it.packageName }
    val selected = linkedSetOf<LauncherApp>()

    for (packageName in DOCK_PACKAGE_PREFERENCES) {
        if (selected.size >= count) break
        byPackage[packageName]?.let { selected += it }
    }

    for (app in visible) {
        if (selected.size >= count) break
        selected += app
    }

    return selected.take(count)
}

/**
 * Builds pager pages with Downloader immediately before Home, then workspace app grids.
 * Home remains the default start page (see [LAUNCHER_PAGE_HOME]).
 *
 * When [placements] is non-empty, apps are laid into sparse 4×4 grids so empty cells
 * (after removals / before drop) are real drop targets. Unplaced visible apps fill
 * remaining empty slots in label order.
 */
fun List<LauncherApp>.toLauncherPages(
    dockApps: List<LauncherApp>,
    placements: Map<String, HomePlacement> = emptyMap(),
    pageSize: Int = HOME_PAGE_APP_CAPACITY,
): List<LauncherPagerPage> {
    val visible = excludingSelfLauncher()
    val visibleDock = dockApps.excludingSelfLauncher()
    val dockKeys = visibleDock.map { it.componentKey }.toSet()
    val workspaceApps = visible.filterNot { it.componentKey in dockKeys }
    val byKey = workspaceApps.associateBy { it.componentKey }

    val appPages = if (placements.isEmpty()) {
        workspaceApps.chunked(pageSize).map { apps ->
            LauncherPagerPage.Workspace(apps.toSparseSlots(pageSize))
        }
    } else {
        buildSparseWorkspacePages(byKey, placements, pageSize)
    }

    return buildList {
        add(LauncherPagerPage.Downloader)
        add(LauncherPagerPage.Home)
        addAll(appPages)
    }
}

private fun List<LauncherApp>.toSparseSlots(pageSize: Int): List<LauncherApp?> {
    val slots = MutableList<LauncherApp?>(pageSize) { null }
    forEachIndexed { index, app ->
        if (index < pageSize) slots[index] = app
    }
    return slots
}

private fun buildSparseWorkspacePages(
    byKey: Map<String, LauncherApp>,
    placements: Map<String, HomePlacement>,
    pageSize: Int,
): List<LauncherPagerPage.Workspace> {
    val validPlacements = placements.filterKeys { it in byKey }
    val placedKeys = validPlacements.keys
    val remaining = byKey.keys
        .filterNot { it in placedKeys }
        .sortedBy { byKey.getValue(it).label.lowercase() }

    var pageCount = validPlacements.values.maxOfOrNull { it.pageIndex + 1 } ?: 0
    val maxOccupiedIndex = validPlacements.values.maxOfOrNull {
        it.pageIndex * pageSize + it.slotIndex
    } ?: -1
    val slotsNeeded = maxOccupiedIndex + 1 + remaining.size
    pageCount = maxOf(pageCount, (slotsNeeded + pageSize - 1) / pageSize, 1)

    val pages = MutableList(pageCount) { MutableList<LauncherApp?>(pageSize) { null } }

    for ((key, placement) in validPlacements) {
        val app = byKey[key] ?: continue
        while (placement.pageIndex >= pages.size) {
            pages += MutableList(pageSize) { null }
        }
        val page = pages[placement.pageIndex]
        if (page[placement.slotIndex] == null) {
            page[placement.slotIndex] = app
        }
    }

    var cursor = 0
    for (key in remaining) {
        val app = byKey[key] ?: continue
        while (true) {
            val pageIndex = cursor / pageSize
            val slotIndex = cursor % pageSize
            if (pageIndex >= pages.size) {
                pages += MutableList(pageSize) { null }
            }
            if (pages[pageIndex][slotIndex] == null) {
                pages[pageIndex][slotIndex] = app
                cursor++
                break
            }
            cursor++
        }
    }

    // Drop trailing completely empty pages (keep at least one workspace page if any apps).
    while (pages.size > 1 && pages.last().all { it == null }) {
        pages.removeAt(pages.lastIndex)
    }
    if (pages.isEmpty()) return emptyList()
    return pages.map { LauncherPagerPage.Workspace(it.toList()) }
}

sealed interface LauncherPagerPage {
    data object Home : LauncherPagerPage
    data object Downloader : LauncherPagerPage
    /** Sparse workspace grid; size is always [HOME_PAGE_APP_CAPACITY]. */
    data class Workspace(val slots: List<LauncherApp?>) : LauncherPagerPage
}

const val HOME_PAGE_APP_CAPACITY = 16
const val LAUNCHER_PAGE_DOWNLOADER = 0
const val LAUNCHER_PAGE_HOME = 1

private fun ResolveInfo.resolvedPackageName(): String {
    val fromActivity = activityInfo?.packageName?.takeIf { it.isNotBlank() }
    if (fromActivity != null) return fromActivity
    return activityInfo?.applicationInfo?.packageName.orEmpty()
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap.asImageBitmap()
    }

    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
