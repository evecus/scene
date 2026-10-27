package jm.scene.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jm.scene.core.proc.BatteryInfo
import jm.scene.core.proc.BatteryInfoLoader
import jm.scene.core.proc.Cluster
import jm.scene.core.proc.CpuInfo
import jm.scene.core.proc.CpuTemp
import jm.scene.core.proc.GpuInfo
import jm.scene.core.proc.MemInfo
import jm.scene.core.proc.MemoryInfo
import jm.scene.core.proc.ProcStat
import jm.scene.core.proc.SwapEntry
import jm.scene.core.root.Root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OverviewState(
    val ready: Boolean = false,
    val memory: MemoryInfo? = null,
    val swaps: List<SwapEntry> = emptyList(),
    val gpu: GpuInfo? = null,
    val clusters: List<Cluster> = emptyList(),
    val coreFreqKHz: Map<Int, Int> = emptyMap(),
    val coreLoad: Map<Int, Float> = emptyMap(),
    val coreHistory: Map<Int, List<Float>> = emptyMap(), // 每核最近 5 个采样
    val topProcs: List<ProcStat> = emptyList(),
    val socName: String = "",
    val cpuTempC: Float = Float.NaN,
    val battery: BatteryInfo? = null,
    val uptimeStr: String = "",
    val socLoadPercent: Int = 0,
)

class OverviewViewModel : ViewModel() {

    private val _state = MutableStateFlow(OverviewState())
    val state: StateFlow<OverviewState> = _state.asStateFlow()

    private var prevCpuTimes: Map<Int, Pair<Long, Long>> = emptyMap()
    private var prevProcTimes: Map<Int, Long> = emptyMap()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            Root.ensureShell()
            val clusters = CpuInfo.loadClusters()
            val soc = loadSocName()
            while (isActive) {
                val t0 = System.nanoTime()
                _state.value = collect(clusters, soc)
                val elapsedMs = (System.nanoTime() - t0) / 1_000_000
                delay((1000 - elapsedMs).coerceAtLeast(100))
            }
        }
    }

    private fun loadSocName(): String {
        Root.readText("/sys/devices/soc0/machine")?.let { if (it.isNotBlank()) return it.trim() }
        Root.cmd("getprop ro.soc.model").firstOrNull()?.let { if (it.isNotBlank()) return it }
        return android.os.Build.HARDWARE.uppercase()
    }

    private suspend fun collect(clusters: List<Cluster>, soc: String): OverviewState =
        withContext(Dispatchers.IO) {
            val mem = MemInfo.load()
            val gpu = jm.scene.core.proc.GpuInfoLoader.load()

            // CPU 负载：两次 /proc/stat 采样差分
            val curTimes = CpuInfo.sampleCpuTimes()
            val loads = CpuInfo.coreLoads(prevCpuTimes, curTimes)
            prevCpuTimes = curTimes

            val freqs = clusters.flatMap { it.relatedCores }.associateWith { CpuInfo.coreCurFreqKHz(it) }

            // 进程采样差分
            val curProcs = CpuInfo.sampleProcs()
            val byPidNow = curProcs.entries.associate { (k, v) -> k.first to v }
            val tops = CpuInfo.topProcesses(prevProcTimes, curProcs, 1.0f)
            prevProcTimes = byPidNow

            val temps = CpuInfo.loadTemps()
            val cpuTemp = temps.maxOfOrNull { it.celsius } ?: Float.NaN

            val history = HashMap(_state.value.coreHistory)
            for ((core, l) in loads) {
                val h = (history[core] ?: emptyList()) + l
                history[core] = if (h.size > 5) h.drop(h.size - 5) else h
            }

            val socLoad = loads.values.takeIf { it.isNotEmpty() }
                ?.let { l -> l.map { it.toInt() }.average().toInt() } ?: 0

            OverviewState(
                ready = true,
                memory = mem,
                swaps = jm.scene.core.proc.MemInfo.loadSwaps(),
                gpu = gpu,
                clusters = clusters,
                coreFreqKHz = freqs,
                coreLoad = loads,
                coreHistory = history,
                topProcs = tops,
                socName = soc,
                cpuTempC = cpuTemp,
                battery = BatteryInfoLoader.load(),
                uptimeStr = loadUptime(),
                socLoadPercent = socLoad,
            )
        }

    private fun loadUptime(): String {
        val sec = Root.readText("/proc/uptime")
            ?.trim()?.substringBefore(' ')?.toFloatOrNull()?.toLong() ?: return ""
        val h = sec / 3600
        val m = sec % 3600 / 60
        val s = sec % 60
        return "%d:%02d:%02d".format(h, m, s)
    }
}
