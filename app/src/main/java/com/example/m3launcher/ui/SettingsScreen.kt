package com.example.m3launcher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.m3launcher.data.AppsRepo
import com.example.m3launcher.data.LauncherState
import java.io.File
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(state: LauncherState, repo: AppsRepo, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var refresh by remember { mutableIntStateOf(0) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (importFont(ctx, uri, state)) refresh++
            else Toast.makeText(ctx, "Couldn't load that font", Toast.LENGTH_SHORT).show()
        }
    }
    Surface(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }, color = cs.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Home Settings", style = MaterialTheme.typography.headlineSmall)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item { ThemeSection(state) }
                item { FontSection(state, ctx, refresh, onImport = { importer.launch(arrayOf("*/*")) }, onChanged = { refresh++ }) }
                item { IconSection(state) }
                item { GridSection(state) }
                item { DrawerSection(state, repo) }
                item { MiscSection(state, repo, ctx) }
            }
        }
    }
}

// ---------------------------------------------------------------- sections

@Composable
private fun ThemeSection(state: LauncherState) {
    val cs = MaterialTheme.colorScheme
    Section("Theme") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            listOf("System", "Light", "Dark").forEachIndexed { i, l ->
                SegmentedButton(
                    selected = state.themeMode == i,
                    onClick = { state.themeMode = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                    label = { Text(l) },
                )
            }
        }
        val canDynamic = Build.VERSION.SDK_INT >= 31
        if (canDynamic) SwitchRow("Dynamic color", "Material You colors from your wallpaper", state.dynamicColor) { state.dynamicColor = it }
        if (!canDynamic || !state.dynamicColor) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SeedHues.forEach { h ->
                    val sel = state.seedHue == h
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(hsl(h, .5f, .55f))
                            .then(if (sel) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)
                            .clickable { state.seedHue = h }
                    )
                }
            }
        }
    }
}

@Composable
private fun FontSection(state: LauncherState, ctx: Context, refresh: Int, onImport: () -> Unit, onChanged: () -> Unit) {
    val options = remember(refresh) { fontOptions(ctx) }
    Section("Font") {
        options.forEach { (label, src) ->
            val family = remember(src) { buildFontFamily(ctx, src) }
            Row(
                Modifier.fillMaxWidth().clickable { state.fontSource = src }.padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.fontSource == src, onClick = { state.fontSource = src })
                Text(label, fontFamily = family, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (src.startsWith("file:")) {
                    IconButton(onClick = {
                        File(src.removePrefix("file:")).delete()
                        if (state.fontSource == src) state.fontSource = "default"
                        onChanged()
                    }) { Icon(Icons.Rounded.Delete, "Delete font") }
                }
            }
        }
        FilledTonalButton(onClick = onImport, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Import font (.ttf / .otf)")
        }
        SliderRow("Text size", "${(state.textScale * 100).roundToInt()}%", state.textScale, 0.8f..1.4f) { state.textScale = it }
    }
}

@Composable
private fun IconSection(state: LauncherState) {
    val cs = MaterialTheme.colorScheme
    Section("Icons") {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ShapeNames.forEachIndexed { i, name ->
                val sel = state.iconShape == i
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(56.dp).clip(iconShape(i))
                            .background(if (sel) cs.primary else cs.surfaceContainerHighest)
                            .clickable { state.iconShape = i },
                        contentAlignment = Alignment.Center,
                    ) { if (sel) Icon(Icons.Rounded.Check, null, tint = cs.onPrimary) }
                    Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        SliderRow("Icon size", "${(state.iconScale * 100).roundToInt()}%", state.iconScale, 0.7f..1.3f) { state.iconScale = it }
        SwitchRow("Show labels", null, state.showLabels) { state.showLabels = it }
        SwitchRow("Themed icons", "Tint icons with your theme (Android 13+ apps that support it)", state.themedIcons) { state.themedIcons = it }
    }
}

@Composable
private fun GridSection(state: LauncherState) {
    Section("Home screen") {
        SliderRow("Columns", "${state.cols}", state.cols.toFloat(), 3f..6f, 2) { state.updateGrid(it.roundToInt(), state.rows) }
        SliderRow("Rows", "${state.rows}", state.rows.toFloat(), 4f..8f, 3) { state.updateGrid(state.cols, it.roundToInt()) }
        SliderRow("Dock icons", "${state.dockCount}", state.dockCount.toFloat(), 3f..6f, 2) { state.updateDockCount(it.roundToInt()) }
        SliderRow("Wallpaper dim", "${(state.dim * 100).roundToInt()}%", state.dim, 0f..0.6f) { state.dim = it }
        SwitchRow("Search bar", null, state.showSearch) { state.showSearch = it }
    }
}

@Composable
private fun DrawerSection(state: LauncherState, repo: AppsRepo) {
    Section("App drawer") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            listOf("Grid", "List").forEachIndexed { i, l ->
                SegmentedButton(
                    selected = state.drawerList == (i == 1),
                    onClick = { state.drawerList = i == 1 },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    label = { Text(l) },
                )
            }
        }
        if (!state.drawerList) {
            SliderRow("Columns", "${state.drawerCols}", state.drawerCols.toFloat(), 3f..6f, 2) { state.drawerCols = it.roundToInt() }
        }
        val hidden = state.hidden.toList()
        Text(
            if (hidden.isEmpty()) "No hidden apps" else "Hidden apps",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        hidden.forEach { k ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(repo.byKey[k]?.label ?: k.substringBefore('#'), Modifier.weight(1f), maxLines = 1)
                TextButton(onClick = { state.unhide(k) }) { Text("Unhide") }
            }
        }
    }
}

@Composable
private fun MiscSection(state: LauncherState, repo: AppsRepo, ctx: Context) {
    Section("Launcher") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Set as default home app") }
            OutlinedButton(onClick = { state.resetLayout(ctx, repo) }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset home layout")
            }
        }
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = cs.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
        Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainer) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(valueText, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

// ---------------------------------------------------------------- fonts

fun fontOptions(ctx: Context): List<Pair<String, String>> {
    val out = mutableListOf("System default" to "default", "Google Sans (if on device)" to "device:google-sans")
    (ctx.assets.list("fonts") ?: emptyArray())
        .filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }
        .forEach { out += (it.substringBeforeLast('.') to "asset:fonts/$it") }
    File(ctx.filesDir, "fonts").listFiles()?.sortedBy { it.name }
        ?.forEach { out += (it.nameWithoutExtension to "file:${it.absolutePath}") }
    return out
}

fun importFont(ctx: Context, uri: Uri, state: LauncherState): Boolean = try {
    val name = (ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
        ?: "font_${System.currentTimeMillis()}.ttf").replace(Regex("[^A-Za-z0-9._-]"), "_")
    val dir = File(ctx.filesDir, "fonts").apply { mkdirs() }
    val out = File(dir, name)
    ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
    if (Typeface.Builder(out).build() == null) {
        out.delete()
        false
    } else {
        state.fontSource = "file:${out.absolutePath}"
        true
    }
} catch (e: Exception) {
    false
}
