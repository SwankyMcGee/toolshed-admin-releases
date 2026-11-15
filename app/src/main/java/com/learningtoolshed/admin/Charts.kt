package com.learningtoolshed.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ============================================================
   Charts, hand-rolled with Canvas.

   No chart library: the screen needs bars and one donut, and
   Compose's Canvas draws both in a few lines. Keeping it in-repo
   means one less dependency and full control over the colors —
   every chart uses the palette, so it looks like the rest of
   the app.
   ============================================================ */

/** Vertical bar chart. `data` is oldest-first; labels are short. */
@Composable
fun BarChart(
    data: List<Bucket>,
    barColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    barMax: Int = 48
) {
    val max = (data.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barMax.dp)
        ) {
            val count = data.size
            if (count == 0) return@Canvas
            val gap = size.width * 0.08f
            val slot = (size.width - gap * (count - 1)) / count
            data.forEachIndexed { i, bucket ->
                val h = if (max == 0) 0f else (bucket.count.toFloat() / max) * size.height
                val x = i * (slot + gap)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, size.height - h),
                    size = Size(slot, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                )
            }
        }
        if (data.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = data.first().label,
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor,
                    fontSize = 10.sp
                )
                if (data.size > 1) {
                    Text(
                        text = data.last().label,
                        style = MaterialTheme.typography.bodySmall,
                        color = labelColor,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/** Horizontal bars with labels — used for type/status/tools. */
@Composable
fun HBars(
    data: List<Bucket>,
    barColor: Color,
    trackColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val max = (data.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        data.forEach { bucket ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = bucket.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    modifier = Modifier.width(72.dp),
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(trackColor)
                ) {
                    Box(
                        modifier = Modifier
                            .width((bucket.count.toFloat() / max * 100).dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }
                Text(
                    text = bucket.count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp)
                )
            }
        }
    }
}

/** Donut chart — for the tool/feature split. `slices` are
    (label, count, color) in draw order. */
@Composable
fun Donut(
    slices: List<Triple<String, Int, Color>>,
    modifier: Modifier = Modifier,
    holeRatio: Float = 0.55f
) {
    val total = slices.sumOf { it.second }
    Canvas(modifier = modifier) {
        if (total <= 0) return@Canvas
        val stroke = size.minDimension * (1f - holeRatio) / 2f
        val diameter = size.minDimension - stroke * 2f
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)
        var startAngle = -90f
        slices.forEach { (_, count, color) ->
            val sweep = (count.toFloat() / total) * 360f
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
            )
            startAngle += sweep
        }
    }
}

/** A legend row: a swatch, a label, a count. */
@Composable
fun Legend(
    items: List<Triple<String, Int, Color>>,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, count, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}