package jm.scene.ui.process

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jm.scene.core.proc.CpuInfo
import jm.scene.core.proc.ProcessManager
import jm.scene.core.proc.ProcStat
import jm.scene.ui.theme.Orange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProcessUiState(
    val procs: List<ProcEntry> = emptyList(),
    val query: String = "",
    val toast: String? = null,
)

class ProcessViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProcessUiState())
    val state = _state.asStateFlow()

    private var prev: Map<Int, Long> = emptyMap()
    private var contextRef: Context? = null

    fun init(ctx: Context) {
        contextRef = ctx.applicationContext
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val cur = CpuInfo.sampleProcs()
                val byPid = cur.entries.associate { (k, v) -> k.first to v }
                val tops = CpuInfo.topProcesses(prev, cur, 2.0f, limit = 60)
                prev = byPid
                val ctx = contextRef
                val entries = tops.map {
                    ProcEntry(it.pid, it.name, it.rssKb, it.cpuPercent, ctx?.let { c -> ProcessManager.resolvePackage(c, it.name) })
                }
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(procs = entries)
                }
                delay(2000)
            }
        }
    }

    fun kill(pid: Int) = viewModelScope.launch(Dispatchers.IO) {
        val ok = withContext(Dispatchers.IO) { ProcessManager.kill(pid) }
        toast(if (ok) "进程 $pid 已结束" else "结束 $pid 失败")
    }

    fun forceStop(pkg: String) = viewModelScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.IO) { ProcessManager.forceStop(pkg) }
        toast("已 force-stop $pkg")
    }

    fun toast(msg: String) {
        _state.value = _state.value.copy(toast = msg)
    }
}

data class ProcEntry(val pid: Int, val name: String, val rssKb: Long, val cpuPercent: Float, val pkg: String?)

@Composable
fun ProcessScreen(vm: ProcessViewModel) {
    val s by vm.state.collectAsState()
    var selected by remember { mutableStateOf<ProcEntry?>(null) }

    Column(Modifier.fillMaxSize()) {
        s.toast?.let { msg ->
            LaunchedEffect(msg) { delay(2500); vm.toast("") }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) { Text(msg, Modifier.padding(12.dp), fontSize = 13.sp) }
        }
        OutlinedTextField(
            value = s.query,
            onValueChange = vm::setQuery,
            label = { Text("搜索进程") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        val shown = s.procs.filter {
            s.query.isBlank() || it.name.contains(s.query, true)
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown.size) { i ->
                val p = shown[i]
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    onClick = { selected = p },
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("%.1f%%".format(p.cpuPercent), fontSize = 13.sp, color = Orange, modifier = Modifier.width(64.dp))
                        Text("${p.rssKb / 1024}MB", fontSize = 12.sp, color = Color(0xFF9E9E9E), modifier = Modifier.width(70.dp))
                        Text("${p.pid}", fontSize = 11.sp, color = Color(0xFFBDBDBD))
                    }
                }
            }
        }
    }

    selected?.let { p ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(p.name, maxLines = 2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("PID: ${p.pid}   RSS: ${p.rssKb / 1024}MB   CPU: %.1f%%".format(p.cpuPercent))
                    p.pkg?.let { Text("包名: $it", fontSize = 12.sp, color = Color(0xFF757575)) }
                }
            },
            confirmButton = {
                Column {
                    TextButton(onClick = { vm.kill(p.pid); selected = null }) { Text("结束进程 (kill -9)", color = Color(0xFFE53935)) }
                    p.pkg?.let {
                        TextButton(onClick = { vm.forceStop(it); selected = null }) { Text("Force Stop") }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("取消") } },
        )
    }
}
