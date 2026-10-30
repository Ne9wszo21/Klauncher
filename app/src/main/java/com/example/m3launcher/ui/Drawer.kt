package com.example.m3launcher.ui

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.m3launcher.data.AppInfo
import com.example.m3launcher.data.AppsRepo
import com.example.m3launcher.data.LauncherState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Drives the drawer sheet (0 = closed, 1 = open) with expressive springs. */
@Stable
class DrawerController(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set
    var heightPx = 1f
    var spec: AnimationSpec<Float> = spring()
    private var job: Job? = null

    val isOpen: Boolean get() = progress > 0.001f

    fun drag(delta: Float) {
        job?.cancel()
        progress = (progress + delta).coerceIn(0f, 1f)
    }

    fun settle(open: Boolean, velocity: Float = 0f) {
        job?.cancel()
        job = scope.launch {
            animate(progress, if (open) 1f else 0f, velocity, spec) { v, _ -> progress = v.coerceIn(0f, 1f) }
        }
    }

    /** velocityY in px/s (down is positive). */
    fun fling(velocityY: Float) {
        val v = -velocityY / heightPx
        settle(if (abs(v) > 1.2f) v > 0f else progress > 0.5f, v)
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f && progress < 1f && source == NestedScrollSource.UserInput) {
                drag(-available.y / heightPx); return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y > 0f && source == NestedScrollSource.UserInput) {
                drag(-available.y / heightPx); return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (progress > 0.001f && progress < 0.999f) { fling(available.y); return available }
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (progress > 0.001f && progress < 0.999f) fling(available.y)
            return available
        }
    }
}

private fun letterOf(label: String): Char {
    val c = label.firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    state: LauncherState,
    repo: AppsRepo,
    drawer: DrawerController,
    autoFocus: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo, Rect) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val hidden = state.hidden
    val all = remember(repo.apps, hidden) { repo.apps.filter { it.key !in hidden } }
    val shown = remember(all, query) {
        if (query.isBlank()) all else all.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val letters = remember(all) {
        all.map { letterOf(it.label) }.distinct().sortedWith(compareBy({ it == '#' }, { it }))
    }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) { delay(250); runCatching { focus.requestFocus() } } }

    fun jumpTo(letter: Char) {
        val idx = all.indexOfFirst { letterOf(it.label) == letter }
        if (idx < 0) return
        scope.launch { if (state.drawerList) listState.scrollToItem(idx) else gridState.scrollToItem(idx) }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = (1f - drawer.progress) * drawer.heightPx }
            .pointerInput(Unit) { detectTapGestures { } },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = cs.surfaceContainer.copy(alpha = 0.97f),
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(cs.onSurfaceVariant.copy(alpha = 0.4f)))
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).focusRequester(focus),
                singleLine = true,
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerHighest,
                    unfocusedContainerColor = cs.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { shown.firstOrNull()?.let(onLaunch) }),
            )

            if (shown.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No apps found", color = cs.onSurfaceVariant)
                    if (query.isNotBlank()) {
                        Spacer(Modifier.size(12.dp))
                        Button(onClick = {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text("Search the web") }
                    }
                }
            } else Row(Modifier.weight(1f)) {
                Box(Modifier.weight(1f)) {
                    if (!state.drawerList) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(state.drawerCols),
                            state = gridState,
                            modifier = Modifier.fillMaxSize().nestedScroll(drawer.connection),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(shown, key = { it.key }) { app ->
                                var rect by remember { mutableStateOf(Rect.Zero) }
                                AppIcon(
                                    app, repo, state,
                                    Modifier.fillMaxWidth()
                                        .onGloballyPositioned { rect = it.boundsInRoot() }
                                        .combinedClickable(
                                            onClick = { onLaunch(app) },
                                            onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onMenu(app, rect) },
                                        )
                                        .padding(vertical = 6.dp),
                                    label = true, labelColor = cs.onSurface, shadow = false,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().nestedScroll(drawer.connection),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(shown, key = { it.key }) { app ->
                                var rect by remember { mutableStateOf(Rect.Zero) }
                                Row(
                                    Modifier.fillMaxWidth()
                                        .onGloballyPositioned { rect = it.boundsInRoot() }
                                        .combinedClickable(
                                            onClick = { onLaunch(app) },
                                            onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onMenu(app, rect) },
                                        )
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppIcon(app, repo, state, size = 44.dp, label = false)
                                    Spacer(Modifier.width(16.dp))
                                    Text(app.label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                                }
                            }
                        }
                    }
                }
                if (query.isBlank()) AlphabetRail(letters, onPick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); jumpTo(it) })
            }
        }
    }
}

@Composable
private fun AlphabetRail(letters: List<Char>, onPick: (Char) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var active by remember { mutableStateOf<Char?>(null) }
    var h by remember { mutableFloatStateOf(1f) }
    fun pick(y: Float) {
        if (letters.isEmpty()) return
        val c = letters[(y / h * letters.size).toInt().coerceIn(0, letters.size - 1)]
        if (c != active) { active = c; onPick(c) }
    }
    Column(
        Modifier.fillMaxHeight().width(28.dp)
            .onSizeChanged { h = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(letters) { detectTapGestures { o -> pick(o.y); active = null } }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { o -> pick(o.y) },
                    onDragEnd = { active = null },
                    onDragCancel = { active = null },
                ) { change, _ -> pick(change.position.y) }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach {
            Text(
                it.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (it == active) cs.primary else cs.onSurfaceVariant,
            )
        }
    }
}
