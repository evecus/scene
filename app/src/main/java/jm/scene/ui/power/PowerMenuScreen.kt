package jm.scene.ui.power

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jm.scene.core.root.Root

private data class PowerAction(val label: String, val desc: String, val icon: ImageVector, val cmd: String, val danger: Boolean = false)

private val actions = listOf(
    PowerAction("关机", "reboot -p", Icons.Filled.PowerSettingsNew, "reboot -p", true),
    PowerAction("重启", "reboot", Icons.Filled.Refresh, "reboot", true),
    PowerAction("热重启", "stop && start (重启框架)", Icons.Filled.RestartAlt, "stop; sleep 1; start"),
    PowerAction("Recovery", "reboot recovery", Icons.Filled.Settings, "reboot recovery", true),
    PowerAction("Bootloader / Fastboot", "reboot bootloader", Icons.Filled.Settings, "reboot bootloader", true),
    PowerAction("EDL (9008)", "reboot edl", Icons.Filled.Bolt, "reboot edl", true),
)

@Composable
fun PowerMenuScreen() {
    var confirm by remember { mutableStateOf<PowerAction?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("电源菜单（root）", fontSize = 13.sp, color = Color(0xFF757575), modifier = Modifier.padding(start = 4.dp))
        }
        items(actions.size) { i ->
            val a = actions[i]
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                onClick = { confirm = a },
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(a.icon, null, tint = if (a.danger) Color(0xFFE53935) else jm.scene.ui.theme.Blue)
                    Spacer(Modifier.padding(6.dp))
                    Column {
                        Text(a.label, fontWeight = FontWeight.Medium)
                        Text(a.desc, fontSize = 11.sp, color = Color(0xFF9E9E9E))
                    }
                }
            }
        }
    }

    confirm?.let { a ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("确认${a.label}？") },
            text = { Text("将执行: ${a.cmd}${if (a.danger) "\n\n请保存好当前工作。" else ""}") },
            confirmButton = {
                Button(onClick = { Root.cmd(a.cmd); confirm = null }) { Text("执行") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } },
        )
    }
}
