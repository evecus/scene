package jm.scene.ui.backup

import android.content.Context
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
import kotlinx.coroutines.withContext
import java.io.File

data class BackupUiState(
    val partitions: List<Pair<String, String>> = emptyList(), // name -> path
    val backups: List<Triple<String, Long, String>> = emptyList(), // file, size, partition
    val busy: Boolean = false,
    val toast: String? = null,
)

class BackupViewModel : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state = _state.asStateFlow()
    private var contextRef: Context? = null

    fun init(ctx: Context) {
        if (contextRef != null) return
        contextRef = ctx.applicationContext
        viewModelScope.launch(Dispatchers.IO) { refreshBackups() }
    }

    private fun backupDir(): File = File(contextRef!!.filesDir, "backups").apply { mkdirs() }

    fun refreshBackups() {
        val dir = backupDir()
        _state.value = _state.value.copy(
            backups = dir.listFiles()?.map { Triple(it.name, it.length(), it.name.substringBefore('.')) }
                ?.sortedByDescending { it.second } ?: emptyList(),
        )
    }

    /** 惰性加载分区列表（ls /dev/block/by-name 可能较慢） */
    fun loadPartitions() = viewModelScope.launch(Dispatchers.IO) {
        val parts = Root.cmd("ls -1 /dev/block/by-name 2>/dev/null || ls -1 /dev/block/bootdevice/by-name 2>/dev/null")
            .map { it.trim() to "/dev/block/by-name/${it.trim()}" }
        _state.value = _state.value.copy(partitions = parts)
    }

    fun backup(name: String, path: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.value = _state.value.copy(busy = true, toast = "正在备份 $name …")
        val dest = File(backupDir(), "$name.img")
        val r = Root.cmd("dd if='$path' of='${dest.absolutePath}' bs=4M")
        _state.value = _state.value.copy(
            busy = false,
            toast = if (dest.exists()) "备份完成: ${dest.name} (${dest.length() / 1024 / 1024}MB)" else "备份失败: ${r.joinToString()}",
        )
        refreshBackups()
    }

    fun restore(fileName: String, partition: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.value = _state.value.copy(busy = true, toast = "正在刷入 $fileName → $partition …")
        val src = File(backupDir(), fileName)
        val r = Root.cmd("dd if='${src.absolutePath}' of='$partition' bs=4M")
        _state.value = _state.value.copy(busy = false, toast = if (r.isEmpty()) "刷入完成" else "刷入返回: ${r.joinToString()}")
    }

    fun delete(fileName: String) = viewModelScope.launch(Dispatchers.IO) {
        File(backupDir(), fileName).delete()
        toast("已删除 $fileName")
        refreshBackups()
    }

    fun toast(msg: String) {
        _state.value = _state.value.copy(toast = msg)
    }
}

@Composable
fun BackupScreen(vm: BackupViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { vm.init(ctx); vm.loadPartitions() }
    val s by vm.state.collectAsState()
    var restoreTarget by remember { mutableStateOf<Triple<String, Long, String>?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            s.toast?.let { msg ->
                LaunchedEffect(msg) { delay(3000); vm.toast("") }
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Text(msg, Modifier.fillMaxWidth().padding(12.dp), fontSize = 13.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("分区备份 (${s.partitions.size})", fontWeight = FontWeight.Bold)
                    Text("点按分区 → dd 镜像到应用目录（root，耗时视分区大小）", fontSize = 11.sp, color = Color(0xFF9E9E9E))
                    Spacer(Modifier.height(8.dp))
                    if (s.partitions.isEmpty()) Text("加载中…", fontSize = 12.sp, color = Color(0xFF9E9E9E))
                    val common = listOf("boot", "dtbo", "vendor_boot", "init_boot", "vbmeta", "recovery", "misc")
                    val shown = s.partitions.filter { p -> common.any { p.first.startsWith(it) } }
                    shown.forEach { (name, path) ->
                        TextButton(onClick = { if (!s.busy) vm.backup(name, path) }) {
                            Text("备份 $name", color = jm.scene.ui.theme.Blue, fontSize = 13.sp)
                        }
                    }
                    TextButton(onClick = { if (!s.busy) vm.backup("super", "/dev/block/by-name/super") }) {
                        Text("备份 super (大, 慎用)", color = Color(0xFFE65100), fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("本地备份 (filesDir/backups)", fontWeight = FontWeight.Bold)
                    if (s.backups.isEmpty()) {
                        Text("暂无备份", fontSize = 12.sp, color = Color(0xFF9E9E9E))
                    } else {
                        s.backups.forEach { (name, size, part) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontSize = 13.sp, maxLines = 1)
                                    Text("${size / 1024 / 1024}MB", fontSize = 11.sp, color = Color(0xFF9E9E9E))
                                }
                                TextButton(onClick = { restoreTarget = Triple(name, size, part) }) { Text("刷入", color = Color(0xFFE65100)) }
                                TextButton(onClick = { vm.delete(name) }) { Text("删除", color = Color(0xFFE53935)) }
                            }
                        }
                    }
                }
            }
        }
    }

    restoreTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("确认刷入") },
            text = { Text("将把 ${t.first} (${t.second / 1024 / 1024}MB) 写入对应分区。\n\n这是危险操作，分区错误可能导致无法开机！") },
            confirmButton = {
                Button(onClick = {
                    vm.restore(t.first, "/dev/block/by-name/${t.first.substringBefore('.')}")
                    restoreTarget = null
                }) { Text("确认刷入", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text("取消") } },
        )
    }
}
