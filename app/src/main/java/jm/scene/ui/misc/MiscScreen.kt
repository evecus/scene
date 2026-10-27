package jm.scene.ui.misc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import jm.scene.core.root.Root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MiscUiState(
    val dpi: String = "",
    val model: String = "",
    val device: String = "",
    val wifi: List<Pair<String, String>> = emptyList(), // ssid -> psk
    val toast: String? = null,
)

class MiscViewModel : ViewModel() {
    private val _state = MutableStateFlow(MiscUiState())
    val state = _state.asStateFlow()

    fun refresh() = viewModelScope.launch(Dispatchers.IO) {
        val dpi = Root.cmd("wm size").firstOrNull() ?: ""
        val model = Root.cmd("getprop ro.product.model").firstOrNull() ?: ""
        val device = Root.cmd("getprop ro.product.device").firstOrNull() ?: ""
        val wifi = loadWifiPsk()
        _state.value = _state.value.copy(dpi = dpi, model = model, device = device, wifi = wifi)
    }

    /** 解析 WifiConfigStore 中的 SSID/PSK 对 */
    private fun loadWifiPsk(): List<Pair<String, String>> {
        val text = Root.cmd("cat /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml")
            .ifEmpty { Root.cmd("cat /data/misc/wifi/WifiConfigStore.xml") }
            .joinToString("\n")
        if (text.isEmpty()) return emptyList()
        val ssidRe = Regex("<string name=\"SSID\">([^<]+)</string>")
        val pskRe = Regex("<string name=\"PreSharedKey\">([^<]*)</string>")
        val ssids = ssidRe.findAll(text).map { it.groupValues[1] }.toList()
        val psks = pskRe.findAll(text).map { it.groupValues[1] }.toList()
        return ssids.indices.mapNotNull { i ->
            val ssid = ssids[i].removeSurrounding("\"")
            val psk = psks.getOrNull(i)?.removeSurrounding("\"") ?: ""
            ssid to psk
        }
    }

    fun setDpi(density: String) = viewModelScope.launch(Dispatchers.IO) {
        val r = Root.cmd("wm density $density").joinToString("; ")
        toast("已执行 wm density $density ${r.takeLast(40)}")
        refresh()
    }

    fun setModel(model: String) = viewModelScope.launch(Dispatchers.IO) {
        Root.cmd("resetprop ro.product.model '$model'")
        Root.cmd("setprop ro.product.model '$model'")
        toast("已写入 ro.product.model=$model（重启或部分系统后生效）")
        refresh()
    }

    fun dex2oatAll() = viewModelScope.launch(Dispatchers.IO) {
        Root.cmd("cmd package compile -m everything -a -f --check-prof false -r bg-dexopt")
        toast("全量 dex2oat 已触发（后台执行中）")
    }

    fun toast(msg: String) {
        _state.value = _state.value.copy(toast = msg)
    }
}

@Composable
fun MiscScreen(vm: MiscViewModel) {
    LaunchedEffect(Unit) { vm.refresh() }
    val s by vm.state.collectAsState()
    var dpiDlg by remember { mutableStateOf(false) }
    var modelDlg by remember { mutableStateOf(false) }

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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("显示", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("分辨率/DPI", fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { dpiDlg = true }) { Text(s.dpi.ifBlank { "--" }, color = jm.scene.ui.theme.Blue) }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("机型修改", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("model / device", fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { modelDlg = true }) {
                            Text("${s.model} / ${s.device}", color = jm.scene.ui.theme.Blue, maxLines = 1)
                        }
                    }
                    Text("通过 setprop/resetprop 写 ro.product.*，需 Magisk resetprop 支持才能持久", fontSize = 11.sp, color = Color(0xFF9E9E9E))
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("WiFi 密码", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Button(onClick = { vm.dex2oatAll() }) { Text("全局 dex2oat") }
                    }
                    if (s.wifi.isEmpty()) {
                        Text("未读取到（需 root + 已保存网络）", fontSize = 12.sp, color = Color(0xFF9E9E9E))
                    } else {
                        s.wifi.forEach { (ssid, psk) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(ssid, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(psk.ifBlank { "(开放网络)" }, fontSize = 12.sp, color = jm.scene.ui.theme.Blue)
                            }
                        }
                    }
                }
            }
        }
    }

    if (dpiDlg) {
        var txt by remember { mutableStateOf("440") }
        AlertDialog(
            onDismissRequest = { dpiDlg = false },
            title = { Text("修改 DPI") },
            text = { OutlinedTextField(value = txt, onValueChange = { txt = it.filter { c -> c.isDigit() } }, label = { Text("密度 (如 440)") }) },
            confirmButton = { Button(onClick = { vm.setDpi(txt); dpiDlg = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { dpiDlg = false }) { Text("取消") } },
        )
    }
    if (modelDlg) {
        var txt by remember { mutableStateOf(s.model) }
        AlertDialog(
            onDismissRequest = { modelDlg = false },
            title = { Text("修改机型") },
            text = { OutlinedTextField(value = txt, onValueChange = { txt = it }, label = { Text("ro.product.model") }) },
            confirmButton = { Button(onClick = { vm.setModel(txt); modelDlg = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { modelDlg = false }) { Text("取消") } },
        )
    }
}
