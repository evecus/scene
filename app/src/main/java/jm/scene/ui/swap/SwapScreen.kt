package jm.scene.ui.swap

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import jm.scene.core.control.SwapControl
import jm.scene.core.control.VmParam
import jm.scene.core.control.ZramState
import jm.scene.core.proc.SwapEntry
import jm.scene.ui.theme.Blue
import jm.scene.ui.theme.Orange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SwapUiState(
    val swaps: List<SwapEntry> = emptyList(),
    val vmParams: List<VmParam> = emptyList(),
    val zram: ZramState? = null,
    val toast: String? = null,
)

class SwapViewModel : ViewModel() {
    private val _state = MutableStateFlow(SwapUiState())
    val state = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val s = SwapUiState(
                swaps = SwapControl.loadSwaps(),
                vmParams = SwapControl.loadVmParams(),
                zram = SwapControl.loadZram(),
            )
            withContext(Dispatchers.Main) { _state.value = s }
        }
    }

    fun toast(msg: String) {
        _state.value = _state.value.copy(toast = msg)
    }

    fun writeVm(key: String, value: String) = viewModelScope.launch(Dispatchers.IO) {
        val ok = withContext(Dispatchers.IO) { SwapControl.writeVmParam(key, value) }
        toast(if (ok) "$key = $value 已写入" else "$key 写入失败")
        refresh()
    }

    fun setZram(sizeMb: Long) = viewModelScope.launch(Dispatchers.IO) {
        val ok = withContext(Dispatchers.IO) { SwapControl.setZramSize(sizeMb) }
        toast(if (ok) "ZRAM 已设为 ${sizeMb}MB" else "ZRAM 设置失败")
        refresh()
    }

    fun createSwap(path: String, sizeMb: Long) = viewModelScope.launch(Dispatchers.IO) {
        val ok = withContext(Dispatchers.IO) { SwapControl.createSwapfile(path, sizeMb) }
        toast(if (ok) "Swapfile $path ($sizeMb MB) 已启用" else "创建失败")
        refresh()
    }

    fun toggle(entry: SwapEntry) = viewModelScope.launch(Dispatchers.IO) {
        val wasOn = _state.value.swaps.any { it.path == entry.path }
        val ok = withContext(Dispatchers.IO) {
            if (wasOn) SwapControl.swapoff(entry.path) else SwapControl.swapon(entry.path)
        }
        toast(if (ok) "${entry.path} 已${if (wasOn) "关闭" else "启用"}" else "操作失败")
        refresh()
    }
}

@Composable
fun SwapScreen(vm: SwapViewModel) {
    val s by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { s.toast?.takeIf { it.isNotBlank() }?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2500)
                vm.toast("")
            }
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                Text(msg.ifBlank { " " }, Modifier.fillMaxWidth().padding(12.dp), fontSize = 13.sp)
            }
        } }

        item { ZramCard(s, vm) }
        item { SwapListCard(s, vm) }
        item { VmParamsCard(s, vm) }
    }
}

@Composable
private fun ZramCard(s: SwapUiState, vm: SwapViewModel) {
    var editSize by remember { mutableStateOf(false) }
    val z = s.zram
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ZRAM", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { editSize = true }, enabled = z?.available == true) { Text("调整大小", color = Blue) }
            }
            if (z == null || !z.available) {
                Text("未检测到 zram0", color = Color(0xFF9E9E9E), fontSize = 13.sp)
            } else {
                InfoLine("磁盘大小", "${z.disksizeKb / 1024} MB")
                InfoLine("压缩算法", z.algo)
                InfoLine("原始数据", "${z.origDataKb / 1024} MB")
                InfoLine("压缩后", "${z.comprDataKb / 1024} MB")
                InfoLine("压缩比", if (z.ratio > 0) "%.2f:1".format(z.ratio) else "--")
            }
        }
    }
    if (editSize && z != null) {
        NumberDialog(
            title = "ZRAM 大小 (MB)",
            initial = (z.disksizeKb / 1024).toString(),
            onOk = { vm.setZram(it); editSize = false },
            onCancel = { editSize = false },
        )
    }
}

@Composable
private fun SwapListCard(s: SwapUiState, vm: SwapViewModel) {
    var newSwap by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用的 Swap", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { newSwap = true }) { Text("创建 Swapfile") }
            }
            if (s.swaps.isEmpty()) {
                Text("无启用的 Swap", color = Color(0xFF9E9E9E), fontSize = 13.sp)
            } else {
                s.swaps.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.path, fontSize = 13.sp, maxLines = 1)
                            Text(
                                "${e.type} · ${e.sizeKb / 1024}MB · 已用 ${e.usedKb / 1024}MB · 优先级 ${e.priority}",
                                fontSize = 11.sp, color = Color(0xFF9E9E9E),
                            )
                        }
                        TextButton(onClick = { vm.toggle(e) }) { Text("Swapoff", color = Orange) }
                    }
                }
            }
        }
    }
    if (newSwap) {
        var path by remember { mutableStateOf("/data/local/tmp/swapfile") }
        var size by remember { mutableStateOf("4096") }
        AlertDialog(
            onDismissRequest = { newSwap = false },
            title = { Text("创建 Swapfile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("路径") })
                    OutlinedTextField(value = size, onValueChange = { size = it }, label = { Text("大小 (MB)") })
                    Text("将执行 dd + mkswap + swapon（root）", fontSize = 11.sp, color = Color(0xFF9E9E9E))
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.createSwap(path, size.toLongOrNull() ?: 4096)
                    newSwap = false
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { newSwap = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun VmParamsCard(s: SwapUiState, vm: SwapViewModel) {
    var editing by remember { mutableStateOf<VmParam?>(null) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("VM 参数 (/proc/sys/vm)", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            s.vmParams.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.label, fontSize = 13.sp)
                    }
                    TextButton(onClick = { editing = p }) {
                        Text(p.value.ifBlank { "--" }, color = Blue)
                    }
                }
            }
        }
    }
    editing?.let { p ->
        NumberDialog(
            title = p.key,
            initial = p.value,
            onOk = { vm.writeVm(p.key, it.toString()); editing = null },
            onCancel = { editing = null },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, fontSize = 13.sp, color = Color(0xFF757575))
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NumberDialog(title: String, initial: String, onOk: (Long) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it.filter { c -> c.isDigit() } })
        },
        confirmButton = {
            Button(onClick = { text.toLongOrNull()?.let(onOk) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}
