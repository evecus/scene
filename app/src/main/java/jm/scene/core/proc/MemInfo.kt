package jm.scene.core.proc

import jm.scene.core.root.Root

data class MemoryInfo(
    val totalKb: Long,
    val availKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val swapCachedKb: Long,
) {
    val usedKb: Long get() = totalKb - availKb
    val usedPercent: Int get() = if (totalKb > 0) (usedKb * 100 / totalKb).toInt() else 0
    val swapUsedPercent: Int
        get() = if (swapTotalKb > 0) ((swapTotalKb - swapFreeKb) * 100 / swapTotalKb).toInt() else 0
    val usedTotalPercent: Int
        get() = if (totalKb + swapTotalKb > 0)
            ((usedKb + swapTotalKb - swapFreeKb) * 100 / (totalKb + swapTotalKb)).toInt() else 0
}

data class SwapEntry(
    val path: String,
    val type: String,
    val sizeKb: Long,
    val usedKb: Long,
    val priority: Int,
)

object MemInfo {

    fun load(): MemoryInfo {
        val m = HashMap<String, Long>()
        Root.readText("/proc/meminfo")?.lineSequence()?.forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val kb = line.substring(idx + 1).trim().removeSuffix(" kB").trim().toLongOrNull()
                if (kb != null) m[line.substring(0, idx).trim()] = kb
            }
        }
        return MemoryInfo(
            totalKb = m["MemTotal"] ?: 0,
            availKb = m["MemAvailable"] ?: 0,
            swapTotalKb = m["SwapTotal"] ?: 0,
            swapFreeKb = m["SwapFree"] ?: 0,
            swapCachedKb = m["SwapCached"] ?: 0,
        )
    }

    fun loadSwaps(): List<SwapEntry> {
        val out = mutableListOf<SwapEntry>()
        val text = Root.cmd("cat /proc/swaps").ifEmpty {
            try { java.io.File("/proc/swaps").readLines() } catch (_: Exception) { emptyList() }
        }
        text.drop(1).forEach { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size >= 5) out.add(
                SwapEntry(
                    path = p[0], type = p[1],
                    sizeKb = p[2].toLongOrNull() ?: 0,
                    usedKb = p[3].toLongOrNull() ?: 0,
                    priority = p[4].toIntOrNull() ?: 0,
                )
            )
        }
        return out
    }
}
