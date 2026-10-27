package jm.scene.ui.record

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jm.scene.core.daemon.PowerRec
import jm.scene.core.daemon.Records
import jm.scene.core.daemon.SceneRs
import jm.scene.ui.theme.Blue
import jm.scene.ui.theme.Orange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

data class PowerUiState(
    val running: Boolean = false,
    val samples: List<PowerRec> = emptyList(),
    val sessions: List<File> = emptyList(),
    val toast: String? = null,
)

class PowerViewModel : ViewModel() {
    private val _state = MutableStateFlow(PowerUiState())
    val state = _state.asStateFlow()
    private var contextRef: Context? = null
    private var viewing: File? = null

    fun init(ctx: Context) {
        if (contextRef != null) return
        contextRef = ctx.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val c = contextRef ?: break
                    val running = SceneRs.isRunning(c, "power")
                    val src = viewing ?: SceneRs.currentSession(c, "power")
                    val samples = src?.let { Records.parsePower(readTail(it)) } ?: emptyList()
                    _state.value = _state.value.copy(
                        running = running,
                        samples = samples,
                        sessions = SceneRs.sessions(c, "power"),
                    )
                } catch (_: Exception) {
                    // 单轮失败忽略，下轮重试
                }
                delay(1000)
            }
        }
    }

    private fun readTail(f: File): List<String> = try {
        f.readLines().filter { it.isNotBlank() }.takeLast(720)
    } catch (_: Exception) { emptyList() }

    fun view(file: File?) {
        viewing = file
    }

    fun start() {
        val ctx = contextRef ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val r = SceneRs.start(ctx, "power", 5000)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    toast = r.fold({ "电源采样已开始 (5s)" }, { "启动失败: ${it.message}" }),
                )
            }
        }
    }

    fun stop() {
        val ctx = contextRef ?: return
        viewModelScope.launch(Dispatchers.IO) { SceneRs.stopCurrent(ctx, "power") }
    }

    fun toast(msg: String) {
        _state.value = _state.value.copy(toast = msg)
    }
}

@Composable
fun PowerScreen(vm: PowerViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { vm.init(ctx) }
    val s by vm.state.collectAsState()

    val list = s.samples
    val caps = list.map { it.capacity.toFloat() }
    val powers = list.map { it.power_w }
    val stats = computeStats(list)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            s.toast?.let { msg ->
                LaunchedEffect(msg) { delay(2500); vm.toast("") }
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Text(msg, Modifier.fillMaxWidth().padding(12.dp), fontSize = 13.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("耗电 / 充电统计", fontWeight = FontWeight.Bold)
                        Text(
                            if (s.running) "采集中 (scene-rs 5s)" else "未运行",
                            fontSize = 12.sp,
                            color = if (s.running) Color(0xFF43A047) else Color(0xFF9E9E9E),
                        )
                    }
                    if (s.running) Button(onClick = { vm.stop() }) { Text("停止") }
                    else Button(onClick = { vm.start() }) { Text("开始记录") }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        PStat("平均功率", "%.2fW".format(stats.avgPower), Orange, Modifier.weight(1f))
                        PStat("平均电流", "%.0fmA".format(stats.avgCurrentMa), Blue, Modifier.weight(1f))
                        PStat("时长", stats.durationStr, Blue, Modifier.weight(1f))
                        PStat("电量变化", "%+.0f%%".format(stats.deltaCap), Orange, Modifier.weight(1f))
                    }
                    Row {
                        PStat(
                            if (stats.charging) "充电速度" else "理论续航",
                            if (stats.charging) "%+.1f%%/h".format(stats.ratePerHour)
                            else "%.1fh".format(stats.theoreticalHours),
                            Blue, Modifier.weight(1f),
                        )
                        PStat("状态", stats.status, Color(0xFF757575), Modifier.weight(1f))
                        Spacer(Modifier.weight(2f))
                    }
                    Text("电量 %", fontSize = 11.sp, color = Color(0xFF9E9E9E))
                    LineChart(caps, Blue, Modifier.fillMaxWidth().height(120.dp), labelMax = "capacity %")
                    Text("功率 W", fontSize = 11.sp, color = Color(0xFF9E9E9E))
                    LineChart(powers, Orange, Modifier.fillMaxWidth().height(120.dp), labelMax = "power W")
                }
            }
        }
        item {
            Text("历史会话 (${s.sessions.size})", fontSize = 13.sp, color = Color(0xFF757575), modifier = Modifier.padding(start = 4.dp))
        }
        items(s.sessions.size) { i ->
            val f = s.sessions[i]
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                onClick = { vm.view(f) },
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(f.name, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${f.length() / 1024}KB", fontSize = 12.sp, color = Color(0xFF9E9E9E))
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { vm.view(null) }) { Text("当前", color = Blue) }
                }
            }
        }
    }
}

private data class PowerStats(
    val avgPower: Float,
    val avgCurrentMa: Float,
    val durationStr: String,
    val deltaCap: Int,
    val charging: Boolean,
    val ratePerHour: Float,
    val theoreticalHours: Float,
    val status: String,
)

private fun computeStats(list: List<PowerRec>): PowerStats {
    if (list.size < 2) return PowerStats(0f, 0f, "--", 0, false, 0f, 0f, "--")
    val first = list.first()
    val last = list.last()
    val durMs = (last.t - first.t).coerceAtLeast(1)
    val hours = durMs / 3600000f
    val dCap = last.capacity - first.capacity
    val charging = last.current_ua > 0
    val avgPower = list.map { it.power_w }.average().toFloat()
    val avgMa = list.map { it.current_ua }.average().toFloat() / 1000f
    val rate = if (hours > 0) dCap / hours else 0f
    val theoretical = if (!charging && abs(dCap) > 0) hours * 100f / abs(dCap) else 0f
    val h = durMs / 3600000
    val m = durMs % 3600000 / 60000
    return PowerStats(
        avgPower = avgPower,
        avgCurrentMa = avgMa,
        durationStr = "%dh%02dm".format(h, m),
        deltaCap = dCap,
        charging = charging,
        ratePerHour = rate,
        theoreticalHours = theoretical,
        status = last.status.ifBlank { "--" },
    )
}

@Composable
private fun PStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color, maxLines = 1)
        Text(label, fontSize = 11.sp, color = Color(0xFF9E9E9E))
    }
}
