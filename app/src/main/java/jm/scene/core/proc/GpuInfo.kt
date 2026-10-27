package jm.scene.core.proc

import jm.scene.core.root.Root

data class GpuInfo(
    val model: String,
    val glVersion: String,
    val curFreqKHz: Int,
    val minFreqKHz: Int,
    val maxFreqKHz: Int,
    val loadPercent: Int,
)

object GpuInfoLoader {

    fun load(): GpuInfo {
        val egl = Root.cmd("getprop ro.hardware.egl").firstOrNull() ?: ""
        val model = Root.readText("/sys/kernel/gpu/gpu_model")
            ?: egl.uppercase().ifBlank { "GPU" }

        // MTK GED
        var cur = -1; var min = -1; var max = -1; var load = -1
        Root.readText("/sys/kernel/ged/hal/current_freqency")?.let { t ->
            Regex("(\\d+)").findAll(t).lastOrNull()?.value?.toIntOrNull()?.let { cur = it }
        }
        Root.readText("/sys/kernel/ged/hal/gpu_load")?.let { t ->
            Regex("(\\d+)").findAll(t).firstOrNull()?.value?.toIntOrNull()?.let { load = it }
        }
        Root.readText("/sys/kernel/ged/hal/gpu_freq_list")?.let { t ->
            val list = Regex("\\d+").findAll(t).map { it.value.toInt() }.toList()
            if (list.isNotEmpty()) { min = list.first(); max = list.last() }
        }
        // 通用 /sys/kernel/gpu 节点
        if (cur < 0) cur = Root.readInt("/sys/kernel/gpu/gpu_busy_clock") ?: -1
        if (max < 0) max = Root.readInt("/sys/kernel/gpu/gpu_max_clock") ?: -1
        if (min < 0) min = Root.readInt("/sys/kernel/gpu/gpu_min_clock") ?: -1
        // Adreno kgsl
        if (cur < 0) cur = Root.readInt("/sys/class/kgsl/kgsl-3d0/gpuclk") ?: -1
        if (max < 0) max = Root.readInt("/sys/class/kgsl/kgsl-3d0/max_gpuclk") ?: -1
        if (load < 0) {
            val busy = Root.readText("/sys/class/kgsl/kgsl-3d0/gpubusy")?.split(Regex("\\s+"))
            if (busy != null && busy.size >= 2) {
                val b = busy[0].toLongOrNull() ?: 0L
                val t = busy[1].toLongOrNull() ?: 0L
                if (t > 0) load = (b * 100 / t).toInt()
            }
        }
        return GpuInfo(
            model = model,
            glVersion = "GLES",
            curFreqKHz = cur.coerceAtLeast(0),
            minFreqKHz = min.coerceAtLeast(0),
            maxFreqKHz = max.coerceAtLeast(0),
            loadPercent = load.coerceIn(-1, 100),
        )
    }
}
