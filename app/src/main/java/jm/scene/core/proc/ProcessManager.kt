package jm.scene.core.proc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.core.content.ContextCompat
import jm.scene.core.root.Root

data class ProcEntry(
    val pid: Int,
    val name: String,
    val rssKb: Long,
    val cpuPercent: Float,
    val pkg: String?, // 若是应用进程
)

object ProcessManager {

    private val iconCache = HashMap<String, Drawable>()

    fun iconFor(ctx: Context, pkg: String?): Drawable? {
        pkg ?: return null
        iconCache[pkg]?.let { return it }
        return try {
            ctx.packageManager.getApplicationIcon(pkg).also { iconCache[pkg] = it }
        } catch (_: Exception) {
            null
        }
    }

    fun resolvePackage(ctx: Context, name: String): String? = try {
        ctx.packageManager.getPackageInfo(name, 0)
        name
    } catch (_: Exception) {
        null
    }

    fun drawableToBitmap(d: Drawable, sizePx: Int = 48): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(c)
        return bmp
    }

    fun kill(pid: Int): Boolean {
        Root.cmd("kill -9 $pid")
        // 检查进程是否还在
        return !java.io.File("/proc/$pid").exists()
    }

    fun forceStop(pkg: String): Boolean {
        Root.cmd("am force-stop $pkg")
        return true
    }
}
