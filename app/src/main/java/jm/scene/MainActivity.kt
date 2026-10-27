package jm.scene

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jm.scene.ui.apps.AppsScreen
import jm.scene.ui.apps.AppsViewModel
import jm.scene.ui.backup.BackupScreen
import jm.scene.ui.backup.BackupViewModel
import jm.scene.ui.charge.ChargeScreen
import jm.scene.ui.charge.ChargeViewModel
import jm.scene.ui.cpu.CpuControlScreen
import jm.scene.ui.cpu.CpuControlViewModel
import jm.scene.ui.feature.FeatureScreen
import jm.scene.ui.overview.OverviewScreen
import jm.scene.ui.overview.OverviewViewModel
import jm.scene.ui.misc.MiscScreen
import jm.scene.ui.misc.MiscViewModel
import jm.scene.ui.power.PowerMenuScreen
import jm.scene.ui.process.ProcessScreen
import jm.scene.ui.process.ProcessViewModel
import jm.scene.ui.record.FpsRecordScreen
import jm.scene.ui.record.FpsViewModel
import jm.scene.ui.record.PowerScreen
import jm.scene.ui.record.PowerViewModel
import jm.scene.ui.swap.SwapScreen
import jm.scene.ui.swap.SwapViewModel
import jm.scene.ui.theme.Blue
import jm.scene.ui.theme.SceneTheme

private val tabs = listOf("功能", "概览", "调节", "用户")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SceneTheme {
                MainScaffold()
            }
        }
    }
}

@Composable
private fun MainScaffold() {
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val vm = remember { OverviewViewModel() }
    val cpuVm = remember { CpuControlViewModel() }
    val swapVm = remember { SwapViewModel() }
    val procVm = remember { ProcessViewModel() }
    val appsVm = remember { AppsViewModel() }
    val fpsVm = remember { FpsViewModel() }
    val powerVm = remember { PowerViewModel() }
    val chargeVm = remember { ChargeViewModel() }
    val miscVm = remember { MiscViewModel() }
    val backupVm = remember { BackupViewModel() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部 Tab（对照截图：功能/概览/调节/用户）
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, t ->
                Column(
                    Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .clickableNoRipple { tab = i; page = null },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        t,
                        fontSize = 14.sp,
                        fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                        color = if (tab == i) Blue else Color(0xFF9E9E9E),
                    )
                    if (tab == i) {
                        Box(
                            Modifier.padding(top = 2.dp).fillMaxWidth(0.4f)
                                .height(3.dp).background(Blue, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                page == "cpu" -> SubPage("CPU 控制", { page = null }) { CpuControlScreen(cpuVm) }
                page == "swap" -> SubPage("SWAP 设置", { page = null }) { SwapScreen(swapVm) }
                page == "process" -> SubPage("进程管理", { page = null }) { ProcessScreen(procVm) }
                page == "apps" -> SubPage("应用管理", { page = null }) { AppsScreen(appsVm) }
                page == "components" -> SubPage("活动 / 服务", { page = null }) { AppsScreen(appsVm, mode = "components") }
                page == "fps" -> SubPage("帧率记录", { page = null }) { FpsRecordScreen(fpsVm) }
                page == "power" -> SubPage("耗电 / 充电统计", { page = null }) { PowerScreen(powerVm) }
                page == "charge" -> SubPage("充电控制", { page = null }) { ChargeScreen(chargeVm) }
                page == "misc" -> SubPage("杂项设置", { page = null }) { MiscScreen(miscVm) }
                page == "backup" -> SubPage("备份还原", { page = null }) { BackupScreen(backupVm) }
                tab == 0 -> FeatureScreen(onNavigate = { page = it })
                tab == 1 -> OverviewScreen(vm)
                tab == 2 -> AdjustTab(onNavigate = { page = it })
                else -> PlaceholderTab(tabs[tab])
            }
        }
    }
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = Blue) }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        content()
    }
}

@Composable
private fun AdjustTab(onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().clickable { onNavigate("charge") },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, null, tint = Blue)
                        Spacer(Modifier.padding(4.dp))
                        Text("充电控制", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        PowerMenuScreen()
    }
}

@Composable
private fun PlaceholderTab(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name · 敬请期待", color = Color(0xFF9E9E9E))
    }
}

// 顶部 tab 无 ripple 点击
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}
