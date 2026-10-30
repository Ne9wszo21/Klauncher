package com.example.m3launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val label: String,
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
) {
    val pkg: String get() = component.packageName
}

class AppsRepo(private val ctx: Context) {
    private val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val um = ctx.getSystemService(Context.USER_SERVICE) as UserManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var infos: Map<String, LauncherActivityInfo> = emptyMap()
    private val cache = LruCache<String, ImageBitmap>(400)

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    var iconVersion by mutableIntStateOf(0)
        private set
    val byKey: Map<String, AppInfo> by derivedStateOf { apps.associateBy { it.key } }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    init {
        reload()
        la.registerCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun close() {
        la.unregisterCallback(callback)
        scope.cancel()
    }

    fun reload() {
        scope.launch {
            val (list, map) = withContext(Dispatchers.Default) { load() }
            infos = map
            cache.evictAll()
            apps = list
            iconVersion++
        }
    }

    private fun load(): Pair<List<AppInfo>, Map<String, LauncherActivityInfo>> {
        val list = ArrayList<AppInfo>()
        val map = HashMap<String, LauncherActivityInfo>()
        for (u in um.userProfiles) {
            val serial = um.getSerialNumberForUser(u)
            for (i in la.getActivityList(null, u)) {
                val key = "${i.componentName.flattenToShortString()}#$serial"
                list += AppInfo(i.label.toString(), key, i.componentName, u)
                map[key] = i
            }
        }
        return list.sortedBy { it.label.lowercase() } to map
    }

    fun icon(app: AppInfo, px: Int, themed: Boolean, bg: Int, fg: Int): ImageBitmap {
        val k = "${app.key}|$px|$themed|$bg|$fg"
        cache.get(k)?.let { return it }
        val d = infos[app.key]?.getIcon(0)
        val bmp = if (d == null) Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        else IconRenderer.render(d, px, themed, bg, fg)
        return bmp.asImageBitmap().also { cache.put(k, it) }
    }

    fun open(app: AppInfo) {
        try { la.startMainActivity(app.component, app.user, null, null) } catch (_: Exception) {}
    }

    fun appInfo(app: AppInfo) {
        try { la.startAppDetailsActivity(app.component, app.user, null, null) } catch (_: Exception) {}
    }

    fun uninstall(app: AppInfo) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }

    /** Only works once we're the default home app. */
    fun shortcuts(app: AppInfo): List<ShortcutInfo> = try {
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.pkg)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        la.getShortcuts(q, app.user)?.filter { it.isEnabled }?.sortedBy { it.rank }?.take(4) ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun startShortcut(s: ShortcutInfo) {
        try { la.startShortcut(s, null, null) } catch (_: Exception) {}
    }
}
