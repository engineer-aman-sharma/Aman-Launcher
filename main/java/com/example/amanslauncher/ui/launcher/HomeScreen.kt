package com.example.amanslauncher.ui.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.example.amanslauncher.launcher.AppShortcutLoader
import com.example.amanslauncher.launcher.HOME_PAGE_APP_CAPACITY
import com.example.amanslauncher.launcher.HomePlacement
import com.example.amanslauncher.launcher.LAUNCHER_PAGE_HOME
import com.example.amanslauncher.launcher.LauncherApp
import com.example.amanslauncher.launcher.LauncherPagerPage
import com.example.amanslauncher.launcher.SELF_LAUNCHER_PACKAGE
import com.example.amanslauncher.launcher.ShortcutMenuItem
import com.example.amanslauncher.launcher.componentKey
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DrawerSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

private const val DRAWER_OPEN_PROGRESS = 0.28f
private const val DRAWER_OPEN_VELOCITY = -1100f
private const val DRAWER_CLOSE_VELOCITY = 1100f

/**
 * Gesture-driven Home ↔ App Drawer progress.
 *
 * Drag updates [progress] synchronously (no per-frame coroutines).
 * Settle uses [Animatable] so release animates smoothly to 0 or 1.
 */
@Stable
private class HomeDrawerState(initialOpen: Boolean) {
    var progress by mutableFloatStateOf(if (initialOpen) 1f else 0f)
        private set

    var isDragging by mutableStateOf(false)
        private set

    private val animatable = Animatable(if (initialOpen) 1f else 0f)

    val isVisible: Boolean get() = progress > 0.001f || isDragging

    /**
     * Drawer keeps receiving close gestures while open or while mid-drag,
     * so swipe-down isn't cancelled when [isDragging] flips true.
     */
    val drawerInteractive: Boolean
        get() = progress >= 0.995f || (isDragging && progress > 0.02f)

    fun beginDrag() {
        isDragging = true
    }

    /** Finger-follow update — must stay synchronous for 60fps tracking. */
    fun updateProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
    }

    fun applyDragDeltaY(deltaY: Float, heightPx: Float) {
        if (heightPx <= 0f) return
        updateProgress(progress - deltaY / heightPx)
    }

    fun shouldOpenOnRelease(velocityY: Float): Boolean = when {
        velocityY > DRAWER_CLOSE_VELOCITY -> false
        velocityY < DRAWER_OPEN_VELOCITY -> true
        else -> progress >= DRAWER_OPEN_PROGRESS
    }

    suspend fun settleTo(open: Boolean) {
        isDragging = false
        val target = if (open) 1f else 0f
        animatable.snapTo(progress)
        if (abs(progress - target) < 0.001f) {
            progress = target
            animatable.snapTo(target)
            return
        }
        try {
            animatable.animateTo(target, DrawerSpring) {
                progress = value
            }
            progress = target
        } catch (e: CancellationException) {
            // Interrupted by a new drag — keep current progress.
            throw e
        }
    }
}

@Stable
private class IconDragState {
    var active by mutableStateOf(false)
        private set
    var app by mutableStateOf<LauncherApp?>(null)
        private set
    /** Finger position in window coordinates. */
    var fingerWindow by mutableStateOf(Offset.Zero)
        private set
    var candidate by mutableStateOf<HomePlacement?>(null)
        private set

    fun begin(app: LauncherApp, fingerWindow: Offset) {
        this.app = app
        this.fingerWindow = fingerWindow
        this.candidate = null
        this.active = true
    }

    fun updateFinger(fingerWindow: Offset, candidate: HomePlacement?) {
        this.fingerWindow = fingerWindow
        this.candidate = candidate
    }

    fun end() {
        active = false
        app = null
        candidate = null
        fingerWindow = Offset.Zero
    }
}

@Composable
fun HomeScreen(
    apps: List<LauncherApp>,
    dockApps: List<LauncherApp>,
    homeAppKeys: Set<String>,
    pages: List<LauncherPagerPage>,
    pagerState: PagerState,
    isDrawerOpen: Boolean,
    onDrawerOpenChange: (Boolean) -> Unit,
    drawerGridState: LazyGridState,
    onAppClick: (LauncherApp) -> Unit,
    onAppInfo: (LauncherApp) -> Unit,
    onUninstall: (LauncherApp) -> Unit,
    onRemoveFromHome: (LauncherApp) -> Unit,
    onPlaceOnHome: (LauncherApp, HomePlacement) -> Boolean,
    onOpenWallpaper: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val touchSlop = viewConfiguration.touchSlop
    val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
    val currentPage = pages.getOrNull(pagerState.currentPage)
    val isDownloaderPage = currentPage is LauncherPagerPage.Downloader
    val showHomeChrome = !isDownloaderPage
    var showWorkspaceOptions by remember { mutableStateOf(false) }

    val drawerState = remember { HomeDrawerState(initialOpen = isDrawerOpen) }
    val drawerProgress = drawerState.progress
    val isDrawerDragging = drawerState.isDragging
    val drawerVisible = drawerState.isVisible
    val drawerInteractive = drawerState.drawerInteractive
    val iconDragState = remember { IconDragState() }
    val isIconDragging = iconDragState.active
    var heightPxSafe by remember { mutableFloatStateOf(1f) }

    // Freeze the apps list while dragging so PackageManager refreshes never hitch the gesture.
    var cachedDrawerApps by remember { mutableStateOf(apps) }
    LaunchedEffect(apps, isDrawerDragging, isIconDragging) {
        if (!isDrawerDragging && !isIconDragging) {
            cachedDrawerApps = apps
        }
    }

    val onDrawerOpenChangeState = rememberUpdatedState(onDrawerOpenChange)
    val onPlaceOnHomeState = rememberUpdatedState(onPlaceOnHome)
    val pagesState = rememberUpdatedState(pages)
    val homeAppKeysState = rememberUpdatedState(homeAppKeys)
    val onWorkspaceLongPressState = rememberUpdatedState {
        if (drawerProgress < 0.02f && !isDrawerOpen && !isIconDragging) {
            showWorkspaceOptions = true
        }
    }

    // Current workspace grid bounds in window coordinates (for drop hit-testing).
    var workspaceGridBounds by remember { mutableStateOf<Rect?>(null) }
    var workspaceGridPageIndex by remember { mutableIntStateOf(-1) }

    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun settleDrawerAsync(open: Boolean) {
        settleJob?.cancel()
        settleJob = scope.launch {
            drawerState.settleTo(open)
            onDrawerOpenChangeState.value(open)
        }
    }

    fun beginDrawerDrag() {
        showWorkspaceOptions = false
        settleJob?.cancel()
        settleJob = null
        cachedDrawerApps = apps
        drawerState.beginDrag()
    }

    fun resolveDropCandidate(fingerWindow: Offset): HomePlacement? {
        val bounds = workspaceGridBounds ?: return null
        if (!bounds.contains(fingerWindow)) return null
        val pageIndex = workspaceGridPageIndex
        if (pageIndex < 0) return null
        val page = pagesState.value.filterIsInstance<LauncherPagerPage.Workspace>()
            .getOrNull(pageIndex) ?: return null
        val localX = fingerWindow.x - bounds.left
        val localY = fingerWindow.y - bounds.top
        val col = (localX / (bounds.width / 4f)).toInt().coerceIn(0, 3)
        val row = (localY / (bounds.height / 4f)).toInt().coerceIn(0, 3)
        val slot = row * 4 + col
        if (slot !in 0 until HOME_PAGE_APP_CAPACITY) return null
        if (page.slots.getOrNull(slot) != null) return null
        val dragApp = iconDragState.app ?: return null
        if (dragApp.componentKey in homeAppKeysState.value) return null
        return HomePlacement(pageIndex = pageIndex, slotIndex = slot)
    }

    fun onIconDragStart(app: LauncherApp, fingerWindow: Offset) {
        showWorkspaceOptions = false
        settleJob?.cancel()
        settleJob = null
        cachedDrawerApps = apps
        drawerState.beginDrag()
        iconDragState.begin(app, fingerWindow)
        if (heightPxSafe > 0f) {
            drawerState.updateProgress((fingerWindow.y / heightPxSafe).coerceIn(0f, 1f))
        }
        // Ensure a workspace grid is visible for drop targeting.
        val pagesNow = pagesState.value
        if (pagesNow.getOrNull(pagerState.currentPage) !is LauncherPagerPage.Workspace) {
            val workspacePagerIndex = pagesNow.indexOfFirst { it is LauncherPagerPage.Workspace }
            if (workspacePagerIndex >= 0) {
                scope.launch { pagerState.scrollToPage(workspacePagerIndex) }
            }
        }
        iconDragState.updateFinger(fingerWindow, resolveDropCandidate(fingerWindow))
    }

    fun onIconDrag(fingerWindow: Offset) {
        if (!iconDragState.active) return
        if (heightPxSafe > 0f) {
            drawerState.updateProgress((fingerWindow.y / heightPxSafe).coerceIn(0f, 1f))
        }
        iconDragState.updateFinger(fingerWindow, resolveDropCandidate(fingerWindow))
    }

    fun onIconDragEnd() {
        if (!iconDragState.active) return
        val app = iconDragState.app
        val candidate = iconDragState.candidate
        iconDragState.end()
        var placed = false
        if (app != null && candidate != null) {
            placed = onPlaceOnHomeState.value(app, candidate)
        }
        // Successful drop closes the drawer; cancel settles by progress.
        settleDrawerAsync(open = if (placed) false else drawerState.shouldOpenOnRelease(0f))
    }

    fun onIconDragCancel() {
        if (!iconDragState.active) return
        iconDragState.end()
        settleDrawerAsync(drawerState.shouldOpenOnRelease(0f))
    }

    // Keep progress in sync with external open/close (Back, dock grid button, restore).
    LaunchedEffect(isDrawerOpen) {
        if (drawerState.isDragging || iconDragState.active) return@LaunchedEffect
        val targetOpen = isDrawerOpen
        val target = if (targetOpen) 1f else 0f
        if (abs(drawerState.progress - target) > 0.01f) {
            settleJob?.cancel()
            settleJob = scope.launch {
                drawerState.settleTo(targetOpen)
            }
        }
        if (isDrawerOpen) {
            showWorkspaceOptions = false
        }
    }

    LaunchedEffect(isDownloaderPage) {
        if (isDownloaderPage) showWorkspaceOptions = false
    }

    BackHandler(enabled = showWorkspaceOptions || isDrawerOpen || drawerVisible || isDownloaderPage || isIconDragging) {
        when {
            isIconDragging -> onIconDragCancel()
            showWorkspaceOptions -> showWorkspaceOptions = false
            isDrawerOpen || drawerVisible -> settleDrawerAsync(false)
            isDownloaderPage -> scope.launch {
                pagerState.animateScrollToPage(LAUNCHER_PAGE_HOME)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Transparent),
    ) {
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        heightPxSafe = heightPx
        // Continuous vertical pages: Home leaves upward; Drawer enters from below.
        // Use layout offset (not graphicsLayer) so hit-testing follows each page 1:1.
        val homeOffsetY = (-drawerProgress * heightPx).roundToInt()
        val drawerOffsetY = ((1f - drawerProgress) * heightPx).roundToInt()
        val homeInFront = drawerProgress < 0.5f || isIconDragging

        // Full-screen App Drawer page (cached; sibling of Home, not drawn over it).
        AppDrawer(
            interactive = drawerInteractive && !isIconDragging,
            apps = cachedDrawerApps,
            homeAppKeys = homeAppKeys,
            gridState = drawerGridState,
            heightPx = heightPx,
            onDragStart = { beginDrawerDrag() },
            onDragDeltaY = { deltaY -> drawerState.applyDragDeltaY(deltaY, heightPx) },
            onAppClick = onAppClick,
            onAppInfo = onAppInfo,
            onUninstall = onUninstall,
            onRemoveFromHome = onRemoveFromHome,
            onIconDragStart = ::onIconDragStart,
            onIconDrag = ::onIconDrag,
            onIconDragEnd = ::onIconDragEnd,
            onIconDragCancel = ::onIconDragCancel,
            isIconDragging = isIconDragging,
            onSettle = { velocityY ->
                if (!isIconDragging) {
                    settleDrawerAsync(drawerState.shouldOpenOnRelease(velocityY))
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, drawerOffsetY) }
                .zIndex(if (homeInFront) 0f else 1f),
        )

        // Full-screen Home page — clock, icons, dock, and chrome all move together.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, homeOffsetY) }
                .zIndex(if (homeInFront) 1f else 0f),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = drawerProgress < 0.02f &&
                    !isDrawerDragging &&
                    !showWorkspaceOptions &&
                    !isIconDragging,
                beyondViewportPageCount = 1,
            ) { pageIndex ->
                val pageGestureModifier =
                    if (showHomeChrome && !showWorkspaceOptions && !isIconDragging) {
                        Modifier.pointerInput(touchSlop, longPressTimeoutMs, heightPx, pageIndex) {
                            detectHomeWorkspaceGestures(
                                touchSlop = touchSlop,
                                longPressTimeoutMs = longPressTimeoutMs,
                                heightPx = heightPx,
                                progress = { drawerState.progress },
                                onDragStart = { beginDrawerDrag() },
                                onProgress = { value -> drawerState.updateProgress(value) },
                                onSettle = { open -> settleDrawerAsync(open) },
                                onLongPress = { onWorkspaceLongPressState.value.invoke() },
                            )
                        }
                    } else {
                        Modifier
                    }

                when (val page = pages[pageIndex]) {
                    LauncherPagerPage.Home -> {
                        HomeWorkspacePage(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(pageGestureModifier)
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 160.dp),
                        )
                    }

                    LauncherPagerPage.Downloader -> {
                        DownloaderPage(
                            isActive = pagerState.currentPage == pageIndex,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is LauncherPagerPage.Workspace -> {
                        val workspaceIndex = pages
                            .take(pageIndex)
                            .count { it is LauncherPagerPage.Workspace }
                        WorkspaceAppsPage(
                            slots = page.slots,
                            homeAppKeys = homeAppKeys,
                            highlightSlot = iconDragState.candidate
                                ?.takeIf { it.pageIndex == workspaceIndex }
                                ?.slotIndex,
                            onAppClick = onAppClick,
                            onAppInfo = onAppInfo,
                            onUninstall = onUninstall,
                            onRemoveFromHome = onRemoveFromHome,
                            onGridPositioned = { coords ->
                                if (pagerState.currentPage == pageIndex) {
                                    val origin = coords.positionInWindow()
                                    workspaceGridBounds = Rect(
                                        offset = origin,
                                        size = androidx.compose.ui.geometry.Size(
                                            coords.size.width.toFloat(),
                                            coords.size.height.toFloat(),
                                        ),
                                    )
                                    workspaceGridPageIndex = workspaceIndex
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .then(pageGestureModifier)
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 160.dp),
                        )
                    }
                }
            }

            if (showHomeChrome) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PageIndicator(
                        pages = pages,
                        currentPage = pagerState.currentPage,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    DrawerHandleHint(
                        onOpenDrawer = { settleDrawerAsync(true) },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DockBar(
                        apps = dockApps,
                        homeAppKeys = homeAppKeys,
                        onAppClick = onAppClick,
                        onAppInfo = onAppInfo,
                        onUninstall = onUninstall,
                        onRemoveFromHome = onRemoveFromHome,
                        onOpenDrawer = { settleDrawerAsync(true) },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Live drag preview floating above Home + Drawer.
        if (isIconDragging) {
            val dragApp = iconDragState.app
            val finger = iconDragState.fingerWindow
            if (dragApp != null) {
                val positionProvider = remember(finger) {
                    object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize,
                        ): IntOffset {
                            return IntOffset(
                                (finger.x - popupContentSize.width / 2f).roundToInt(),
                                (finger.y - popupContentSize.height / 2f).roundToInt(),
                            )
                        }
                    }
                }
                Popup(
                    popupPositionProvider = positionProvider,
                    properties = PopupProperties(
                        focusable = false,
                        excludeFromSystemGesture = true,
                    ),
                ) {
                    Image(
                        bitmap = dragApp.icon,
                        contentDescription = dragApp.label,
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                shadowElevation = 12.dp.toPx()
                                scaleX = 1.08f
                                scaleY = 1.08f
                                alpha = 0.95f
                            }
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
            }
        }

        // Always the top-most launcher chrome (above Home + Drawer).
        HomeWorkspaceOptionsOverlay(
            visible = showWorkspaceOptions,
            onDismiss = { showWorkspaceOptions = false },
            onWallpaper = {
                showWorkspaceOptions = false
                onOpenWallpaper()
            },
            onHomeSettings = {
                showWorkspaceOptions = false
                onOpenHomeSettings()
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f),
        )
    }
}

@Composable
private fun HomeWorkspacePage(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        LauncherClock()
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WorkspaceAppsPage(
    slots: List<LauncherApp?>,
    homeAppKeys: Set<String>,
    highlightSlot: Int?,
    onAppClick: (LauncherApp) -> Unit,
    onAppInfo: (LauncherApp) -> Unit,
    onUninstall: (LauncherApp) -> Unit,
    onRemoveFromHome: (LauncherApp) -> Unit,
    onGridPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        HomePageAppGrid(
            slots = slots,
            homeAppKeys = homeAppKeys,
            highlightSlot = highlightSlot,
            onAppClick = onAppClick,
            onAppInfo = onAppInfo,
            onUninstall = onUninstall,
            onRemoveFromHome = onRemoveFromHome,
            onGridPositioned = onGridPositioned,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun HomePageAppGrid(
    slots: List<LauncherApp?>,
    homeAppKeys: Set<String>,
    highlightSlot: Int?,
    onAppClick: (LauncherApp) -> Unit,
    onAppInfo: (LauncherApp) -> Unit,
    onUninstall: (LauncherApp) -> Unit,
    onRemoveFromHome: (LauncherApp) -> Unit,
    onGridPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paddedSlots = remember(slots) {
        List(HOME_PAGE_APP_CAPACITY) { index -> slots.getOrNull(index) }
    }
    Column(
        modifier = modifier.onGloballyPositioned(onGridPositioned),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        for (row in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (col in 0 until 4) {
                    val slotIndex = row * 4 + col
                    val app = paddedSlots[slotIndex]
                    val highlighted = highlightSlot == slotIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (highlighted) {
                                    Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White.copy(alpha = 0.22f))
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (app != null) {
                            AppIconItem(
                                app = app,
                                isOnHome = app.componentKey in homeAppKeys,
                                onClick = { onAppClick(app) },
                                onAppInfo = { onAppInfo(app) },
                                onUninstall = { onUninstall(app) },
                                onRemoveFromHome = { onRemoveFromHome(app) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Spacer(modifier = Modifier.height(74.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(
    pages: List<LauncherPagerPage>,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    // Discover-style: dots only for Home + workspace pages (Downloader has no chrome).
    val indicatorPages = pages.mapIndexedNotNull { index, page ->
        when (page) {
            LauncherPagerPage.Downloader -> null
            else -> index
        }
    }
    if (indicatorPages.size <= 1) {
        Spacer(modifier = modifier.height(8.dp))
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        indicatorPages.forEach { pageIndex ->
            val selected = pageIndex == currentPage
            Box(
                modifier = Modifier
                    .size(if (selected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                    ),
            )
        }
    }
}

@Composable
private fun LauncherClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1_000)
        }
    }

    val timeFormat = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = timeFormat.format(now),
            color = Color.White,
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )
        Text(
            text = dateFormat.format(now),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DrawerHandleHint(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Click-only: vertical open-swipe is owned solely by the workspace gesture detector
    // to avoid nested pointerInput / drag conflicts.
    Column(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onOpenDrawer,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.55f)),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Swipe up for apps",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun AppDrawer(
    interactive: Boolean,
    apps: List<LauncherApp>,
    homeAppKeys: Set<String>,
    gridState: LazyGridState,
    heightPx: Float,
    onDragStart: () -> Unit,
    onDragDeltaY: (Float) -> Unit,
    onAppClick: (LauncherApp) -> Unit,
    onAppInfo: (LauncherApp) -> Unit,
    onUninstall: (LauncherApp) -> Unit,
    onRemoveFromHome: (LauncherApp) -> Unit,
    onIconDragStart: (LauncherApp, Offset) -> Unit,
    onIconDrag: (Offset) -> Unit,
    onIconDragEnd: () -> Unit,
    onIconDragCancel: () -> Unit,
    isIconDragging: Boolean,
    onSettle: (velocityY: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onDragStartState = rememberUpdatedState(onDragStart)
    val onDragDeltaState = rememberUpdatedState(onDragDeltaY)
    val onSettleState = rememberUpdatedState(onSettle)

    fun gridAtTop(): Boolean =
        gridState.firstVisibleItemIndex == 0 &&
            gridState.firstVisibleItemScrollOffset == 0

    val nestedScrollConnection = remember(heightPx, gridState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || heightPx <= 0f || !gridAtTop()) return Offset.Zero
                onDragStartState.value()
                onDragDeltaState.value(available.y)
                return Offset(0f, available.y)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y >= 0f || heightPx <= 0f) {
                    return Offset.Zero
                }
                onDragStartState.value()
                onDragDeltaState.value(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (gridAtTop() && available.y > DRAWER_CLOSE_VELOCITY) {
                    onSettleState.value(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > DRAWER_CLOSE_VELOCITY && gridAtTop()) {
                    onSettleState.value(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    // Full-screen drawer page. Vertical placement is applied by the parent layer.
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 24.dp)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color(0xE6121212))
            .then(
                if (interactive) {
                    Modifier.nestedScroll(nestedScrollConnection)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .then(
                    if (interactive) {
                        Modifier.pointerInput(heightPx) {
                            var velocityY = 0f
                            var lastTime = 0L
                            detectVerticalDragGestures(
                                onDragStart = {
                                    onDragStart()
                                    velocityY = 0f
                                    lastTime = System.nanoTime()
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val now = System.nanoTime()
                                    val dt = (now - lastTime) / 1_000_000_000f
                                    if (dt > 0f) velocityY = dragAmount / dt
                                    lastTime = now
                                    onDragDeltaY(dragAmount)
                                },
                                onDragEnd = { onSettle(velocityY) },
                                onDragCancel = { onSettle(0f) },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.45f)),
            )
        }

        Text(
            text = "All apps",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = interactive && !isIconDragging,
        ) {
            items(
                items = apps,
                key = { it.componentKey },
            ) { app ->
                AppIconItem(
                    app = app,
                    isOnHome = app.componentKey in homeAppKeys,
                    onClick = { onAppClick(app) },
                    onAppInfo = { onAppInfo(app) },
                    onUninstall = { onUninstall(app) },
                    onRemoveFromHome = { onRemoveFromHome(app) },
                    enableDrawerDrag = true,
                    onDrawerDragStart = onIconDragStart,
                    onDrawerDrag = onIconDrag,
                    onDrawerDragEnd = onIconDragEnd,
                    onDrawerDragCancel = onIconDragCancel,
                )
            }
        }
    }
}

/**
 * Single continuous Home workspace gesture:
 * - Vertical upward drag → App Drawer follows 1:1, then settles
 * - Stationary long-press → Home options
 * - Horizontal → leave for the pager
 *
 * Drag path does not use nested timers that fight the swipe; long-press is only
 * decided if the finger stays within touch slop for the timeout.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectHomeWorkspaceGestures(
    touchSlop: Float,
    longPressTimeoutMs: Long,
    heightPx: Float,
    progress: () -> Float,
    onDragStart: () -> Unit,
    onProgress: (Float) -> Unit,
    onSettle: (Boolean) -> Unit,
    onLongPress: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        if (progress() >= 0.995f) return@awaitEachGesture

        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)

        var totalX = 0f
        var totalY = 0f
        val pointerId = down.id
        val downTime = down.uptimeMillis

        var mode: SyncedGestureMode? = null

        while (mode == null) {
            val elapsed = android.os.SystemClock.uptimeMillis() - downTime
            val remaining = longPressTimeoutMs - elapsed
            if (remaining <= 0L) {
                mode = SyncedGestureMode.LongPress
                break
            }

            val event = withTimeoutOrNull(remaining) { awaitPointerEvent() }
            if (event == null) {
                mode = SyncedGestureMode.LongPress
                break
            }

            val change = event.changes.firstOrNull { it.id == pointerId }
                ?: return@awaitEachGesture

            if (change.changedToUp() || !change.pressed) {
                return@awaitEachGesture
            }

            val delta = change.positionChange()
            totalX += delta.x
            totalY += delta.y
            tracker.addPosition(change.uptimeMillis, change.position)

            if (abs(totalX) > touchSlop || abs(totalY) > touchSlop) {
                mode = when {
                    abs(totalX) >= abs(totalY) -> SyncedGestureMode.Horizontal
                    totalY >= 0f -> SyncedGestureMode.Downward
                    else -> SyncedGestureMode.DrawerDrag
                }
            }
        }

        val resolvedMode = mode ?: return@awaitEachGesture

        when (resolvedMode) {
            SyncedGestureMode.Up,
            SyncedGestureMode.Horizontal,
            SyncedGestureMode.Downward,
            -> return@awaitEachGesture

            SyncedGestureMode.LongPress -> {
                onLongPress()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: return@awaitEachGesture
                    change.consume()
                    if (change.changedToUp() || !change.pressed) return@awaitEachGesture
                }
            }

            SyncedGestureMode.DrawerDrag -> {
                onDragStart()
                // 1:1 mapping from absolute upward travel since down.
                if (heightPx > 0f) {
                    onProgress((-totalY / heightPx).coerceIn(0f, 1f))
                }

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: return@awaitEachGesture

                    if (change.changedToUp() || !change.pressed) {
                        val velocity = tracker.calculateVelocity()
                        val open = progress() >= DRAWER_OPEN_PROGRESS ||
                            (progress() > 0.08f && velocity.y < DRAWER_OPEN_VELOCITY)
                        onSettle(open)
                        return@awaitEachGesture
                    }

                    val delta = change.positionChange()
                    totalY += delta.y
                    tracker.addPosition(change.uptimeMillis, change.position)
                    change.consume()
                    if (heightPx > 0f) {
                        onProgress((-totalY / heightPx).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }
}

private enum class SyncedGestureMode {
    Up,
    LongPress,
    Horizontal,
    Downward,
    DrawerDrag,
}

@Composable
private fun DockBar(
    apps: List<LauncherApp>,
    homeAppKeys: Set<String>,
    onAppClick: (LauncherApp) -> Unit,
    onAppInfo: (LauncherApp) -> Unit,
    onUninstall: (LauncherApp) -> Unit,
    onRemoveFromHome: (LauncherApp) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        apps.take(4).forEach { app ->
            DockIcon(
                app = app,
                isOnHome = app.componentKey in homeAppKeys,
                onClick = { onAppClick(app) },
                onAppInfo = { onAppInfo(app) },
                onUninstall = { onUninstall(app) },
                onRemoveFromHome = { onRemoveFromHome(app) },
            )
        }
        DrawerDockButton(onClick = onOpenDrawer)
    }
}

@Composable
private fun DrawerDockButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color.White),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIconItem(
    app: LauncherApp,
    isOnHome: Boolean,
    onClick: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onRemoveFromHome: () -> Unit,
    modifier: Modifier = Modifier,
    enableDrawerDrag: Boolean = false,
    onDrawerDragStart: ((LauncherApp, Offset) -> Unit)? = null,
    onDrawerDrag: ((Offset) -> Unit)? = null,
    onDrawerDragEnd: (() -> Unit)? = null,
    onDrawerDragCancel: (() -> Unit)? = null,
) {
    var menuExpanded by remember(app.componentKey) { mutableStateOf(false) }
    val viewConfiguration = LocalViewConfiguration.current
    val touchSlop = viewConfiguration.touchSlop
    val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
    var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val onDragStartState = rememberUpdatedState(onDrawerDragStart)
    val onDragState = rememberUpdatedState(onDrawerDrag)
    val onDragEndState = rememberUpdatedState(onDrawerDragEnd)
    val onDragCancelState = rememberUpdatedState(onDrawerDragCancel)
    val onClickState = rememberUpdatedState(onClick)

    val interactionModifier = if (enableDrawerDrag) {
        Modifier.pointerInput(app.componentKey, touchSlop, longPressTimeoutMs) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                val pointerId = down.id
                val downTime = down.uptimeMillis
                var totalX = 0f
                var totalY = 0f
                var longPressed = false
                var dragging = false

                // Phase 1: wait for long-press or move past slop (scroll/cancel).
                while (!longPressed) {
                    val elapsed = android.os.SystemClock.uptimeMillis() - downTime
                    val remaining = longPressTimeoutMs - elapsed
                    if (remaining <= 0L) {
                        longPressed = true
                        break
                    }
                    val event = withTimeoutOrNull(remaining) { awaitPointerEvent() }
                    if (event == null) {
                        longPressed = true
                        break
                    }
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: return@awaitEachGesture
                    if (change.changedToUp() || !change.pressed) {
                        // Tap before long-press → launch.
                        onClickState.value()
                        return@awaitEachGesture
                    }
                    val delta = change.positionChange()
                    totalX += delta.x
                    totalY += delta.y
                    if (abs(totalX) > touchSlop || abs(totalY) > touchSlop) {
                        // Let the drawer grid scroll / nested drag handle this gesture.
                        return@awaitEachGesture
                    }
                }

                // Phase 2: long-press armed — drag past slop starts DnD; release opens menu.
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: run {
                            if (dragging) onDragCancelState.value?.invoke()
                            return@awaitEachGesture
                        }

                    if (change.changedToUp() || !change.pressed) {
                        if (dragging) {
                            onDragEndState.value?.invoke()
                        } else {
                            menuExpanded = true
                        }
                        return@awaitEachGesture
                    }

                    val delta = change.positionChange()
                    totalX += delta.x
                    totalY += delta.y
                    val coords = itemCoordinates
                    val fingerWindow = if (coords != null && coords.isAttached) {
                        coords.localToWindow(change.position)
                    } else {
                        change.position
                    }

                    if (!dragging && (abs(totalX) > touchSlop || abs(totalY) > touchSlop)) {
                        dragging = true
                        menuExpanded = false
                        onDragStartState.value?.invoke(app, fingerWindow)
                        change.consume()
                    } else if (dragging) {
                        onDragState.value?.invoke(fingerWindow)
                        change.consume()
                    }
                }
            }
        }
    } else {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { menuExpanded = true },
        )
    }

    Box(
        modifier = modifier.onGloballyPositioned { itemCoordinates = it },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .then(interactionModifier)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AppContextMenu(
            expanded = menuExpanded,
            app = app,
            isOnHome = isOnHome,
            onDismiss = { menuExpanded = false },
            onAppInfo = onAppInfo,
            onUninstall = onUninstall,
            onRemoveFromHome = onRemoveFromHome,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockIcon(
    app: LauncherApp,
    isOnHome: Boolean,
    onClick: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onRemoveFromHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(app.componentKey) { mutableStateOf(false) }

    Box(modifier = modifier) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                ),
        )
        AppContextMenu(
            expanded = menuExpanded,
            app = app,
            isOnHome = isOnHome,
            onDismiss = { menuExpanded = false },
            onAppInfo = onAppInfo,
            onUninstall = onUninstall,
            onRemoveFromHome = onRemoveFromHome,
        )
    }
}

@Composable
private fun AppContextMenu(
    expanded: Boolean,
    app: LauncherApp,
    isOnHome: Boolean,
    onDismiss: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onRemoveFromHome: () -> Unit,
) {
    val context = LocalContext.current
    val isSelfLauncher = app.packageName == SELF_LAUNCHER_PACKAGE
    val showUninstall = !isSelfLauncher
    val showRemoveFromHome = !isSelfLauncher && isOnHome
    var shortcuts by remember(app.componentKey) {
        mutableStateOf<List<ShortcutMenuItem>>(emptyList())
    }

    LaunchedEffect(expanded, app.componentKey) {
        if (!expanded) {
            shortcuts = emptyList()
            return@LaunchedEffect
        }
        shortcuts = withContext(Dispatchers.IO) {
            AppShortcutLoader.queryShortcutMenuItems(context, app)
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 8.dp),
        modifier = Modifier
            .widthIn(min = 220.dp, max = 280.dp)
            .background(Color(0xF01C1C1E), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xF01C1C1E),
    ) {
        // App-published shortcuts (each as its own icon + shortLabel row).
        shortcuts.forEach { item ->
            ShortcutMenuRow(
                item = item,
                onClick = {
                    AppShortcutLoader.startShortcut(context, item.info)
                    onDismiss()
                },
            )
        }

        if (shortcuts.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
                color = Color.White.copy(alpha = 0.14f),
            )
        }

        // Launcher actions in a separate section with icons.
        LauncherActionRow(
            label = "App Info",
            icon = Icons.Outlined.Info,
            onClick = {
                onAppInfo()
                onDismiss()
            },
        )
        if (showUninstall) {
            LauncherActionRow(
                label = "Uninstall",
                icon = Icons.Outlined.Delete,
                onClick = {
                    onUninstall()
                    onDismiss()
                },
            )
        }
        if (showRemoveFromHome) {
            LauncherActionRow(
                label = "Remove from Home",
                icon = Icons.Outlined.Home,
                onClick = {
                    onRemoveFromHome()
                    onDismiss()
                },
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
            color = Color.White.copy(alpha = 0.14f),
        )
        LauncherActionRow(
            label = "Cancel",
            icon = Icons.Outlined.Close,
            tint = Color.White.copy(alpha = 0.75f),
            onClick = onDismiss,
        )
    }
}

@Composable
private fun ShortcutMenuRow(
    item: ShortcutMenuItem,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = item.label,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Image(
                bitmap = item.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        },
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun LauncherActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = tint,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp),
            )
        },
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun HomeWorkspaceOptionsOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onWallpaper: () -> Unit,
    onHomeSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Popup ensures this sits above all Home/Drawer layers regardless of sibling zIndex.
    if (!visible) return

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .zIndex(100f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xF01C1C1E))
                    .padding(horizontal = 12.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WorkspaceOptionItem(
                    label = "Wallpaper",
                    icon = Icons.Outlined.Wallpaper,
                    onClick = onWallpaper,
                )
                WorkspaceOptionItem(
                    label = "Home Screen\nSettings",
                    icon = Icons.Outlined.Settings,
                    onClick = onHomeSettings,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceOptionItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = 108.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            maxLines = 2,
        )
    }
}
