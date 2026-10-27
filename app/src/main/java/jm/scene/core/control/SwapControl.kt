package jm.scene.core.control

import jm.scene.core.proc.SwapEntry
import jm.scene.core.root.Root
import java.io.File

data class VmParam(val key: String, val label: String, val value: String)

data class ZramState(
    val available: Boolean,
    val disksizeKb: Long,
    val algo: String,
    val origDataKb: Long,
    val comprDataKb: Long,
    val memUsedTotalKb: Long,
) {
    val ratio: Float get() = if (comprDataKb > 0) origDataKb.toFloat() / comprDataKb else 0f
}

object SwapControl {

    fun loadSwaps(): List<SwapEntry> = jm.scene.core.proc.MemInfo.loadSwaps()

    fun loadVmParams(): List<VmParam> = listOf(
        "swappiness",
        "watermark_scale_factor",
        "vfs_cache_pressure",
        "page-cluster",
        "min_free_kbytes",
    ).map { key ->
        VmParam(key, key, Root.readText("/proc/sys/vm/$key")?.trim() ?: "")
    }

    fun writeVmParam(key: String, value: String): Boolean {
        Root.cmd("echo '$value' > /proc/sys/vm/$key")
        return Root.readText("/proc/sys/vm/$key")?.trim() == value
    }

    fun loadZram(): ZramState {
        val base = "/sys/block/zram0"
        val mm = Root.readText("$base/mm_stat")?.trim()?.split(Regex("\\s+")) ?: emptyList()
        val disksizeBytes = Root.readText("$base/disksize")?.trim()?.toLongOrNull() ?: 0L
        return ZramState(
            available = File(base).exists(),
            disksizeKb = disksizeBytes / 1024,
            algo = Root.readText("$base/comp_algorithm")?.trim() ?: "",
            origDataKb = mm.getOrNull(0)?.toLongOrNull()?.div(1024) ?: 0,
            comprDataKb = mm.getOrNull(1)?.toLongOrNull()?.div(1024) ?: 0,
            memUsedTotalKb = mm.getOrNull(2)?.toLongOrNull()?.div(1024) ?: 0,
        )
    }

    /** 重设 zram 大小（需要先 swapoff zram0），sizeMb 传 0 表示关闭 zram */
    fun setZramSize(sizeMb: Long): Boolean {
        if (Root.cmd("swapon --show=NAME").any { it.contains("zram0") }) {
            Root.cmd("swapoff /dev/block/zram0 || swapoff /dev/zram0")
        }
        if (sizeMb <= 0L) {
            Root.cmd("echo 1 > /sys/block/zram0/reset")
            return true
        }
        val bytes = sizeMb * 1024 * 1024
        Root.cmd("echo '$bytes' > /sys/block/zram0/disksize")
        Root.cmd("mkswap /dev/block/zram0")
        Root.cmd("swapon /dev/block/zram0")
        return loadZram().disksizeKb > 0
    }

    /** 创建并启用 swapfile，sizeMb 如 4096 */
    fun createSwapfile(path: String, sizeMb: Long): Boolean {
        val mb = if (sizeMb > 1024) 1024 * 4 else sizeMb.coerceAtLeast(64)
        Root.cmd("dd if=/dev/zero of='$path' bs=1M count=$mb")
        Root.cmd("chmod 0600 '$path'")
        Root.cmd("mkswap '$path'")
        Root.cmd("swapon '$path'")
        return loadSwaps().any { it.path == path }
    }

    fun swapoff(path: String): Boolean {
        Root.cmd("swapoff '$path'")
        return loadSwaps().none { it.path == path }
    }

    fun swapon(path: String): Boolean {
        Root.cmd("swapon '$path'")
        return loadSwaps().any { it.path == path }
    }
}
