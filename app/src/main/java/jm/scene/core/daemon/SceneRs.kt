package jm.scene.core.daemon

import android.content.Context
import jm.scene.core.root.Root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * scene-rs 二进制的释放、会话化管理与 JSONL 读取。
 * 会话文件: filesDir/fps_<ts>.jsonl / power_<ts>.jsonl
 * 当前会话指针: filesDir/current_<kind>.txt （内容为会话文件绝对路径）
 * pidfile 由 scene-rs 写在 <out>.pid
 */
object SceneRs {

    private const val ASSET = "native/scene-rs"
    private const val BIN = "scene-rs"

    /** 释放二进制，返回可执行路径 */
    fun ensureBinary(ctx: Context): File {
        val f = File(ctx.filesDir, BIN)
        if (!f.exists() || f.length() == 0L) {
            syncBinaryFromAssets(ctx)
        }
        return f
    }

    fun syncBinaryFromAssets(ctx: Context): Unit {
        val f = File(ctx.filesDir, BIN)
        ctx.assets.open(ASSET).use { input ->
            f.outputStream().use { input.copyTo(it) }
        }
        f.setExecutable(true, false)
    }

    private fun sessionFile(ctx: Context, kind: String): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(ctx.filesDir, "${kind}_$ts.jsonl")
    }

    /** 启动后台采集；返回会话文件路径 */
    suspend fun start(ctx: Context, kind: String, intervalMs: Long): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!Root.isRoot()) error("无 root 权限")
                val bin = ensureBinary(ctx)
                val out = sessionFile(ctx, kind)
                // 同 kind 已有任务先停
                if (isRunning(ctx, kind)) stopCurrent(ctx, kind)
                val cmd = "${bin.absolutePath} $kind record --interval-ms $intervalMs --out ${out.absolutePath} --bg"
                val r = Root.cmd(cmd)
                // 等 pidfile 出现（scene-rs fork 后写入）
                val pidFile = File("${out.absolutePath}.pid")
                var waited = 0
                while (!pidFile.exists() && waited < 2000) {
                    Thread.sleep(100); waited += 100
                }
                if (!pidFile.exists() && r.isNotEmpty()) error("启动失败: ${r.joinToString()}")
                // 写当前会话指针
                File(ctx.filesDir, "current_$kind.txt").writeText(out.absolutePath)
                out.absolutePath
            }
        }

    fun currentSession(ctx: Context, kind: String): File? =
        File(ctx.filesDir, "current_$kind.txt").takeIf { it.exists() }
            ?.readText()?.trim()?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }

    fun pidAlive(pidFile: File): Boolean {
        val pid = pidFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: return false
        return Root.cmd("kill -0 $pid").let { true } && File("/proc/$pid").exists()
    }

    fun isRunning(ctx: Context, kind: String): Boolean {
        val cur = currentSession(ctx, kind) ?: return false
        return pidAlive(File("${cur.absolutePath}.pid"))
    }

    fun stopCurrent(ctx: Context, kind: String) {
        val cur = currentSession(ctx, kind) ?: return
        val pidFile = File("${cur.absolutePath}.pid")
        val pid = pidFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (pid != null) Root.cmd("kill -TERM $pid")
        pidFile.delete()
        File(ctx.filesDir, "current_$kind.txt").delete()
    }

    /** 历史会话列表（新→旧） */
    fun sessions(ctx: Context, kind: String): List<File> =
        ctx.filesDir.listFiles { f -> f.name.startsWith("${kind}_") && f.name.endsWith(".jsonl") }
            ?.sortedByDescending { it.name } ?: emptyList()

    /** 增量读取 JSONL 尾部 n 行 */
    fun tail(ctx: Context, kind: String, lines: Int = 200): List<String> {
        val f = currentSession(ctx, kind) ?: return emptyList()
        return try {
            f.readLines().filter { it.isNotBlank() }.takeLast(lines)
        } catch (_: Exception) { emptyList() }
    }

    // ---- 充电保护守护（scene-rs charge guard）----

    private val guardPidFile: File
        get() = File(filesDirCompat, "charge_guard.pid")

    @Volatile
    private var filesDirCompat: java.io.File? = null

    fun initDirs(ctx: Context) {
        filesDirCompat = ctx.filesDir
    }

    fun startChargeGuard(ctx: Context, stopPct: Int, startPct: Int): Result<String> {
        initDirs(ctx)
        return runCatching {
            if (!Root.isRoot()) error("无 root 权限")
            val bin = ensureBinary(ctx)
            if (guardPidFile.exists()) stopChargeGuard(ctx)
            val cmd = "${bin.absolutePath} charge guard --stop $stopPct --start $startPct --pid ${guardPidFile.absolutePath} --bg"
            val r = Root.cmd(cmd)
            var waited = 0
            while (!guardPidFile.exists() && waited < 2000) { Thread.sleep(100); waited += 100 }
            require(guardPidFile.exists()) { "启动失败: ${r.joinToString()}" }
            "充电保护已启动: ${startPct}% 恢复 / ${stopPct}% 停充"
        }
    }

    fun stopChargeGuard(ctx: Context) {
        initDirs(ctx)
        val pid = guardPidFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (pid != null) Root.cmd("kill -TERM $pid")
        guardPidFile.delete()
    }

    fun isChargeGuardRunning(ctx: Context): Boolean {
        initDirs(ctx)
        return guardPidFile.exists() && pidAlive(guardPidFile)
    }
}
