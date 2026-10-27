package jm.scene.core.control

import jm.scene.core.root.Root

data class ChargeNodes(
    val limitPath: String?,      // charge_control_limit
    val limitMax: Int,           // charge_control_limit_max
    val ccMaxPath: String?,      // constant_charge_current_max (µA)
    val ccMaxNow: Int,           // 当前限流 µA
    val suspendPath: String?,    // input_suspend
    val suspended: Boolean,
)

object ChargeControl {

    private val bases = listOf("/sys/class/power_supply/battery", "/sys/class/power_supply/main", "/sys/class/power_supply/usb")

    private fun find(suffix: String): String? =
        bases.map { "$it/$suffix" }.firstOrNull { Root.readText(it) != null }

    fun load(): ChargeNodes {
        val limitPath = find("charge_control_limit")
        val limitMax = find("charge_control_limit_max")?.let { Root.readInt(it) } ?: 0
        val ccPath = find("constant_charge_current_max")
        val suspendPath = find("input_suspend")
        return ChargeNodes(
            limitPath = limitPath,
            limitMax = limitMax,
            ccMaxPath = ccPath,
            ccMaxNow = ccPath?.let { Root.readInt(it) } ?: 0,
            suspendPath = suspendPath,
            suspended = suspendPath?.let { Root.readText(it)?.trim() == "1" } ?: false,
        )
    }

    fun writeSuspend(path: String, suspend: Boolean): Boolean {
        Root.cmd("echo '${if (suspend) 1 else 0}' > '$path'")
        return Root.readText(path)?.trim() == (if (suspend) "1" else "0")
    }

    fun writeLimit(path: String, value: Int): Boolean {
        Root.cmd("echo '$value' > '$path'")
        return Root.readInt(path) == value
    }

    fun writeCcMax(path: String, ua: Int): Boolean {
        Root.cmd("echo '$ua' > '$path'")
        return Root.readInt(path) == ua
    }

    /** 场景式充电保护：交给 scene-rs charge guard 后台跑 */
    object Guard {
        fun start(ctx: android.content.Context, stopPct: Int, startPct: Int): Result<String> =
            jm.scene.core.daemon.SceneRs.startChargeGuard(ctx, stopPct, startPct)

        fun stop(ctx: android.content.Context) =
            jm.scene.core.daemon.SceneRs.stopChargeGuard(ctx)

        fun isRunning(ctx: android.content.Context): Boolean =
            jm.scene.core.daemon.SceneRs.isChargeGuardRunning(ctx)
    }
}
