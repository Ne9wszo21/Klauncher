package com.example.m3launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.m3launcher.data.AppInfo
import com.example.m3launcher.data.AppsRepo
import com.example.m3launcher.data.LauncherState
import com.example.m3launcher.data.MAX_PAGES
import com.example.m3launcher.data.Src
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class DragState(val key: String, val src: Src, val pos: Offset, val moved: Boolean)

@SuppressLint("WrongConstant", "PrivateApi")
fun expandNotifications(ctx: Context) {
    try {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    } catch (_: Throwable) {}
}

@Composable
fun HomeScreen(state: LauncherState, repo: AppsRepo) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cs = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme

    val drawer = remember { DrawerController(scope) }
    drawer.spec = motion.defaultSpatialSpec<Float>()
    val pager = rememberPagerState { state.pages }

    var menu by remember { mutableStateOf<MenuState?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<DragState?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var focusSearch by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf<Src?>(null) }
    var gridRect by remember { mutableStateOf(Rect.Zero) }
    var dockRect by remember { mutableStateOf(Rect.Zero) }
    var pull by remember { mutableFloatStateOf(0f) }

    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    val removeBottom = topInset + with(density) { 96.dp.toPx() }
    val edgePx = with(density) { 40.dp.toPx() }
    val drawerVisible by remember { derivedStateOf { drawer.progress > 0f } }

    val jiggleT = rememberInfiniteTransition(label = "jiggle")
    val jiggle = jiggleT.animateFloat(-1.6f, 1.6f, infiniteRepeatable(tween(130, easing = LinearEasing), RepeatMode.Reverse), label = "j")

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun keyOf(s: Src): String? = when (s) {
        is Src.Home -> state.home[s.slot]
        is Src.Dock -> state.dock.getOrNull(s.idx)
    }

    fun cellAt(p: Offset): Int {
        val cw = gridRect.width / state.cols
        val ch = gridRect.height / state.rows
        val c = ((p.x - gridRect.left) / cw).toInt().coerceIn(0, state.cols - 1)
        val r = ((p.y - gridRect.top) / ch).toInt().coerceIn(0, state.rows - 1)
        return r * state.cols + c
    }

    fun cellRect(cell: Int): Rect {
        val cw = gridRect.width / state.cols
        val ch = gridRect.height / state.rows
        val c = cell % state.cols
        val r = cell / state.cols
        return Rect(gridRect.left + c * cw, gridRect.top + r * ch, gridRect.left + (c + 1) * cw, gridRect.top + (r + 1) * ch)
    }

    fun dockIndexAt(x: Float): Int =
        ((x - dockRect.left) / (dockRect.width / state.dockCount)).toInt().coerceIn(0, state.dockCount - 1)

    fun hit(p: Offset): Pair<Src, Rect>? {
        if (dockRect.contains(p)) {
            val i = dockIndexAt(p.x)
            if (i < state.dock.size) {
                val sw = dockRect.width / state.dockCount
                return Src.Dock(i) to Rect(dockRect.left + i * sw, dockRect.top, dockRect.left + (i + 1) * sw, dockRect.bottom)
            }
        } else if (gridRect.contains(p)) {
            val cell = cellAt(p)
            val slot = pager.currentPage * 100 + cell
            if (state.home.containsKey(slot)) return Src.Home(slot) to cellRect(cell)
        }
        return null
    }

    fun itemMenu(app: AppInfo, src: Src?, rect: Rect): MenuState {
        val items = mutableListOf<MenuItem>()
        repo.shortcuts(app).forEach { s ->
            items += MenuItem(Icons.Rounded.Shortcut, (s.shortLabel ?: s.id).toString()) { repo.startShortcut(s) }
        }
        if (src != null) {
            items += MenuItem(Icons.Rounded.Close, "Remove") {
                when (src) {
                    is Src.Home -> state.removeHome(src.slot)
                    is Src.Dock -> state.removeDock(src.idx)
                }
                state.trimPages()
            }
        } else {
            items += MenuItem(Icons.Rounded.Add, "Add to home") { if (!state.addToHome(app.key)) toast("Home is full") }
            items += MenuItem(Icons.Rounded.Add, "Add to dock") { if (!state.addToDock(app.key)) toast("Dock is full") }
        }
        items += MenuItem(Icons.Rounded.Info, "App info") { repo.appInfo(app) }
        items += MenuItem(Icons.Rounded.VisibilityOff, "Hide") { state.hide(app.key) }
        items += MenuItem(Icons.Rounded.Delete, "Uninstall") { repo.uninstall(app) }
        return MenuState(rect, items)
    }

    fun bgMenu(p: Offset) = MenuState(
        Rect(p, p),
        listOf(
            MenuItem(Icons.Rounded.Settings, "Home Settings") { settingsOpen = true },
            MenuItem(Icons.Rounded.OpenWith, "Rearrange") { editMode = true },
            MenuItem(Icons.Rounded.Palette, "Wallpaper & Style") {
                try {
                    ctx.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) { toast("No wallpaper picker found") }
            },
        ),
    )

    fun dropItem(d: DragState) {
        val p = d.pos
        when {
            p.y < removeBottom -> {
                when (val s = d.src) {
                    is Src.Home -> state.removeHome(s.slot)
                    is Src.Dock -> state.removeDock(s.idx)
                }
            }
            dockRect.contains(p) -> state.moveToDock(d.src, d.key, dockIndexAt(p.x))
            gridRect.contains(p) -> state.moveToHome(d.src, d.key, pager.currentPage * 100 + cellAt(p))
        }
        state.trimPages()
    }

    fun onTap(t: Pair<Src, Rect>?) {
        if (menu != null) { menu = null; return }
        if (editMode) { editMode = false; return }
        t?.let { keyOf(it.first) }?.let { repo.byKey[it] }?.let { repo.open(it) }
    }

    // Seed / clean layout once the app list is ready.
    LaunchedEffect(repo.apps) {
        if (repo.apps.isEmpty()) return@LaunchedEffect
        if (!state.initialized) state.seed(ctx, repo) else state.prune(repo.byKey)
    }

    // Home button pressed again -> reset to the first page.
    LaunchedEffect(state.homeTick) {
        if (state.homeTick == 0) return@LaunchedEffect
        menu = null; editMode = false; settingsOpen = false; dragging = null
        drawer.settle(false)
        pager.animateScrollToPage(0)
    }

    LaunchedEffect(drawerVisible) { if (!drawerVisible) focusSearch = false }

    // Hold an item at the screen edge to flip pages (creating a new one on the right).
    val edge = dragging?.takeIf { it.moved }?.let { d ->
        when {
            d.pos.x < gridRect.left + edgePx -> -1
            d.pos.x > gridRect.right - edgePx -> 1
            else -> 0
        }
    } ?: 0
    LaunchedEffect(edge, pager.currentPage) {
        if (edge == 0) return@LaunchedEffect
        delay(600)
        val next = pager.currentPage + edge
        if (next >= state.pages && state.pages < MAX_PAGES) { state.pages += 1; delay(100) }
        if (next in 0 until state.pages) pager.animateScrollToPage(next)
    }

    BackHandler {
        when {
            menu != null -> menu = null
            settingsOpen -> settingsOpen = false
            drawer.isOpen -> drawer.settle(false)
            editMode -> editMode = false
            pager.currentPage != 0 -> scope.launch { pager.animateScrollToPage(0) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        drawer.heightPx = constraints.maxHeight.toFloat()

        // Wallpaper dim, deepens as the drawer opens.
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(Color.Black.copy(alpha = (state.dim + drawer.progress * 0.5f).coerceIn(0f, 0.9f)))
            }
        )

        // ---------- home layer ----------
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val pr = drawer.progress
                    alpha = 1f - pr
                    scaleX = 1f - 0.05f * pr
                    scaleY = scaleX
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { d ->
                        when {
                            dragging != null || menu != null -> {}
                            d < 0f || drawer.progress > 0f -> drawer.drag(-d / drawer.heightPx)
                            else -> pull += d
                        }
                    },
                    onDragStopped = { v ->
                        if (drawer.progress > 0f) drawer.fling(v)
                        else if (pull > 140f || v > 1800f) expandNotifications(ctx)
                        pull = 0f
                    },
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (drawer.progress > 0f || settingsOpen) return@awaitEachGesture
                        val target = hit(down.position)
                        pressed = target?.first
                        val lp = awaitLongPressOrCancellation(down.id)
                        if (lp == null) {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            pressed = null
                            if (up != null && !up.pressed) onTap(target)
                            return@awaitEachGesture
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        pressed = null
                        if (target == null) {
                            menu = bgMenu(lp.position)
                            return@awaitEachGesture
                        }
                        val (src, rect) = target
                        val app = keyOf(src)?.let { repo.byKey[it] } ?: return@awaitEachGesture
                        dragging = DragState(app.key, src, lp.position, false)
                        menu = itemMenu(app, src, rect)
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == lp.id } ?: break
                            ch.consume()
                            if (!ch.pressed) break
                            if (!moved && (ch.position - lp.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                                menu = null
                            }
                            if (moved) dragging = dragging?.copy(pos = ch.position, moved = true)
                        }
                        val d = dragging
                        dragging = null
                        if (d != null && d.moved) dropItem(d)
                    }
                }
        ) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                if (state.showSearch) {
                    Surface(
                        onClick = { focusSearch = true; drawer.settle(true) },
                        shape = CircleShape,
                        color = cs.surfaceContainerHigh.copy(alpha = 0.92f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(52.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Search, null, tint = cs.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text("Search apps", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { gridRect = it.boundsInRoot() }
                ) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = dragging == null,
                    ) { page ->
                        HomePage(
                            page, state, repo,
                            hiddenSrc = dragging?.takeIf { it.moved }?.src,
                            pressed = pressed,
                            rotation = { if (editMode) jiggle.value else 0f },
                        )
                    }
                }

                Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
                    if (editMode) FilledTonalButton(onClick = { editMode = false }) { Text("Done") }
                    else if (state.pages > 1) PageDots(pager, state.pages)
                }

                // dock
                Box(
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(cs.surfaceContainer.copy(alpha = 0.7f))
                        .onGloballyPositioned { dockRect = it.boundsInRoot() }
                ) {
                    Row(Modifier.fillMaxSize()) {
                        repeat(state.dockCount) { i ->
                            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                val app = state.dock.getOrNull(i)?.let { repo.byKey[it] }
                                if (app != null) {
                                    val src = Src.Dock(i)
                                    AppIcon(
                                        app, repo, state,
                                        Modifier.alpha(if (dragging?.takeIf { it.moved }?.src == src) 0f else 1f),
                                        label = false,
                                        pressed = pressed == src,
                                        rotation = { if (editMode) jiggle.value else 0f },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- drawer ----------
        if (drawerVisible) {
            AppDrawer(
                state, repo, drawer, autoFocus = focusSearch,
                onLaunch = { repo.open(it); drawer.settle(false) },
                onMenu = { app, rect -> menu = itemMenu(app, null, rect) },
            )
        }

        // ---------- remove zone while dragging ----------
        AnimatedVisibility(
            visible = dragging?.moved == true,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(motion.defaultSpatialSpec()) { -it } + fadeIn(),
            exit = slideOutVertically(motion.defaultSpatialSpec()) { -it } + fadeOut(),
        ) {
            val over = (dragging?.pos?.y ?: Float.MAX_VALUE) < removeBottom
            Box(Modifier.padding(top = with(density) { topInset.toDp() } + 16.dp)) {
                Surface(
                    shape = CircleShape,
                    color = if (over) cs.errorContainer else cs.surfaceContainerHigh,
                ) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Delete, null, tint = if (over) cs.onErrorContainer else cs.onSurface)
                        Spacer(Modifier.width(8.dp))
                        Text("Remove", color = if (over) cs.onErrorContainer else cs.onSurface)
                    }
                }
            }
        }

        // ---------- dragged icon ----------
        dragging?.takeIf { it.moved }?.let { d ->
            val app = repo.byKey[d.key]
            if (app != null) {
                val size = 56.dp * state.iconScale * 1.15f
                val half = with(density) { (size / 2).toPx() }
                Box(
                    Modifier
                        .offset { IntOffset((d.pos.x - half).roundToInt(), (d.pos.y - half).roundToInt()) }
                        .size(size)
                ) { AppIcon(app, repo, state, size = size, label = false) }
            }
        }

        // ---------- popup menu ----------
        menu?.let { m -> key(m) { PopupMenu(m.anchor, m.items) { menu = null } } }

        // ---------- settings ----------
        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInVertically(motion.defaultSpatialSpec()) { it / 8 } +
                scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f) + fadeIn(motion.defaultEffectsSpec()),
            exit = slideOutVertically(motion.defaultSpatialSpec()) { it / 8 } +
                scaleOut(motion.defaultSpatialSpec(), targetScale = 0.92f) + fadeOut(motion.defaultEffectsSpec()),
        ) {
            SettingsScreen(state, repo) { settingsOpen = false }
        }
    }
}

@Composable
private fun HomePage(
    page: Int,
    state: LauncherState,
    repo: AppsRepo,
    hiddenSrc: Src?,
    pressed: Src?,
    rotation: () -> Float,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cw = maxWidth / state.cols
        val chh = maxHeight / state.rows
        for (cell in 0 until state.cols * state.rows) {
            val slot = page * 100 + cell
            val key = state.home[slot] ?: continue
            val app = repo.byKey[key] ?: continue
            val src = Src.Home(slot)
            val sign = if (cell % 2 == 0) 1f else -1f
            Box(
                Modifier.offset(cw * (cell % state.cols), chh * (cell / state.cols)).size(cw, chh),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    app, repo, state,
                    Modifier.alpha(if (hiddenSrc == src) 0f else 1f),
                    pressed = pressed == src,
                    rotation = { rotation() * sign },
                )
            }
        }
    }
}

@Composable
private fun PageDots(pager: PagerState, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val sel = pager.currentPage == i
            val w by animateDpAsState(
                if (sel) 18.dp else 6.dp,
                MaterialTheme.motionScheme.fastSpatialSpec<Dp>(),
                label = "dot",
            )
            Box(
                Modifier.padding(horizontal = 3.dp).size(w, 6.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = if (sel) 0.95f else 0.45f))
            )
        }
    }
}
