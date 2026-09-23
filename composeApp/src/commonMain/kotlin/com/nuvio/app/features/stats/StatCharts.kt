package com.nuvio.app.features.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** One slice/bar: a label, its numeric value, and the color it draws with. */
data class ChartDatum(
    val label: String,
    val value: Float,
    val color: Color,
)

/**
 * A fixed categorical palette. AniList's own genre/format colors aren't exposed per-stat, so this is
 * a stable, high-contrast set reused everywhere a chart needs distinct series colors.
 */
val statChartPalette: List<Color> = listOf(
    Color(0xFF3DB4F2), // blue
    Color(0xFF7B61FF), // violet
    Color(0xFFFF6B6B), // red
    Color(0xFF2ECC71), // green
    Color(0xFFFFA94D), // orange
    Color(0xFFFFD43B), // yellow
    Color(0xFF22D3EE), // cyan
    Color(0xFFF06595), // pink
    Color(0xFF94D82D), // lime
    Color(0xFFA78BFA), // lavender
)

fun paletteColor(index: Int): Color = statChartPalette[index % statChartPalette.size]

/**
 * Animated donut with a center caption. Tapping a slice selects it and swaps the caption to that
 * slice's label + value. All slices animate in from zero on first composition.
 */
@Composable
fun DonutChart(
    data: List<ChartDatum>,
    modifier: Modifier = Modifier,
    centerPrimary: String,
    centerSecondary: String,
) {
    if (data.isEmpty()) return
    val total = data.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
    var selected by remember { mutableStateOf(-1) }
    val sweep by animateFloatAsState(targetValue = 1f, animationSpec = tween(700), label = "donutSweep")

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .aspectRatio(1f),
            ) {
                val stroke = size.minDimension * 0.16f
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                var startAngle = -90f
                data.forEachIndexed { index, datum ->
                    val fraction = datum.value / total
                    val fullSweep = fraction * 360f * sweep
                    val selectedStroke = if (index == selected) stroke * 1.25f else stroke
                    drawArc(
                        color = datum.color,
                        startAngle = startAngle,
                        sweepAngle = fullSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = selectedStroke, cap = StrokeCap.Butt),
                    )
                    startAngle += fraction * 360f
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val primary = if (selected in data.indices) formatCompact(data[selected].value) else centerPrimary
                val secondary = if (selected in data.indices) data[selected].label else centerSecondary
                Text(
                    text = primary,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        ChartLegend(
            data = data,
            selectedIndex = selected,
            onToggle = { index -> selected = if (selected == index) -1 else index },
        )
    }
}

@Composable
private fun ChartLegend(
    data: List<ChartDatum>,
    selectedIndex: Int,
    onToggle: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        data.forEachIndexed { index, datum ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggle(index) }
                    .background(
                        if (index == selectedIndex) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        else Color.Transparent,
                    )
                    .padding(vertical = 3.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(datum.color),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = datum.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatCompact(datum.value),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Vertical bars with labels beneath — for release-year or score distributions. Bars animate up on
 * first composition; tapping a bar shows its value above it.
 */
@Composable
fun VerticalBarChart(
    data: List<ChartDatum>,
    modifier: Modifier = Modifier,
    barColor: Color,
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.value }.coerceAtLeast(1f)
    var selected by remember { mutableStateOf(-1) }
    val grow by animateFloatAsState(targetValue = 1f, animationSpec = tween(650), label = "barGrow")

    Row(
        modifier = modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEachIndexed { index, datum ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selected = if (selected == index) -1 else index },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (index == selected) {
                    Text(
                        text = formatCompact(datum.value),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                val heightFraction = (datum.value / maxValue) * grow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((110f * heightFraction).dp.coerceAtLeast(3.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (index == selected) barColor else barColor.copy(alpha = 0.75f)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = datum.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Ranked horizontal bars — for genres, tags, studios. Each row is a label, an animated filled track
 * proportional to the top value, and the raw count.
 */
@Composable
fun HorizontalBarList(
    data: List<ChartDatum>,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.value }.coerceAtLeast(1f)
    val grow by animateFloatAsState(targetValue = 1f, animationSpec = tween(700), label = "hbarGrow")
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        data.forEach { datum ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = datum.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formatCompact(datum.value),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(trackColor),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((datum.value / maxValue) * grow)
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(datum.color),
                    )
                }
            }
        }
    }
}

/** Compact number formatting — 12,340 → "12.3K", drops the ".0". */
fun formatCompact(value: Float): String {
    val v = value.toLong()
    return when {
        v >= 1_000_000 -> trimZero(v / 1_000_000.0) + "M"
        v >= 1_000 -> trimZero(v / 1_000.0) + "K"
        else -> v.toString()
    }
}

private fun trimZero(value: Double): String {
    val rounded = (value * 10).toLong()
    val whole = rounded / 10
    val frac = rounded % 10
    return if (frac == 0L) whole.toString() else "$whole.$frac"
}
