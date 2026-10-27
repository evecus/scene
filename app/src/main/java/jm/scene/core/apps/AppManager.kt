package jm.scene.core.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import jm.scene.core.root.Root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val label: String,
    val pkg: String,
    val isSystem: Boolean,
    val sizeBytes: Long,
    val icon: Bitmap?,
    val frozen: Boolean,
)

data class ComponentEntry(val name: String, val exported: Boolean, val shortName: String)

object AppManager {

    private val iconCache = object : LruCache<String, Bitmap>(256) {}

    private fun drawableToBitmap(d: Drawable, sizePx: Int = 48): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(c)
        return bmp
    }

    fun icon(ctx: Context, pkg: String): Bitmap? {
        iconCache.get(pkg)?.let { return it }
        return try {
            drawableToBitmap(ctx.packageManager.getApplicationIcon(pkg)).also { iconCache.put(pkg, it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadApps(ctx: Context, filter: String): List<AppEntry> =
        withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val pkgs: List<PackageInfo> = try {
                pm.getInstalledPackages(PackageManager.GET_META_DATA)
            } catch (_: Exception) { emptyList() }
            pkgs.mapNotNull { pi ->
                val ai = pi.applicationInfo ?: return@mapNotNull null
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (filter == "system" && !isSystem) return@mapNotNull null
                if (filter == "user" && isSystem) return@mapNotNull null
                val frozen = ai.enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                AppEntry(
                    label = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pi.packageName },
                    pkg = pi.packageName,
                    isSystem = isSystem,
                    sizeBytes = try { java.io.File(ai.sourceDir ?: "").length() } catch (_: Exception) { 0 },
                    icon = icon(ctx, pi.packageName),
                    frozen = frozen,
                )
            }.sortedBy { it.label.lowercase() }
        }

    // ---- 操作（root）----

    fun uninstall(pkg: String): String = Root.cmd("pm uninstall --user 0 '$pkg'").joinToString("\n")
        .ifEmpty { Root.cmd("pm uninstall '$pkg'").joinToString("\n").ifEmpty { "已执行" } }

    fun clearData(pkg: String): String = Root.cmd("pm clear '$pkg'").joinToString("\n").ifEmpty { "已执行" }

    fun setFrozen(pkg: String, frozen: Boolean): String =
        Root.cmd(
            if (frozen) "pm disable-user --user 0 '$pkg'" else "pm enable '$pkg'"
        ).joinToString("\n").ifEmpty { "已执行" }

    fun dex2oat(pkg: String, mode: String = "everything"): String =
        Root.cmd("cmd package compile -m $mode -f '$pkg'").joinToString("\n").ifEmpty { "已执行" }

    // ---- 活动 / 服务 ----

    fun activities(ctx: Context, pkg: String): List<ComponentEntry> = try {
        val pm = ctx.packageManager
        pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities
            ?.filterNotNull()
            ?.map { ComponentEntry(it.name, it.exported, it.name.substringAfterLast('.')) }
            ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun services(ctx: Context, pkg: String): List<ComponentEntry> = try {
        val pm = ctx.packageManager
        pm.getPackageInfo(pkg, PackageManager.GET_SERVICES).services
            ?.filterNotNull()
            ?.map { ComponentEntry(it.name, it.exported, it.name.substringAfterLast('.')) }
            ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun startActivity(ctx: Context, cls: String): String =
        Root.cmd("am start -n '$cls'").joinToString("\n").ifEmpty { "已执行" }

    fun startService(cls: String): String =
        Root.cmd("am start-foreground-service -n '$cls' || am startservice -n '$cls'")
            .joinToString("\n").ifEmpty { "已执行" }
}
