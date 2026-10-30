package com.example.m3launcher.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

const val MAX_PAGES = 8

/** SharedPreferences-backed Compose state property. */
class PrefDelegate<T : Any>(
    private val sp: SharedPreferences,
    private val key: String,
    private val default: T,
) : ReadWriteProperty<Any?, T> {
    private val state = mutableStateOf(load())

    @Suppress("UNCHECKED_CAST")
    private fun load(): T = when (default) {
        is Int -> sp.getInt(key, default)
        is Boolean -> sp.getBoolean(key, default)
        is Float -> sp.getFloat(key, default)
        is String -> sp.getString(key, default) ?: default
        else -> default
    } as T

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.value = value
        val e = sp.edit()
        when (value) {
            is Int -> e.putInt(key, value)
            is Boolean -> e.putBoolean(key, value)
            is Float -> e.putFloat(key, value)
            is String -> e.putString(key, value)
        }
        e.apply()
    }
}

class LauncherState(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    // --- settings ---
    var initialized by PrefDelegate(sp, "init", false)
    var themeMode by PrefDelegate(sp, "themeMode", 0)          // 0 system, 1 light, 2 dark
    var dynamicColor by PrefDelegate(sp, "dynamic", true)
    var seedHue by PrefDelegate(sp, "hue", 140f)
    var themedIcons by PrefDelegate(sp, "themedIcons", false)
    var fontSource by PrefDelegate(sp, "font", "default")      // default | device:<name> | asset:<path> | file:<path>
    var textScale by PrefDelegate(sp, "textScale", 1f)
    var iconShape by PrefDelegate(sp, "iconShape", 0)
    var iconScale by PrefDelegate(sp, "iconScale", 1f)
    var showLabels by PrefDelegate(sp, "labels", true)
    var cols by PrefDelegate(sp, "cols", 4)
    var rows by PrefDelegate(sp, "rows", 6)
    var dockCount by PrefDelegate(sp, "dockCount", 5)
    var showSearch by PrefDelegate(sp, "search", true)
    var dim by PrefDelegate(sp, "dim", 0.15f)
    var drawerList by PrefDelegate(sp, "drawerList", false)
    var drawerCols by PrefDelegate(sp, "drawerCols", 4)
    var pages by PrefDelegate(sp, "pages", 1)
    private var hiddenRaw by PrefDelegate(sp, "hidden", "")

    // --- layout ---
    /** slot = page * 100 + cell  ->  app key */
    val home = mutableStateMapOf<Int, String>()
    val dock = mutableStateListOf<String>()

    /** Bumped when the Home button is pressed while we're already on top. */
    var homeTick by mutableIntStateOf(0)

    init {
        sp.getString("home", "")!!.lines().forEach { l ->
            val i = l.indexOf('=')
            if (i > 0) {
                val slot = l.substring(0, i).toIntOrNull() ?: return@forEach
                home[slot] = l.substring(i + 1)
            }
        }
        dock.addAll(sp.getString("dock", "")!!.lines().filter { it.isNotEmpty() })
    }

    val capacity: Int get() = cols * rows

    val hidden: Set<String> get() = hiddenRaw.split("\n").filter { it.isNotEmpty() }.toSet()
    fun hide(key: String) { hiddenRaw = (hidden + key).joinToString("\n") }
    fun unhide(key: String) { hiddenRaw = (hidden - key).joinToString("\n") }

    private fun saveHome() {
        sp.edit().putString("home", home.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
    }

    private fun saveDock() {
        sp.edit().putString("dock", dock.joinToString("\n")).apply()
    }

    fun firstFree(): Int? {
        for (p in 0 until pages) for (c in 0 until capacity) if (!home.containsKey(p * 100 + c)) return p * 100 + c
        return null
    }

    fun addToHome(key: String): Boolean {
        var slot = firstFree()
        if (slot == null && pages < MAX_PAGES) { pages += 1; slot = (pages - 1) * 100 }
        if (slot == null) return false
        home[slot] = key
        saveHome()
        return true
    }

    fun addToDock(key: String): Boolean {
        if (dock.size >= dockCount) return false
        dock.add(key)
        saveDock()
        return true
    }

    fun removeHome(slot: Int) { home.remove(slot); saveHome() }
    fun removeDock(idx: Int) { if (idx in dock.indices) { dock.removeAt(idx); saveDock() } }

    fun moveToHome(src: Src, key: String, target: Int) {
        var slot = target
        val srcSlot = (src as? Src.Home)?.slot ?: -1
        if (home.containsKey(slot) && slot != srcSlot) {
            val page = target / 100
            slot = (0 until capacity).map { page * 100 + it }
                .filter { !home.containsKey(it) || it == srcSlot }
                .minByOrNull { kotlin.math.abs(it % 100 - target % 100) } ?: return
        }
        when (src) {
            is Src.Home -> home.remove(src.slot)
            is Src.Dock -> if (src.idx in dock.indices) dock.removeAt(src.idx)
        }
        home[slot] = key
        saveHome(); saveDock()
    }

    fun moveToDock(src: Src, key: String, idx: Int) {
        val list = dock.toMutableList()
        when (src) {
            is Src.Dock -> if (src.idx in list.indices) list.removeAt(src.idx)
            is Src.Home -> if (list.size >= dockCount) return
        }
        list.add(idx.coerceIn(0, list.size), key)
        if (src is Src.Home) home.remove(src.slot)
        dock.clear(); dock.addAll(list)
        saveHome(); saveDock()
    }

    fun trimPages() {
        while (pages > 1 && home.keys.none { it / 100 == pages - 1 }) pages -= 1
    }

    /** Re-pack items after the grid size changed. */
    fun updateGrid(c: Int, r: Int) {
        if (c == cols && r == rows) return
        val items = home.entries.sortedBy { it.key }.map { it.value }
        cols = c; rows = r
        home.clear()
        val cap = capacity
        items.forEachIndexed { i, k -> home[(i / cap) * 100 + i % cap] = k }
        pages = maxOf(1, minOf(MAX_PAGES, (items.size + cap - 1) / cap))
        saveHome()
    }

    fun updateDockCount(n: Int) {
        dockCount = n
        while (dock.size > n) { val k = dock.removeAt(dock.size - 1); addToHome(k) }
        saveDock()
    }

    fun prune(valid: Map<String, AppInfo>) {
        val bad = home.filterValues { it !in valid }.keys.toList()
        bad.forEach { home.remove(it) }
        val dockBad = dock.any { it !in valid }
        dock.removeAll { it !in valid }
        if (bad.isNotEmpty()) saveHome()
        if (dockBad) saveDock()
    }

    @Suppress("DEPRECATION")
    fun seed(ctx: Context, repo: AppsRepo) {
        val pm = ctx.packageManager
        fun pkgFor(i: Intent) = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val wanted = listOfNotNull(
            pkgFor(Intent(Intent.ACTION_DIAL)),
            pkgFor(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            pkgFor(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
        ).distinct()
        val apps = repo.apps
        wanted.forEach { p ->
            apps.firstOrNull { it.pkg == p }?.let { if (dock.size < dockCount && it.key !in dock) dock.add(it.key) }
        }
        apps.filter { it.key !in dock }.take(cols * 2).forEach { addToHome(it.key) }
        saveDock()
        initialized = true
    }

    fun resetLayout(ctx: Context, repo: AppsRepo) {
        home.clear(); dock.clear(); pages = 1
        saveHome(); saveDock()
        seed(ctx, repo)
    }
}

/** Where an item lives: a home grid slot (page*100+cell) or a dock index. */
sealed interface Src {
    data class Home(val slot: Int) : Src
    data class Dock(val idx: Int) : Src
}
