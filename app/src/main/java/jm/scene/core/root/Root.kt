package jm.scene.core.root

import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * libsu root 封装：常驻 root shell。
 * 所有 sysfs/proc 读取先走普通 File 读（多数节点 app 可读），失败再降级 root cat。
 */
object Root {

    private var initialized = false

    @Synchronized
    fun ensureShell() {
        if (initialized) return
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
        Shell.getShell()
        initialized = true
    }

    fun isRoot(): Boolean = try {
        ensureShell()
        Shell.getShell().isRoot
    } catch (_: Exception) {
        false
    }

    /** 优先直接读，失败走 root。返回 trim 后文本，读不到返回 null。 */
    fun readText(path: String): String? {
        try {
            File(path).useLines { lines ->
                val sb = StringBuilder()
                lines.forEach { sb.append(it).append('\n') }
                if (sb.isNotBlank()) return sb.toString().trim()
            }
        } catch (_: Exception) {
        }
        return try {
            ensureShell()
            val r = Shell.cmd("cat '$path'").exec()
            if (r.isSuccess) r.out.joinToString("\n").trim().ifBlank { null } else null
        } catch (_: Exception) {
            null
        }
    }

    fun readInt(path: String): Int? = readText(path)?.trim()?.toIntOrNull()

    fun cmd(command: String): List<String> = try {
        ensureShell()
        val r = Shell.cmd(command).exec()
        if (r.isSuccess) r.out else emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}
