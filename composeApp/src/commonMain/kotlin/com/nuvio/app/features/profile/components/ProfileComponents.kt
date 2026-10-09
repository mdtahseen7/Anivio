package com.nuvio.app.features.profile.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvio

/**
 * A circular XP progress ring with a center slot. Used by the identity header to show the current
 * level inside the ring and animate progress toward the next level.
 */
@Composable
fun LevelRing(
    progress: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 96.dp,
    stroke: Dp = 8.dp,
    trackColor: Color = MaterialTheme.nuvio.colors.borderSubtle,
    ringBrush: Brush = Brush.sweepGradient(
        listOf(
            MaterialTheme.nuvio.colors.accent,
            MaterialTheme.nuvio.colors.accentStrong,
            MaterialTheme.nuvio.colors.accent,
        ),
    ),
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "levelRing",
    )
    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    brush = ringBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** A single big-number stat tile, used in grids across the profile screen. */
@Composable
fun ProfileStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.nuvio.colors.textPrimary,
    icon: ImageVector? = null,
) {
    val tokens = MaterialTheme.nuvio
    val isAccented = accent != tokens.colors.textPrimary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    if (isAccented) {
                        listOf(accent.copy(alpha = 0.20f), tokens.colors.surfaceElevated)
                    } else {
                        listOf(tokens.colors.surfaceElevated, tokens.colors.surfaceElevated)
                    },
                ),
            )
            .border(
                width = 1.dp,
                color = if (isAccented) accent.copy(alpha = 0.28f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isAccented) accent else tokens.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            value,
            color = if (isAccented) accent else tokens.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
        Text(
            label,
            color = tokens.colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * GitHub-style activity heatmap. Days are laid out in columns of 7 (one week per column), oldest on
 * the left. Intensity 0..4 maps to increasingly saturated accent cells.
 */
@Composable
fun ActivityHeatmap(
    intensities: List<Int>,
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.nuvio.colors.accent,
    emptyColor: Color = MaterialTheme.nuvio.colors.surfaceElevated,
    cellSize: Dp = 13.dp,
    cellGap: Dp = 3.dp,
) {
    val weeks = intensities.chunked(7)
    val lastIndex = intensities.lastIndex
    var index = 0
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(cellGap),
    ) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                week.forEach { intensity ->
                    val isToday = index == lastIndex
                    val fill = if (intensity <= 0) {
                        Brush.verticalGradient(listOf(emptyColor, emptyColor))
                    } else {
                        val top = (0.30f + 0.175f * intensity).coerceAtMost(1f)
                        Brush.verticalGradient(
                            listOf(
                                baseColor.copy(alpha = top),
                                baseColor.copy(alpha = (top - 0.12f).coerceAtLeast(0.12f)),
                            ),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .clip(RoundedCornerShape(3.dp))
                            .background(fill)
                            .then(
                                if (isToday) {
                                    Modifier.border(1.dp, baseColor, RoundedCornerShape(3.dp))
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    index++
                }
            }
        }
    }
}

/** A labelled horizontal progress bar used for genre/studio/score distributions. */
@Composable
fun ProfileBarRow(
    label: String,
    value: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.nuvio.colors.accent,
) {
    val tokens = MaterialTheme.nuvio
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "barRow",
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = tokens.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(value, color = tokens.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(tokens.colors.surfaceElevated),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(barColor),
            )
        }
    }
}

/** A vertical bar used by the hour/weekday histograms. */
@Composable
fun ProfileVerticalBar(
    fraction: Float,
    label: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 72.dp,
) {
    val tokens = MaterialTheme.nuvio
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "vbar",
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.height(maxHeight).width(10.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxHeight * animated.coerceAtLeast(0.04f))
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(
                        Brush.verticalGradient(
                            if (highlighted) {
                                listOf(tokens.colors.accent, tokens.colors.accentStrong)
                            } else {
                                listOf(
                                    tokens.colors.borderStrong,
                                    tokens.colors.borderStrong.copy(alpha = 0.45f),
                                )
                            },
                        ),
                    ),
            )
        }
        Text(label, color = tokens.colors.textMuted, fontSize = 9.sp)
    }
}

/** Small accent-tinted pill chip for section tags / counts. */
@Composable
fun ProfilePill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.nuvio.colors.accent,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SectionSpacer(height: Dp = 20.dp) {
    Spacer(Modifier.height(height))
}

@Composable
internal fun ProfileSquare(modifier: Modifier = Modifier, content: @Composable BoxScopeAlias.() -> Unit) {
    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        content()
    }
}

typealias BoxScopeAlias = androidx.compose.foundation.layout.BoxScope
