package jm.scene.ui.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.max

/** 简单折线图：values 归一化绘制，带 max 标注 */
@Composable
fun LineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    labelMax: String? = null,
) {
    androidx.compose.foundation.layout.Box(modifier) {
        if (values.size < 2) {
            Text(
                "数据不足",
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E),
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val vmax = max(values.max(), 0.001f)
                val vmin = 0f
                val range = (vmax - vmin).coerceAtLeast(0.001f)
                val n = values.size
                val stepX = size.width / (n - 1)
                // 网格 4 条
                for (g in 1..3) {
                    val y = size.height * g / 4f
                    drawLine(Color(0xFFE0E0E0), Offset(0f, y), Offset(size.width, y), 1.5f)
                }
                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = size.height - (v - vmin) / range * size.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                // 最后点高亮
                val lastX = (n - 1) * stepX
                val lastY = size.height - (values.last() - vmin) / range * size.height
                drawCircle(color, radius = 6f, center = Offset(lastX, lastY))
            }
        }
        labelMax?.let {
            Text(
                it,
                fontSize = 10.sp,
                color = Color(0xFF9E9E9E),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
