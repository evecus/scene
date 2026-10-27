package jm.scene.core.proc

import jm.scene.core.root.Root

data class Cluster(
    val policy: String,
    val relatedCores: List<Int>,
    val minFreqKHz: Int,
    val maxFreqKHz: Int,
    val curMinKHz: Int,
    val curMaxKHz: Int,
    val governor: String,
)

data class CpuTemp(val zone: String, val celsius: Float)

data class ProcStat(
    val pid: Int,
    val name: String,
    val rssKb: Long,
    val cpuPercent: Float,
)

object CpuInfo {

    /** 枚举 cpufreq policy，得到 cluster 划分与当前 min/max/governor */
    fun loadClusters(): List<Cluster> {
        val out = mutableListOf<Cluster>()
        val base = "/sys/devices/system/cpu/cpufreq"
        val policies = Root.cmd("ls -d $base/policy* 2>/dev/null")
            .map { it.substringAfterLast('/') }
            .ifEmpty {
                // 退化：直接找 cpu0..n 的 cpufreq 目录
                val cores = mutableListOf<Int>()
                for (i in 0 until Runtime.getRuntime().availableProcessors()) {
                    if (Root.readText("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq") != null)
                        cores.add(i)
                }
                if (cores.isNotEmpty()) listOf("cpu${cores.first()}") else emptyList()
            }
        for (p in policies) {
            val dir = if (p.startsWith("policy")) "$base/$p" else "$base/$p/cpufreq"
            val related = Root.readText("$dir/related_cpus")
                ?.split(Regex("\\s+"))?.mapNotNull(String::toIntOrNull)
                ?: (0 until Runtime.getRuntime().availableProcessors()).filter {
                    Root.readText("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq") != null
                }
            if (related.isEmpty()) continue
            out.add(
                Cluster(
                    policy = p,
                    relatedCores = related,
                    minFreqKHz = Root.readInt("$dir/scaling_min_freq") ?: 0,
                    maxFreqKHz = Root.readInt("$dir/scaling_max_freq") ?: 0,
                    curMinKHz = Root.readInt("$dir/scaling_min_freq") ?: 0,
                    curMaxKHz = Root.readInt("$dir/scaling_max_freq") ?: 0,
                    governor = Root.readText("$dir/scaling_governor") ?: "",
                )
            )
        }
        // 同 cluster（related_cpus 相同）去重
        return out.distinctBy { it.relatedCores }
    }

    fun coreCurFreqKHz(core: Int): Int =
        Root.readInt("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq") ?: 0

    /** 解析 /proc/stat，返回每核 (idle, total) */
    fun sampleCpuTimes(): Map<Int, Pair<Long, Long>> {
        val map = HashMap<Int, Pair<Long, Long>>()
        val lines = try {
            java.io.File("/proc/stat").readLines()
        } catch (_: Exception) { return map }
        for (line in lines) {
            if (!line.startsWith("cpu")) continue
            val parts = line.trim().split(Regex("\\s+"))
            val tag = parts[0]
            if (tag == "cpu") continue
            val core = tag.substring(3).toIntOrNull() ?: continue
            val vals = parts.drop(1).mapNotNull(String::toLongOrNull)
            if (vals.size < 5) continue
            val idle = vals[3] + vals[4] // idle + iowait
            val total = vals.sum()
            map[core] = idle to total
        }
        return map
    }

    /** 依据两次采样计算每核负载百分比 */
    fun coreLoads(prev: Map<Int, Pair<Long, Long>>, cur: Map<Int, Pair<Long, Long>>): Map<Int, Float> {
        val out = HashMap<Int, Float>()
        for ((core, c) in cur) {
            val p = prev[core] ?: continue
            val dTotal = c.second - p.second
            val dIdle = c.first - p.first
            out[core] = if (dTotal > 0) ((dTotal - dIdle).toFloat() / dTotal * 100f).coerceIn(0f, 100f) else 0f
        }
        return out
    }

    fun loadTemps(): List<CpuTemp> {
        val out = mutableListOf<CpuTemp>()
        val zones = Root.cmd("ls -d /sys/class/thermal/thermal_zone* 2>/dev/null")
        for (z in zones) {
            val type = Root.readText("$z/type") ?: continue
            if (!type.contains("cpu", true) && !type.contains("soc", true)) continue
            val t = Root.readInt("$z/temp") ?: continue
            out.add(CpuTemp(type, t / 1000f))
        }
        return out
    }

    /** 两次进程采样 (pid -> (utime+stime)) 计算 CPU%，RSS 取 statm */
    fun topProcesses(
        prev: Map<Int, Long>,
        cur: Map<Pair<Int, String>, Long>,
        intervalSec: Float,
        limit: Int = 5,
    ): List<ProcStat> {
        val nameByPid = cur.keys.associate { it.first to it.second }
        val out = mutableListOf<ProcStat>()
        for ((key, now) in cur) {
            val pid = key.first
            val p = prev[pid] ?: 0
            val ticks = now - p
            val percent = if (intervalSec > 0) ticks / intervalSec / 100f * 100f else 0f
            val rssKb = try {
                val statm = java.io.File("/proc/$pid/statm").readText().trim().split(Regex("\\s+"))
                (statm.getOrNull(1)?.toLongOrNull() ?: 0) * 4
            } catch (_: Exception) { 0 }
            out.add(ProcStat(pid, key.second, rssKb, percent))
        }
        return out.sortedByDescending { it.cpuPercent }.take(limit).also { _ ->
            nameByPid // keep reference
        }
    }

    /** 一次进程采样：pid -> (utime+stime)，名字来自 comm */
    fun sampleProcs(): Map<Pair<Int, String>, Long> {
        val out = HashMap<Pair<Int, String>, Long>()
        val proc = java.io.File("/proc")
        val files = proc.listFiles { f -> f.name.all { it.isDigit() } } ?: return out
        for (d in files) {
            try {
                val stat = java.io.File(d, "stat").readText()
                // comm 可能含空格与括号，取最后一个 ')' 之后
                val close = stat.lastIndexOf(')')
                if (close < 0) continue
                val comm = stat.substring(stat.indexOf('(') + 1, close)
                val rest = stat.substring(close + 2).split(Regex("\\s+"))
                // rest[0]=state; utime=rest[11], stime=rest[12]
                val utime = rest.getOrNull(11)?.toLongOrNull() ?: 0
                val stime = rest.getOrNull(12)?.toLongOrNull() ?: 0
                out[d.name.toInt() to comm] = utime + stime
            } catch (_: Exception) {
            }
        }
        return out
    }
}
