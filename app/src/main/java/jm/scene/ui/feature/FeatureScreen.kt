package jm.scene.ui.feature

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jm.scene.ui.theme.Blue

private data class FeatureItem(val label: String, val icon: ImageVector, val route: String)
private data class FeatureGroup(val title: String, val items: List<FeatureItem>)

private val groups = listOf(
    FeatureGroup("设备性能", listOf(
        FeatureItem("CPU 控制", Icons.Filled.Memory, "cpu"),
        FeatureItem("SWAP 设置", Icons.Filled.AcUnit, "swap"),
    )),
    FeatureGroup("充电耗电", listOf(
        FeatureItem("耗电统计", Icons.Filled.Speed, "power"),
        FeatureItem("充电统计", Icons.Filled.Bolt, "power"),
        FeatureItem("帧率记录", Icons.Filled.GraphicEq, "fps"),
    )),
    FeatureGroup("应用与权限", listOf(
        FeatureItem("进程管理", Icons.Filled.Biotech, "process"),
        FeatureItem("应用管理", Icons.Filled.Apps, "apps"),
        FeatureItem("活动 / 服务", Icons.Filled.PlayCircleOutline, "components"),
    )),
    FeatureGroup("进阶功能", listOf(
        FeatureItem("杂项设置", Icons.Filled.Tune, "misc"),
        FeatureItem("备份还原", Icons.Filled.Backup, "backup"),
        FeatureItem("充电控制", Icons.Filled.Bolt, "charge"),
    )),
)

@Composable
fun FeatureScreen(onNavigate: (String) -> Unit = {}) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { g ->
            item { GroupTitle(g.title) }
            // 每行两个卡片
            val items = g.items
            var i = 0
            while (i < items.size) {
                val a = items[i]
                val b = items.getOrNull(i + 1)
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureCard(a, Modifier.weight(1f), onNavigate)
                        if (b != null) FeatureCard(b, Modifier.weight(1f), onNavigate)
                        else Spacer(Modifier.weight(1f))
                    }
                }
                i += 2
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, fontSize = 13.sp, color = Color(0xFF757575), modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun FeatureCard(item: FeatureItem, modifier: Modifier, onNavigate: (String) -> Unit) {
    Card(
        modifier = modifier.clickable { onNavigate(item.route) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(item.icon, null, tint = Blue)
            Spacer(Modifier.width(10.dp))
            Text(item.label, fontWeight = FontWeight.Medium)
        }
    }
}
