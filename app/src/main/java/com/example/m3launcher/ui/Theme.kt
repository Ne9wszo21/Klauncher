package com.example.m3launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.core.graphics.ColorUtils
import com.example.m3launcher.data.LauncherState
import java.io.File

fun hsl(h: Float, s: Float, l: Float) =
    Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, l)))

val SeedHues = listOf(140f, 175f, 215f, 270f, 330f, 0f, 25f, 50f)

fun seedScheme(h: Float, dark: Boolean): ColorScheme {
    val h2 = h + 15f
    val h3 = h + 80f
    return if (dark) darkColorScheme(
        primary = hsl(h, .45f, .78f), onPrimary = hsl(h, .6f, .15f),
        primaryContainer = hsl(h, .35f, .28f), onPrimaryContainer = hsl(h, .5f, .9f),
        secondary = hsl(h2, .2f, .75f), onSecondary = hsl(h2, .25f, .15f),
        secondaryContainer = hsl(h2, .15f, .26f), onSecondaryContainer = hsl(h2, .2f, .88f),
        tertiary = hsl(h3, .3f, .75f), onTertiary = hsl(h3, .35f, .15f),
        tertiaryContainer = hsl(h3, .25f, .27f), onTertiaryContainer = hsl(h3, .3f, .88f),
        background = hsl(h, .12f, .07f), onBackground = hsl(h, .1f, .9f),
        surface = hsl(h, .1f, .08f), onSurface = hsl(h, .08f, .9f),
        surfaceVariant = hsl(h, .1f, .2f), onSurfaceVariant = hsl(h, .08f, .75f),
        outline = hsl(h, .06f, .55f), outlineVariant = hsl(h, .08f, .28f),
        surfaceContainerLowest = hsl(h, .1f, .05f), surfaceContainerLow = hsl(h, .1f, .1f),
        surfaceContainer = hsl(h, .1f, .12f), surfaceContainerHigh = hsl(h, .1f, .16f),
        surfaceContainerHighest = hsl(h, .09f, .2f),
    ) else lightColorScheme(
        primary = hsl(h, .5f, .38f), onPrimary = Color.White,
        primaryContainer = hsl(h, .6f, .88f), onPrimaryContainer = hsl(h, .7f, .1f),
        secondary = hsl(h2, .2f, .38f), onSecondary = Color.White,
        secondaryContainer = hsl(h2, .3f, .88f), onSecondaryContainer = hsl(h2, .3f, .1f),
        tertiary = hsl(h3, .3f, .38f), onTertiary = Color.White,
        tertiaryContainer = hsl(h3, .4f, .88f), onTertiaryContainer = hsl(h3, .4f, .1f),
        background = hsl(h, .2f, .97f), onBackground = hsl(h, .1f, .1f),
        surface = hsl(h, .2f, .97f), onSurface = hsl(h, .1f, .1f),
        surfaceVariant = hsl(h, .2f, .9f), onSurfaceVariant = hsl(h, .1f, .3f),
        outline = hsl(h, .06f, .45f), outlineVariant = hsl(h, .1f, .8f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = hsl(h, .2f, .95f),
        surfaceContainer = hsl(h, .2f, .93f), surfaceContainerHigh = hsl(h, .2f, .91f),
        surfaceContainerHighest = hsl(h, .2f, .89f),
    )
}

/** source: default | device:<family> | asset:<path in assets/> | file:<absolute path> */
fun buildFontFamily(ctx: Context, source: String): FontFamily = try {
    val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
    when {
        source.startsWith("device:") ->
            FontFamily(weights.map { Font(DeviceFontFamilyName(source.removePrefix("device:")), it) })
        source.startsWith("asset:") ->
            FontFamily(weights.map { Font(source.removePrefix("asset:"), ctx.assets, it) })
        source.startsWith("file:") ->
            FontFamily(weights.map { Font(File(source.removePrefix("file:")), it) })
        else -> FontFamily.Default
    }
} catch (e: Throwable) {
    FontFamily.Default
}

fun Typography.withFamily(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

@SuppressLint("NewApi")
@Composable
fun LauncherTheme(state: LauncherState, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = when (state.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val scheme = if (state.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else seedScheme(state.seedHue, dark)
    val family = remember(state.fontSource) { buildFontFamily(ctx, state.fontSource) }
    val typo = remember(family) { Typography().withFamily(family) }
    val d = LocalDensity.current
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = typo,
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(d.density, d.fontScale * state.textScale),
            content = content,
        )
    }
}
