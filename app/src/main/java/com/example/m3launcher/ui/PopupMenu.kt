package com.example.m3launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class MenuItem(val icon: ImageVector?, val label: String, val onClick: () -> Unit)
class MenuState(val anchor: Rect, val items: List<MenuItem>)

/** Rounded card menu, like the "Home Settings / Rearrange / Wallpaper & Style" popup. */
@Composable
fun PopupMenu(anchor: Rect, items: List<MenuItem>, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    var size by remember { mutableStateOf(IntSize.Zero) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val s by animateFloatAsState(if (shown) 1f else 0.6f, motion.defaultSpatialSpec<Float>(), label = "menuScale")
    val a by animateFloatAsState(if (shown) 1f else 0f, motion.defaultEffectsSpec<Float>(), label = "menuAlpha")
    val density = LocalDensity.current
    val m = with(density) { 16.dp.toPx() }
    val gap = with(density) { 8.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        val x = (anchor.center.x - size.width / 2f).coerceIn(m, maxOf(m, sw - size.width - m))
        val above = anchor.top - size.height - gap > m
        val y = if (above) anchor.top - size.height - gap
        else (anchor.bottom + gap).coerceAtMost(maxOf(m, sh - size.height - m))
        Surface(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .onSizeChanged { size = it }
                .graphicsLayer {
                    scaleX = s; scaleY = s; alpha = a
                    transformOrigin = TransformOrigin(0.5f, if (above) 1f else 0f)
                }
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerHigh,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, cs.outlineVariant),
        ) {
            Column(Modifier.padding(vertical = 8.dp).widthIn(min = 240.dp)) {
                items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onDismiss(); item.onClick() }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.icon != null) Icon(item.icon, null, Modifier.size(28.dp), tint = cs.primary)
                        else Spacer(Modifier.width(28.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(item.label, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    }
                }
            }
        }
    }
}
