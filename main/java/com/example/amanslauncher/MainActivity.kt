package com.example.amanslauncher

import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.amanslauncher.launcher.HOME_PAGE_APP_CAPACITY
import com.example.amanslauncher.launcher.HomeLayoutStore
import com.example.amanslauncher.launcher.HomePlacement
import com.example.amanslauncher.launcher.LAUNCHER_PAGE_HOME
import com.example.amanslauncher.launcher.LauncherApp
import com.example.amanslauncher.launcher.LauncherPagerPage
import com.example.amanslauncher.launcher.PackageChangeMonitor
import com.example.amanslauncher.launcher.SELF_LAUNCHER_PACKAGE
import com.example.amanslauncher.launcher.componentKey
import com.example.amanslauncher.launcher.excludingSelfLauncher
import com.example.amanslauncher.launcher.loadLaunchableApps
import com.example.amanslauncher.launcher.openSystemWallpaperPicker
import com.example.amanslauncher.launcher.pickDockApps
import com.example.amanslauncher.launcher.toLauncherPages
import com.example.amanslauncher.ui.launcher.HomeScreen
import com.example.amanslauncher.ui.theme.AmansLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestHomeRole = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* User may accept or decline; no further action required. */ }

    private val uninstallActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // User finished or canceled the system uninstall UI — refresh either way.
        refreshApps(debounceMs = 200L)
    }

    private lateinit var homeLayoutStore: HomeLayoutStore

    private var apps by mutableStateOf<List<LauncherApp>>(emptyList())
    private var dockApps by mutableStateOf<List<LauncherApp>>(emptyList())
    private var hiddenFromHomeKeys by mutableStateOf<Set<String>>(emptySet())
    private var workspacePlacements by mutableStateOf<Map<String, HomePlacement>>(emptyMap())
    private var isDrawerOpen by mutableStateOf(false)
    private var drawerScrollIndex by mutableIntStateOf(0)
    private var drawerScrollOffset by mutableIntStateOf(0)
    private var homePageIndex by mutableIntStateOf(LAUNCHER_PAGE_HOME)
    private var settleOnHomeRequest by mutableIntStateOf(0)

    private var refreshJob: Job? = null
    private val packageChangeMonitor = PackageChangeMonitor(
        onPackagesChanged = { refreshApps(debounceMs = 300L) },
    )

    private val uninstallResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_UNINSTALL_RESULT) return
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            )
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmIntent = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    if (confirmIntent != null) {
                        runCatching { uninstallActivityLauncher.launch(confirmIntent) }
                            .onFailure { startActivity(confirmIntent) }
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> refreshApps(debounceMs = 100L)
                else -> refreshApps(debounceMs = 200L)
            }
        }
    }

    private var uninstallReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeLayoutStore = HomeLayoutStore(this)
        hiddenFromHomeKeys = homeLayoutStore.hiddenFromHomeKeys()
        workspacePlacements = homeLayoutStore.placements()
        restoreLauncherState(savedInstanceState)
        enableEdgeToEdge()
        requestDefaultHomeIfNeeded()
        packageChangeMonitor.register(this)
        registerUninstallResultReceiver()
        refreshApps()

        setContent {
            AmansLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                ) {
                    val homeApps = remember(apps, hiddenFromHomeKeys) {
                        apps.filterNot { it.componentKey in hiddenFromHomeKeys }
                    }
                    val pages = remember(homeApps, dockApps, workspacePlacements, hiddenFromHomeKeys) {
                        val base = homeApps.toLauncherPages(dockApps, workspacePlacements)
                        // Keep one empty workspace page so removed apps can be dropped back.
                        if (base.none { it is LauncherPagerPage.Workspace } &&
                            hiddenFromHomeKeys.isNotEmpty()
                        ) {
                            base + LauncherPagerPage.Workspace(
                                List(HOME_PAGE_APP_CAPACITY) { null },
                            )
                        } else {
                            base
                        }
                    }
                    val pageCount = pages.size
                    val initialPage = homePageIndex.coerceIn(0, pageCount - 1)
                    val pagerState = rememberPagerState(
                        initialPage = initialPage,
                        pageCount = { pageCount },
                    )
                    val drawerGridState = rememberLazyGridState(
                        initialFirstVisibleItemIndex = drawerScrollIndex,
                        initialFirstVisibleItemScrollOffset = drawerScrollOffset,
                    )

                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }
                            .distinctUntilChanged()
                            .collect { page -> homePageIndex = page }
                    }

                    LaunchedEffect(pageCount) {
                        if (pagerState.currentPage >= pageCount) {
                            pagerState.scrollToPage(pageCount - 1)
                        }
                    }

                    LaunchedEffect(settleOnHomeRequest) {
                        if (settleOnHomeRequest > 0) {
                            pagerState.animateScrollToPage(LAUNCHER_PAGE_HOME)
                        }
                    }

                    LaunchedEffect(drawerGridState) {
                        snapshotFlow {
                            drawerGridState.firstVisibleItemIndex to
                                drawerGridState.firstVisibleItemScrollOffset
                        }
                            .distinctUntilChanged()
                            .collect { (index, offset) ->
                                drawerScrollIndex = index
                                drawerScrollOffset = offset
                            }
                    }

                    HomeScreen(
                        apps = apps,
                        dockApps = dockApps,
                        homeAppKeys = homeApps.map { it.componentKey }.toSet(),
                        pages = pages,
                        pagerState = pagerState,
                        isDrawerOpen = isDrawerOpen,
                        onDrawerOpenChange = { isDrawerOpen = it },
                        drawerGridState = drawerGridState,
                        onAppClick = ::launchApp,
                        onAppInfo = ::openAppInfo,
                        onUninstall = ::openUninstall,
                        onRemoveFromHome = ::removeFromHome,
                        onPlaceOnHome = ::placeOnHome,
                        onOpenWallpaper = ::openWallpaperPicker,
                        onOpenHomeSettings = ::openHomeScreenSettings,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Home button returns to the default Home page and closes the drawer.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            isDrawerOpen = false
            homePageIndex = LAUNCHER_PAGE_HOME
            settleOnHomeRequest++
        }
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_DRAWER_OPEN, isDrawerOpen)
        outState.putInt(KEY_DRAWER_SCROLL_INDEX, drawerScrollIndex)
        outState.putInt(KEY_DRAWER_SCROLL_OFFSET, drawerScrollOffset)
        outState.putInt(KEY_HOME_PAGE_INDEX, homePageIndex)
    }

    override fun onDestroy() {
        packageChangeMonitor.unregister(this)
        unregisterUninstallResultReceiver()
        refreshJob?.cancel()
        super.onDestroy()
    }

    private fun restoreLauncherState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        isDrawerOpen = savedInstanceState.getBoolean(KEY_DRAWER_OPEN, false)
        drawerScrollIndex = savedInstanceState.getInt(KEY_DRAWER_SCROLL_INDEX, 0)
        drawerScrollOffset = savedInstanceState.getInt(KEY_DRAWER_SCROLL_OFFSET, 0)
        homePageIndex = savedInstanceState.getInt(KEY_HOME_PAGE_INDEX, LAUNCHER_PAGE_HOME)
    }

    private fun refreshApps(debounceMs: Long = 0L) {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            homeLayoutStore.scrubSelfLauncherEntries()
            val launchable = withContext(Dispatchers.Default) {
                packageManager
                    .loadLaunchableApps(excludePackage = SELF_LAUNCHER_PACKAGE)
                    .excludingSelfLauncher()
            }
            apps = launchable
            hiddenFromHomeKeys = homeLayoutStore.hiddenFromHomeKeys()
            val homeApps = launchable.filterNot { it.componentKey in hiddenFromHomeKeys }
            dockApps = homeApps.pickDockApps()
            val dockKeys = dockApps.map { it.componentKey }.toSet()
            val workspaceKeys = homeApps
                .filterNot { it.componentKey in dockKeys }
                .map { it.componentKey }
            homeLayoutStore.ensureDefaultPlacements(workspaceKeys)
            homeLayoutStore.reconcilePlacements(launchable.map { it.componentKey }.toSet())
            workspacePlacements = homeLayoutStore.placements()
        }
    }

    private fun launchApp(app: LauncherApp) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = app.componentName
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        startActivity(intent)
    }

    private fun openAppInfo(app: LauncherApp) {
        if (app.packageName == SELF_LAUNCHER_PACKAGE) return
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${app.packageName}")
        }
        startActivity(intent)
    }

    private fun openUninstall(app: LauncherApp) {
        if (app.packageName == SELF_LAUNCHER_PACKAGE) return

        // Prefer PackageInstaller.uninstall — reliably presents the system confirmation UI.
        val usedInstaller = runCatching {
            val callback = Intent(ACTION_UNINSTALL_RESULT).setPackage(packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                app.packageName.hashCode() and 0xFFFF,
                callback,
                flags,
            )
            packageManager.packageInstaller.uninstall(app.packageName, pendingIntent.intentSender)
            true
        }.getOrDefault(false)

        if (usedInstaller) return

        // Fallback: classic uninstall intent (no NEW_TASK — this is a singleTask Home activity).
        val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${app.packageName}")
        }
        runCatching {
            uninstallActivityLauncher.launch(deleteIntent)
        }.recoverCatching {
            @Suppress("DEPRECATION")
            val legacy = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:${app.packageName}")
            }
            uninstallActivityLauncher.launch(legacy)
        }
    }

    private fun removeFromHome(app: LauncherApp) {
        if (app.packageName == SELF_LAUNCHER_PACKAGE) return
        homeLayoutStore.hideFromHome(app)
        hiddenFromHomeKeys = homeLayoutStore.hiddenFromHomeKeys()
        workspacePlacements = homeLayoutStore.placements()
        val homeApps = apps.filterNot { it.componentKey in hiddenFromHomeKeys }
        dockApps = homeApps.pickDockApps()
    }

    private fun placeOnHome(app: LauncherApp, placement: HomePlacement): Boolean {
        if (app.packageName == SELF_LAUNCHER_PACKAGE) return false
        val key = app.componentKey
        // Already visible on Home or dock — no duplicates.
        if (key !in hiddenFromHomeKeys && apps.any { it.componentKey == key }) return false
        if (placement.slotIndex !in 0 until HOME_PAGE_APP_CAPACITY || placement.pageIndex < 0) {
            return false
        }
        val placed = homeLayoutStore.placeOnHome(app, placement)
        if (!placed) return false
        hiddenFromHomeKeys = homeLayoutStore.hiddenFromHomeKeys()
        workspacePlacements = homeLayoutStore.placements()
        val homeApps = apps.filterNot { it.componentKey in hiddenFromHomeKeys }
        dockApps = homeApps.pickDockApps()
        return true
    }

    private fun openWallpaperPicker() {
        openSystemWallpaperPicker(this)
    }

    private fun openHomeScreenSettings() {
        startActivity(Intent(this, HomeScreenSettingsActivity::class.java))
    }

    private fun registerUninstallResultReceiver() {
        if (uninstallReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            uninstallResultReceiver,
            IntentFilter(ACTION_UNINSTALL_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        uninstallReceiverRegistered = true
    }

    private fun unregisterUninstallResultReceiver() {
        if (!uninstallReceiverRegistered) return
        runCatching { unregisterReceiver(uninstallResultReceiver) }
        uninstallReceiverRegistered = false
    }

    private fun requestDefaultHomeIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java) ?: return
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                requestHomeRole.launch(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                )
            }
        } else if (!isDefaultHomeApp()) {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
            )
        }
    }

    private fun isDefaultHomeApp(): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private companion object {
        const val KEY_DRAWER_OPEN = "drawer_open"
        const val KEY_DRAWER_SCROLL_INDEX = "drawer_scroll_index"
        const val KEY_DRAWER_SCROLL_OFFSET = "drawer_scroll_offset"
        const val KEY_HOME_PAGE_INDEX = "home_page_index"
        const val ACTION_UNINSTALL_RESULT = "com.example.amanslauncher.UNINSTALL_RESULT"
    }
}
