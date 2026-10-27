package jm.scene.core.proc

import jm.scene.core.root.Root
import kotlin.math.abs

data class BatteryInfo(
    val status: String,
    val capacity: Int,
    val currentMa: Float,
    val voltageV: Float,
    val tempC: Float,
    val powerW: Float,
    val chargeFullMah: Int,
    val chargeCounterMah: Int,
    val cycleCount: Int,
    val technology: String,
) {
    val charging: Boolean get() = currentMa > 0f
}

object BatteryInfoLoader {

    fun load(): BatteryInfo {
        val uevent = Root.readText("/sys/class/power_supply/battery/uevent") ?: ""
        val m = HashMap<String, String>()
        uevent.lineSequence().forEach { line ->
            val i = line.indexOf('=')
            if (i > 0) m[line.substring(0, i).removePrefix("POWER_SUPPLY_")] = line.substring(i + 1)
        }
        val ua = m["current_now"]?.toFloatOrNull() ?: 0f   // µA，放电为负
        val uv = m["voltage_now"]?.toFloatOrNull() ?: 0f   // µV
        val curMa = ua / 1000f
        val voltV = uv / 1_000_000f
        return BatteryInfo(
            status = m["status"] ?: "Unknown",
            capacity = m["capacity"]?.toIntOrNull() ?: 0,
            currentMa = curMa,
            voltageV = voltV,
            tempC = (m["temp"]?.toFloatOrNull() ?: 0f) / 10f,
            powerW = abs(curMa * voltV) / 1000f,
            chargeFullMah = ((m["charge_full"]?.toFloatOrNull() ?: 0f) / 1000f).toInt(),
            chargeCounterMah = ((m["charge_counter"]?.toFloatOrNull() ?: 0f) / 1000f).toInt(),
            cycleCount = m["cycle_count"]?.toIntOrNull() ?: 0,
            technology = m["technology"] ?: "",
        )
    }
}
