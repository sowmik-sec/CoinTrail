package com.cointrail.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cointrail.core.Money

/** Calm, fixed chart palette, matching the app's deliberate green/teal theme (SPEC §10). */
private val CategoryChartColors: List<Color> = listOf(
    Color(0xFF00897B),
    Color(0xFFB07A2B),
    Color(0xFF5B7FA6),
    Color(0xFF8E5FA8),
    Color(0xFFA44A3F),
    Color(0xFF4C7A34),
    Color(0xFF3F8C8C),
    Color(0xFF7E6A9E),
)

private fun categoryChartColor(index: Int): Color = CategoryChartColors[index % CategoryChartColors.size]

/** A 2° separation between donut wedges, applied only to wedges wide enough to spare it. */
private const val SEGMENT_GAP = 2f

private const val MIN_GAP_SWEEP = 6f

/** The per-category breakdown drawn as horizontal bars, longest (biggest) category first. */
@Composable
fun CategoryBars(rows: List<CategoryBreakdownRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEachIndexed { index, row ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(categoryChartColor(index))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${row.sharePercent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = row.amount.format(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(row.barFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(categoryChartColor(index)),
                    )
                }
            }
        }
    }
}

/** The same breakdown as a donut, with the month total in the middle and a legend beside it. */
@Composable
fun CategoryDonut(rows: List<CategoryBreakdownRow>, total: Money, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 26.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var startAngle = -90f
                rows.forEachIndexed { index, row ->
                    val sweep = 360f * row.amount.paisa / total.paisa
                    if (sweep > 0f) {
                        // Inset only wedges wide enough to spare it, so tiny categories still get a slice.
                        val gap = if (rows.size > 1 && sweep > MIN_GAP_SWEEP) SEGMENT_GAP else 0f
                        drawArc(
                            color = categoryChartColor(index),
                            startAngle = startAngle + gap / 2,
                            sweepAngle = sweep - gap,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
                        )
                    }
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = total.format(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEachIndexed { index, row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(categoryChartColor(index))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${row.sharePercent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color) {
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
}
