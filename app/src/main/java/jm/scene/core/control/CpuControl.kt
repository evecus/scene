package jm.scene.core.control

import jm.scene.core.root.Root

data class CpuClusterState(
    val policy: String,
    val relatedCores: List<Int>,
    val curMinKHz: Int,
    val curMaxKHz: Int,
    val governor: String,
    val availableGovernors: List<String>,
    val availableFreqsKHz: List<Int>,
    val onlineByCore: Map<Int, Boolean>,
)

object CpuControl {

    private fun dir(policy: String) = "/sys/devices/system/cpu/cpufreq/$policy"

    /** 读某 cluster 完整状态；policy 形如 policy0 */
    fun loadCluster(policy: String, relatedCores: List<Int>): CpuClusterState {
        val d = dir(policy)
        val freqs = Root.readText("$d/scaling_available_frequencies")
            ?.split(Regex("\\s+"))?.mapNotNull(String::toIntOrNull) ?: emptyList()
        val govs = Root.readText("$d/scaling_available_governors")
            ?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
        return CpuClusterState(
            policy = policy,
            relatedCores = relatedCores,
            curMinKHz = Root.readInt("$d/scaling_min_freq") ?: 0,
            curMaxKHz = Root.readInt("$d/scaling_max_freq") ?: 0,
            governor = Root.readText("$d/scaling_governor") ?: "",
            availableGovernors = govs,
            availableFreqsKHz = freqs,
            onlineByCore = relatedCores.associateWith {
                Root.readText("/sys/devices/system/cpu/cpu${it}/online")?.trim() != "0"
            },
        )
    }

    fun writeMinFreq(policy: String, kHz: Int): Boolean =
        Root.cmd("echo '$kHz' > ${dir(policy)}/scaling_min_freq").let { true } &&
            (Root.readInt("${dir(policy)}/scaling_min_freq") == kHz)

    fun writeMaxFreq(policy: String, kHz: Int): Boolean =
        Root.cmd("echo '$kHz' > ${dir(policy)}/scaling_max_freq").let { true } &&
            (Root.readInt("${dir(policy)}/scaling_max_freq") == kHz)

    fun writeGovernor(policy: String, governor: String): Boolean {
        Root.cmd("echo '$governor' > ${dir(policy)}/scaling_governor")
        return Root.readText("${dir(policy)}/scaling_governor")?.trim() == governor
    }

    /** 核心 online/offline（cpu0 不可 offline） */
    fun setCoreOnline(core: Int, online: Boolean): Boolean {
        val v = if (online) "1" else "0"
        Root.cmd("echo '$v' > /sys/devices/system/cpu/cpu$core/online")
        return (Root.readText("/sys/devices/system/cpu/cpu$core/online")?.trim() != "0") == online
    }
}
