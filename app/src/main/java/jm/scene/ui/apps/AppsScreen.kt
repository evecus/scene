package jm.scene.ui.apps

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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jm.scene.core.apps.AppEntry
import jm.scene.core.apps.AppManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppsUiState(
    val apps: List<AppEntry> = emptyList(),
    val filter: String = "installed", // installed / system
    val query: String = "",
    val loading: Boolean = true,
    val toast: String? = null,
)

class AppsViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppsUiState())
    val state = _state.asStateFlow()
    private var contextRef: Context? = null

    fun init(ctx: Context) {
        if (contextRef == null) {
            contextRef = ctx.applicationContext
            refresh()
        }
    }

    fun setFilter(f: String) {
        _state.value = _state.value.copy(filter = f)
        refresh()
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun refresh() {
        val ctx = contextRef ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            val apps = AppManager.loadApps(ctx, _state.value.filter)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(apps = apps, loading = false)
            }
        }
    }

    fun op(desc: String, block: suspend () -> String) = viewModelScope.launch(Dispatchers.IO) {
        val msg = withContext(Dispatchers.IO) { block() }
        toast("$desc: $msg")
        refresh()
    }

    fun toast(msg: String) {
        _state.value = _state.value.copy(toast = msg)
    }
}

@Composable
fun AppsScreen(vm: AppsViewModel, mode: String = "apps") {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { vm.init(ctx) }
    val s by vm.state.collectAsState()
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    var actPkg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        s.toast?.takeIf { it.isNotBlank() }?.let { msg ->
            LaunchedEffect(msg) { delay(2500); vm.toast("") }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) { Text(msg, Modifier.padding(12.dp), fontSize = 13.sp) }
        }

        // 过滤条
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("installed" to "已安装", "system" to "系统").forEach { (k, label) ->
                FilterChip(
                    selected = s.filter == k,
                    onClick = { vm.setFilter(k) },
                    label = { Text(label) },
                )
            }
        }
        Text(
            if (s.loading) "加载中…" else "${s.apps.size} 个应用",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 12.sp, color = Color(0xFF9E9E9E),
        )

        val shown = s.apps.filter {
            s.query.isBlank() || it.label.contains(s.query, true) || it.pkg.contains(s.query, true)
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown.size) { i ->
                val a = shown[i]
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    onClick = {
                        if (mode == "components") actPkg = a.pkg else selected = a
                    },
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        a.icon?.let {
                            Image(it.asImageBitmap(), null, Modifier.size(38.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.label, fontSize = 14.sp, maxLines = 1)
                                if (a.frozen) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("已冻结", fontSize = 10.sp, color = Color(0xFFE53935))
                                }
                            }
                            Text(a.pkg, fontSize = 11.sp, color = Color(0xFF9E9E9E), maxLines = 1)
                        }
                        Text("${a.sizeBytes / 1024 / 1024}MB", fontSize = 12.sp, color = Color(0xFF9E9E9E))
                    }
                }
            }
        }
    }

    // 应用操作对话框
    selected?.let { a ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(a.label) },
            text = { Text(a.pkg, fontSize = 12.sp, color = Color(0xFF757575)) },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        vm.op("卸载") { AppManager.uninstall(a.pkg) }; selected = null
                    }) { Text("卸载", color = Color(0xFFE53935)) }
                    TextButton(onClick = {
                        vm.op("清除数据") { AppManager.clearData(a.pkg) }; selected = null
                    }) { Text("清除数据") }
                    TextButton(onClick = {
                        vm.op(if (a.frozen) "解冻" else "冻结") { AppManager.setFrozen(a.pkg, !a.frozen) }
                        selected = null
                    }) { Text(if (a.frozen) "解冻" else "冻结") }
                    TextButton(onClick = {
                        vm.op("dex2oat") { AppManager.dex2oat(a.pkg) }; selected = null
                    }) { Text("dex2oat 全量编译") }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("取消") } },
        )
    }

    // 活动/服务浏览器
    actPkg?.let { pkg ->
        ComponentsDialog(pkg, onClose = { actPkg = null })
    }
}

@Composable
private fun ComponentsDialog(pkg: String, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val acts = remember(pkg) { AppManager.activities(ctx, pkg) }
    val svcs = remember(pkg) { AppManager.services(ctx, pkg) }
    var toast by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(pkg, fontSize = 14.sp, maxLines = 2) },
        text = {
            LazyColumn(
                modifier = Modifier.height(420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item { Text("Activities (${acts.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                items(acts.size) { i ->
                    val a = acts[i]
                    TextButton(onClick = { toast = AppManager.startActivity(ctx, a.name) }) {
                        Text(a.shortName, fontSize = 12.sp, color = jm.scene.ui.theme.Blue, maxLines = 1)
                    }
                }
                item { Spacer(Modifier.height(8.dp)); Text("Services (${svcs.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                items(svcs.size) { i ->
                    val sv = svcs[i]
                    TextButton(onClick = { toast = AppManager.startService(sv.name) }) {
                        Text(sv.shortName, fontSize = 12.sp, color = Color(0xFFFB8C00), maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("关闭") }
        },
    )
    toast?.let { t ->
        LaunchedEffect(t) { delay(2000); toast = null }
    }
}
