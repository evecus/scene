package jm.scene.ui.cpu

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import jm.scene.core.control.CpuClusterState
import jm.scene.core.control.CpuControl
import jm.scene.ui.theme.Blue
import jm.scene.ui.theme.Orange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CpuUiState(
    val clusters: List<CpuClusterState> = emptyList(),
    val toast: String? = null,
)

class CpuControlViewModel : ViewModel() {

    private val _state = MutableStateFlow(CpuUiState())
    val state = _state.asStateFlow()

    // cluster policy -> relatedCores
    private var map: List<Pair<String, List<Int>>> = emptyList()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            if (map.isEmpty()) {
                map = mutableListOf<Pair<String, List<Int>>>().also { out ->
                    val base = "/sys/devices/system/cpu/cpufreq"
                    Root.cmd("ls -d $base/policy* 2>/dev/null").map { it.substringAfterLast('/') }
                        .forEach { p ->
                            val related = Root.readText("$base/$p/related_cpus")
                                ?.split(Regex("\\s+"))?.mapNotNull(String::toIntOrNull)
                                ?: listOf(p.removePrefix("policy").toIntOrNull() ?: 0)
                            out.add(p to related)
                        }
                }.distinctBy { it.second }
            }
            val clusters = map.map { (p, cores) -> CpuControl.loadCluster(p, cores) }
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(clusters = clusters) }
        }
    }

    fun toast(msg: String) {
        _state.value = _state.value.copy(toast = msg)
    }

    fun clearToast() {
        _state.value = _state.value.copy(toast = null)
    }

    private fun write(label: String, ok: Boolean) {
        toast(if (ok) "$label 成功" else "$label 失败（可能被内核限制或需要重新校准）")
        refresh()
    }

    fun setMin(policy: String, kHz: Int) = viewModelScope.launch(Dispatchers.IO) {
        val ok = withContext(Dispatchers.IO) { CpuControl.writeMinFreq(policy, kHz) }
        write("设置最小频率 $kHz", ok)
    }

    fun setMax(policy: String, kHz: Int) = viewModelScope.launch(Dispatchers.IO) {
        val ok = withContext(Dispatchers.IO) { CpuControl.writeMaxFreq(policy, kHz) }
        write("设置最大频率 $kHz", ok)
    }

    fun setGovernor(policy: String, governor: String) = viewModelScope.launch(Dispatchers.IO) {
        val ok = withContext(Dispatchers.IO) { CpuControl.writeGovernor(policy, governor) }
        write("设置 governor $governor", ok)
    }

    fun setCoreOnline(core: Int, online: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val ok = withContext(Dispatchers.IO) { CpuControl.setCoreOnline(core, online) }
        write("Core$core ${if (online) "Online" else "Offline"}", ok)
    }
}

@Composable
fun CpuControlScreen(vm: CpuControlViewModel) {
    val s by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(s.clusters.size) { i ->
            ClusterCard(s.clusters[i], vm)
        }
        item {
            s.toast?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2500)
                    vm.clearToast()
                }
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Text(msg, Modifier.fillMaxWidth().padding(12.dp), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ClusterCard(c: CpuClusterState, vm: CpuControlViewModel) {
    var pickMin by remember { mutableStateOf(false) }
    var pickMax by remember { mutableStateOf(false) }
    var pickGov by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${c.policy}  (CPU${c.relatedCores.first()}~CPU${c.relatedCores.last()})",
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${c.curMinKHz / 1000} - ${c.curMaxKHz / 1000} MHz",
                    fontSize = 13.sp, color = Orange,
                )
            }

            // Governor 选择
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Governor", fontSize = 13.sp, color = Color(0xFF757575))
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = true,
                    onClick = { if (c.availableGovernors.isNotEmpty()) pickGov = true },
                    label = { Text(c.governor.ifBlank { "未知" }) },
                )
            }

            // 频率选择
            Row {
                TextButton(onClick = { if (c.availableFreqsKHz.isNotEmpty()) pickMin = true }) {
                    Text("Min：${c.curMinKHz / 1000}MHz", color = Blue)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { if (c.availableFreqsKHz.isNotEmpty()) pickMax = true }) {
                    Text("Max：${c.curMaxKHz / 1000}MHz", color = Blue)
                }
            }

            // Core Online 开关
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                c.onlineByCore.forEach { (core, online) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("C$core", fontSize = 11.sp, color = Color(0xFF757575))
                        Switch(
                            checked = online,
                            onCheckedChange = { vm.setCoreOnline(core, it) },
                            enabled = core != c.relatedCores.first(),
                        )
                    }
                }
            }
        }
    }

    if (pickGov) {
        PickDialog(
            title = "选择 Governor",
            options = c.availableGovernors,
            current = c.governor,
            format = { it },
        ) {
            pickGov = false
            it?.let { g -> vm.setGovernor(c.policy, g) }
        }
    }
    if (pickMin) {
        PickDialog(
            title = "最小频率",
            options = c.availableFreqsKHz.sorted(),
            current = c.curMinKHz,
            format = { "${it / 1000} MHz" },
        ) {
            pickMin = false
            it?.let { f -> vm.setMin(c.policy, f) }
        }
    }
    if (pickMax) {
        PickDialog(
            title = "最大频率",
            options = c.availableFreqsKHz.sorted(),
            current = c.curMaxKHz,
            format = { "${it / 1000} MHz" },
        ) {
            pickMax = false
            it?.let { f -> vm.setMax(c.policy, f) }
        }
    }
}

@Composable
private fun PickDialog(
    title: String,
    options: List<Any>,
    current: Any,
    format: (Any) -> String,
    onPick: (Any?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onPick(null) },
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options.size) { i ->
                    val v = options[i]
                    TextButton(onClick = { onPick(v) }) {
                        Text(
                            format(v),
                            color = if (v == current) Blue else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (v == current) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("取消") } },
    )
}
