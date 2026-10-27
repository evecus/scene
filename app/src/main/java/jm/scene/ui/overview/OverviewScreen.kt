package jm.scene.ui.overview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jm.scene.core.proc.Cluster
import jm.scene.core.proc.ProcStat
import jm.scene.ui.theme.Blue
import jm.scene.ui.theme.Orange
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun OverviewScreen(vm: OverviewViewModel) {
    val s by vm.state.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MemoryCard(s) }
        item { GpuCard(s) }
        item { CpuCard(s) }
        item { BottomGrid(s) }
    }
}

/* ---------------- 内存卡片 ---------------- */

@Composable
private fun MemoryCard(s: OverviewState) {
    val mem = s.memory ?: return
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Donut(
                percent = mem.usedPercent,
                label = "Memory",
                stroke = 16.dp,
                trackColor = Color(0xFFE0E0E0),
                valueColor = Orange,
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BarLine("物理内存", mem.usedPercent, formatGb(mem.totalKb))
                BarLine("交换分区", mem.swapUsedPercent, formatGb(mem.swapTotalKb))
                val totalPct = mem.usedTotalPercent
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Used $totalPct%", fontSize = 12.sp, color = Color(0xFF757575))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "(${if (totalPct > 100) "+" else ""}${totalPct - 100}%)",
                        fontSize = 12.sp, color = Blue, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("SwapCached ${formatMb(mem.swapCachedKb)}", fontSize = 12.sp, color = Color(0xFF757575))
                }
            }
        }
    }
}

@Composable
private fun BarLine(label: String, pct: Int, sizeGb: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$pct%", fontWeight = FontWeight.Bold, color = Blue)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(sizeGb, fontSize = 12.sp, color = Color(0xFF9E9E9E))
        }
        LinearProgressIndicator(
            progress = { (pct / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            trackColor = Color(0xFFE8E8E8),
        )
    }
}

/* ---------------- GPU 卡片 ---------------- */

@Composable
private fun GpuCard(s: OverviewState) {
    val gpu = s.gpu ?: return
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Donut(
                percent = gpu.loadPercent.coerceAtLeast(0),
                label = "GPU",
                stroke = 16.dp,
                trackColor = Color(0xFFE0E0E0),
                valueColor = Blue,
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("${gpu.curFreqKHz / 1000}MHz", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("负载：${gpu.loadPercent.coerceAtLeast(0)}%", fontSize = 13.sp, color = Color(0xFF757575))
                Spacer(Modifier.height(4.dp))
                Text(gpu.model, fontSize = 11.sp, color = Color(0xFF9E9E9E))
                if (gpu.maxFreqKHz > 0) {
                    Text(
                        "${gpu.minFreqKHz / 1000} - ${gpu.maxFreqKHz / 1000} MHz",
                        fontSize = 11.sp, color = Color(0xFF9E9E9E),
                    )
                }
            }
        }
    }
}

/* ---------------- CPU 卡片 ---------------- */

@Composable
private fun CpuCard(s: OverviewState) {
    if (s.clusters.isEmpty()) return
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.topProcs.forEach { p -> TopProcLine(p) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (s.cpuTempC.isNaN()) "--" else "%.1f℃".format(s.cpuTempC),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Orange,
                    )
                    Spacer(Modifier.height(8.dp))
                    MiniBarChart(listOf(0f, 0f, 0f, 0f), height = 60.dp, empty = true)
                    Text("CPU", fontSize = 12.sp, color = Color(0xFF757575))
                    Text("${s.socLoadPercent}%", fontSize = 12.sp, color = Color(0xFF757575))
                    Text(s.socName, fontSize = 11.sp, color = Color(0xFF9E9E9E), textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(16.dp))
            // 每 cluster 一行：relatedCores 拆成 per-core 小图
            s.clusters.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val cols = c.relatedCores.size.coerceAtLeast(1)
                    c.relatedCores.forEach { core ->
                        val f = s.coreFreqKHz[core] ?: 0
                        val h = s.coreHistory[core] ?: emptyList()
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            MiniBarChart(h)
                            Text("${f / 1000}MHz", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${c.minFreqKHz / 1000} - ${c.maxFreqKHz / 1000}MHz",
                                fontSize = 9.sp, color = Color(0xFFBDBDBD),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopProcLine(p: ProcStat) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(Blue.copy(alpha = 0.3f))
        )
        Spacer(Modifier.width(6.dp))
        Text(p.name, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Text("%.1f%%".format(p.cpuPercent), fontSize = 11.sp, color = Color(0xFF757575))
    }
}

/* ---------------- 底部信息宫格 ---------------- */

@Composable
private fun BottomGrid(s: OverviewState) {
    val bat = s.battery
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(Modifier.weight(1f), "功率", bat?.let { "%.2fW".format(it.powerW) } ?: "--")
        InfoCard(Modifier.weight(1f), "SDK", "SDK(${android.os.Build.VERSION.SDK_INT})")
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(Modifier.weight(1f), "电池", bat?.let { "${it.capacity}%  " + "%.3fv".format(it.voltageV) } ?: "--")
        InfoCard(Modifier.weight(1f), "已开机", s.uptimeStr.ifEmpty { "--" })
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(
            Modifier.weight(1f), "温度",
            if (s.cpuTempC.isNaN()) "--" else "%.1f℃".format(s.cpuTempC),
        )
        InfoCard(Modifier.weight(1f), "SoC", s.socName)
    }
}

@Composable
private fun InfoCard(modifier: Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 11.sp, color = Color(0xFF9E9E9E))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/* ---------------- 通用绘制 ---------------- */

@Composable
private fun Donut(percent: Int, label: String, stroke: androidx.compose.ui.unit.Dp, trackColor: Color, valueColor: Color) {
    val target = (percent / 100f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, tween(500), label = "donut")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val st = stroke.toPx()
            val inset = st / 2
            drawArc(
                color = trackColor,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - st, size.height - st),
                style = Stroke(st, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    color = valueColor,
                    startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - st, size.height - st),
                    style = Stroke(st, cap = StrokeCap.Round),
                )
            }
        }
        Text(label, fontSize = 15.sp, color = Color(0xFF9E9E9E))
    }
}

/** 每 CPU 核的迷你柱状图（最近 5 次采样），h 值 0..100 */
@Composable
private fun MiniBarChart(history: List<Float>, height: androidx.compose.ui.unit.Dp = 44.dp, empty: Boolean = false) {
    val colors = listOf(
        Color(0xFF64B5F6), Color(0xFF42A5F5), Color(0xFF2196F3),
        Color(0xFF1E88E5), Color(0xFF1976D2),
    )
    Canvas(Modifier.width(52.dp).height(height)) {
        val n = 5
        val gap = 3.dp.toPx()
        val barW = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val v = if (empty) 0f else (history.getOrNull(history.size - n + i) ?: 0f) / 100f
            val h = (size.height * v).coerceAtLeast(2f)
            drawRoundRect(
                color = colors[i % colors.size],
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
            )
        }
    }
}

private fun formatGb(kb: Long): String = "%.1fGB".format(kb / 1024.0 / 1024.0)
private fun formatMb(kb: Long): String = "${kb / 1024}MB"
