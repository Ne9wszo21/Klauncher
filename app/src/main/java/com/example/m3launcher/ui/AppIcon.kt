package com.example.m3launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.m3launcher.data.AppInfo
import com.example.m3launcher.data.AppsRepo
import com.example.m3launcher.data.LauncherState

val ShapeNames = listOf("Circle", "Squircle", "Cookie", "Clover", "Flower")

@Composable
fun iconShape(index: Int): Shape = remember(index) {
    when (index) {
        0 -> MaterialShapes.Circle.toShape()
        1 -> RoundedCornerShape(percent = 30)
        2 -> MaterialShapes.Cookie9Sided.toShape()
        3 -> MaterialShapes.Clover4Leaf.toShape()
        4 -> MaterialShapes.Flower.toShape()
        else -> CircleShape
    }
}

@Composable
fun AppIcon(
    app: AppInfo,
    repo: AppsRepo,
    state: LauncherState,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp * state.iconScale,
    label: Boolean = state.showLabels,
    labelColor: Color = Color.White,
    shadow: Boolean = true,
    pressed: Boolean = false,
    rotation: () -> Float = { 0f },
) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val cs = MaterialTheme.colorScheme
    val themed = state.themedIcons
    val bg = cs.secondaryContainer.toArgb()
    val fg = cs.onSecondaryContainer.toArgb()
    val bmp = remember(app.key, px, themed, if (themed) bg else 0, if (themed) fg else 0, repo.iconVersion) {
        repo.icon(app, px, themed, bg, fg)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>(),
        label = "press",
    )
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = rotation() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(bmp, contentDescription = app.label, modifier = Modifier.size(size).clip(iconShape(state.iconShape)))
        if (label) {
            Spacer(Modifier.height(4.dp))
            Text(
                app.label,
                color = labelColor,
                style = MaterialTheme.typography.labelMedium.let {
                    if (shadow) it.copy(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 6f)) else it
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
