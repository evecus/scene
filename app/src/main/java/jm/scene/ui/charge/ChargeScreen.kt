package jm.scene.ui.charge

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import jm.scene.core.control.ChargeControl
import jm.scene.core.control.ChargeNodes
import jm.scene.core.proc.BatteryInfoLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChargeUiState(
    val nodes: ChargeNodes? = null,
    val battery: jm.scene.core.proc.BatteryInfo? = null,
    val guardRunning: Boolean = false,
    val toast: String? = null,
)

class ChargeViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChargeUiState())
    val state = _state.asStateFlow()
    private var contextRef: Context? = null

    fun init(ctx: Context) {
        if (contextRef != null) return
        contextRef = ctx.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val c = contextRef ?: break
                    _state.value = _state.value.copy(
                        nodes = ChargeControl.load(),
                        battery = jm.scene.core.proc.BatteryInfoLoader.load(),
                        guardRunning = ChargeControl.Guard.isRunning(c),
                    )
                } catch (_: Exception) {
                    // 单轮失败忽略
                }
                delay(2000)
            }
        }
    }

    fun toast(msg: String) {
        _state.value = _state.value.copy(toast = msg)
    }

    fun setSuspend(on: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val p = _state.value.nodes?.suspendPath ?: return@launch
        val ok = withContext(Dispatchers.IO) { ChargeControl.writeSuspend(p, on) }
        toast(if (ok) (if (on) "已暂停充电" else "已恢复充电") else "写入失败")
    }

    fun setLimit(v: Int) = viewModelScope.launch(Dispatchers.IO) {
        val p = _state.value.nodes?.limitPath ?: return@launch
        val ok = withContext(Dispatchers.IO) { ChargeControl.writeLimit(p, v) }
        toast(if (ok) "充电限值已设为 $v" else "写入失败")
    }

    fun setCcMax(ua: Int) = viewModelScope.launch(Dispatchers.IO) {
        val p = _state.value.nodes?.ccMaxPath ?: return@launch
        val ok = withContext(Dispatchers.IO) { ChargeControl.writeCcMax(p, ua) }
        toast(if (ok) "限流已设为 ${ua / 1000}mA" else "写入失败")
    }

    fun startGuard(stopPct: Int, startPct: Int) {
        val c = contextRef ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val r = ChargeControl.Guard.start(c, stopPct, startPct)
            toast(r.fold({ it }, { "启动失败: ${it.message}" }))
        }
    }

    fun stopGuard() {
        val c = contextRef ?: return
        viewModelScope.launch(Dispatchers.IO) { ChargeControl.Guard.stop(c); toast("充电保护已停止") }
    }
}

@Composable
fun ChargeScreen(vm: ChargeViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { vm.init(ctx) }
    val s by vm.state.collectAsState()
    val bat = s.battery
    var pickLimit by remember { mutableStateOf(false) }
    var pickCc by remember { mutableStateOf(false) }
    var guardDlg by remember { mutableStateOf(false) }

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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("电池状态", fontWeight = FontWeight.Bold)
                    InfoLine("电量", bat?.let { "${it.capacity}%  " + "%.3fV, %.2fW, %.0fmA".format(it.voltageV, it.powerW, it.currentMa) } ?: "--")
                    InfoLine("温度 / 循环", bat?.let { "%.1f℃ / ${it.cycleCount}".format(it.tempC) } ?: "--")
                    InfoLine("状态", bat?.status ?: "--")
                }
            }
        }
        val n = s.nodes
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("充电控制", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("暂停充电 (input_suspend)")
                        Spacer(Modifier.weight(1f))
                        if (n?.suspendPath != null) {
                            Switch(checked = n.suspended, onCheckedChange = { vm.setSuspend(it) })
                        } else Text("不支持", color = Color(0xFF9E9E9E), fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("充电限值 (charge_control_limit)")
                        Spacer(Modifier.weight(1f))
                        if (n?.limitPath != null && n.limitMax > 0) {
                            FilterChip(selected = true, onClick = { pickLimit = true }, label = { Text("${readLimit(n)} %") })
                        } else Text("不支持", color = Color(0xFF9E9E9E), fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("限流 (constant_charge_current_max)")
                        Spacer(Modifier.weight(1f))
                        if (n?.ccMaxPath != null) {
                            FilterChip(selected = true, onClick = { pickCc = true }, label = { Text("${n.ccMaxNow / 1000} mA") })
                        } else Text("不支持", color = Color(0xFF9E9E9E), fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("充电保护 (scene-rs 守护)", fontWeight = FontWeight.Bold)
                        Text(
                            if (s.guardRunning) "运行中 · App 退出仍生效" else "到设定电量自动停充，回落自动恢复",
                            fontSize = 12.sp,
                            color = if (s.guardRunning) Color(0xFF43A047) else Color(0xFF9E9E9E),
                        )
                    }
                    if (s.guardRunning) Button(onClick = { vm.stopGuard() }) { Text("停止") }
                    else OutlinedButton(onClick = { guardDlg = true }) { Text("启动") }
                }
            }
        }
    }

    if (pickLimit) {
        val n = s.nodes ?: return
        PickNumDialog("充电限值 (%)", (0..n.limitMax).toList().map { it }) { pickLimit = false; it?.let { vm.setLimit(it) } }
    }
    if (pickCc) {
        PickNumDialog("限流 (mA)", listOf(500, 750, 1000, 1500, 2000, 3000, 4000)) {
            pickCc = false; it?.let { vm.setCcMax(it * 1000) }
        }
    }
    if (guardDlg) {
        var stop by remember { mutableStateOf("90") }
        var start by remember { mutableStateOf("70") }
        AlertDialog(
            onDismissRequest = { guardDlg = false },
            title = { Text("充电保护") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(value = stop, onValueChange = { stop = it.filter { c -> c.isDigit() } }, label = { Text("停充电量 (%)") })
                    androidx.compose.material3.OutlinedTextField(value = start, onValueChange = { start = it.filter { c -> c.isDigit() } }, label = { Text("恢复电量 (%)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.startGuard(stop.toIntOrNull() ?: 90, start.toIntOrNull() ?: 70)
                    guardDlg = false
                }) { Text("启动") }
            },
            dismissButton = { TextButton(onClick = { guardDlg = false }) { Text("取消") } },
        )
    }
}

private fun readLimit(n: ChargeNodes): Int =
    n.limitPath?.let { jm.scene.core.root.Root.readInt(it) } ?: 0

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, fontSize = 13.sp, color = Color(0xFF757575))
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PickNumDialog(title: String, options: List<Int>, onPick: (Int?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onPick(null) },
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options.size) { i ->
                    TextButton(onClick = { onPick(options[i]) }) { Text("${options[i]}") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("取消") } },
    )
}
