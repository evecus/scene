package jm.scene.core.daemon

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** scene-rs fps record JSONL 行 */
@Serializable
data class FpsRec(
    val t: Long,
    val fps: Float,
    val frames: Int = 0,
    val avg_frame_ms: Float = 0f,
    val jitter_ms: Float = 0f,
)

/** scene-rs power record JSONL 行 */
@Serializable
data class PowerRec(
    val t: Long,
    val capacity: Int = 0,
    val current_ua: Long = 0,
    val voltage_uv: Long = 0,
    val power_w: Float = 0f,
    val temp_deci: Int = 0,
    val status: String = "",
)

object Records {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseFps(lines: List<String>): List<FpsRec> = lines.mapNotNull {
        try { json.decodeFromString<FpsRec>(it) } catch (_: Exception) { null }
    }

    fun parsePower(lines: List<String>): List<PowerRec> = lines.mapNotNull {
        try { json.decodeFromString<PowerRec>(it) } catch (_: Exception) { null }
    }
}
