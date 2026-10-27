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
import androidx.compose.material3.OutlinedButton
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
import jm.scene.core.daemon.FpsRec
import jm.scene.core.daemon.Records
import jm.scene.core.daemon.SceneRs
import jm.scene.ui.theme.Blue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FpsUiState(
    val running: Boolean = false,
    val samples: List<FpsRec> = emptyList(),
    val sessions: List<File> = emptyList(),
    val toast: String? = null,
)

class FpsViewModel : ViewModel() {
    private val _state = MutableStateFlow(FpsUiState())
    val state = _state.asStateFlow()
    private var contextRef: Context? = null
    private var viewing: File? = null // null = 跟随当前会话

    fun init(ctx: Context) {
        if (contextRef != null) return
        contextRef = ctx.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val ctx2 = contextRef ?: break
                    val running = SceneRs.isRunning(ctx2, "fps")
                    val src = viewing ?: SceneRs.currentSession(ctx2, "fps")
                    val samples = src?.let { Records.parseFps(readTail(it)) } ?: emptyList()
                    val sessions = SceneRs.sessions(ctx2, "fps")
                    _state.value = _state.value.copy(running = running, samples = samples, sessions = sessions)
                } catch (_: Exception) {
                    // 单轮失败忽略，下轮重试，避免未捕获异常导致闪退
                }
                delay(1000)
            }
        }
    }

    private fun readTail(f: File): List<String> = try {
        f.readLines().filter { it.isNotBlank() }.takeLast(240)
    } catch (_: Exception) { emptyList() }

    fun view(file: File?) {
        viewing = file
    }

    fun start() {
        val ctx = contextRef ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val r = SceneRs.start(ctx, "fps", 500)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    toast = r.fold({ "帧率记录已开始 (500ms)" }, { "启动失败: ${it.message}" }),
                )
            }
        }
    }

    fun stop() {
        val ctx = contextRef ?: return
        viewModelScope.launch(Dispatchers.IO) {
            SceneRs.stopCurrent(ctx, "fps")
        }
    }

    fun toast(msg: String) {
        _state.value = _state.value.copy(toast = msg)
    }
}

@Composable
fun FpsRecordScreen(vm: FpsViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { vm.init(ctx) }
    val s by vm.state.collectAsState()

    val list = s.samples
    val fpsVals = list.map { it.fps }
    val avg = fpsVals.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
    val max = fpsVals.maxOrNull() ?: 0f
    val min = fpsVals.minOrNull() ?: 0f
    val jitter = list.map { it.jitter_ms }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f

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
                        Text("帧率记录", fontWeight = FontWeight.Bold)
                        Text(
                            if (s.running) "采集中 (scene-rs)" else "未运行",
                            fontSize = 12.sp,
                            color = if (s.running) Color(0xFF43A047) else Color(0xFF9E9E9E),
                        )
                    }
                    if (s.running) {
                        Button(onClick = { vm.stop() }) { Text("停止") }
                    } else {
                        Button(onClick = { vm.start() }) { Text("开始记录") }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Stat("Avg", "%.0f".format(avg), Modifier.weight(1f))
                        Stat("Max", "%.0f".format(max), Modifier.weight(1f))
                        Stat("Min", "%.0f".format(min), Modifier.weight(1f))
                        Stat("Jitter", "%.1fms".format(jitter), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    LineChart(
                        values = fpsVals,
                        color = Blue,
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        labelMax = "FPS",
                    )
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

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Blue)
        Text(label, fontSize = 11.sp, color = Color(0xFF9E9E9E))
    }
}
