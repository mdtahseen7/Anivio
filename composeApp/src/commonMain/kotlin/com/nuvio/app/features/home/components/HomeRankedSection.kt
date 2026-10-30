package com.nuvio.app.features.home.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState

@Composable
fun HomeRankedSection(
    section: HomeCatalogSection,
    sectionPadding: Dp,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    val entries = section.items.take(10)
    if (entries.isEmpty()) return

    Text(
        text = section.title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sectionPadding),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = sectionPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(entries, key = { _, item -> item.stableKey() }) { index, item ->
            RankedHomeCard(
                rank = index + 1,
                item = item,
                watched = WatchingState.isPosterWatched(
                    watchedKeys = watchedKeys,
                    item = item,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                ),
                onClick = onPosterClick?.let { { it(item) } },
                onLongClick = onPosterLongClick?.let { { it(item) } },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun RankedHomeCard(
    rank: Int,
    item: MetaPreview,
    watched: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    val transition = rememberInfiniteTransition(label = "rank-$rank")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = rank * 90),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rank-pulse-$rank",
    )
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD166)
        2 -> Color(0xFFD8DEE9)
        3 -> Color(0xFFE39A63)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
    }

    Box(
        modifier = Modifier.width(206.dp).height(292.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = rank.toString(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 28.dp)
                // The poster intentionally overlaps the outlined rank. Keep the outline above it
                // so both digits of rank 10 remain readable instead of hiding the zero.
                .zIndex(1f)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                },
            color = rankColor.copy(alpha = 0.6f),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 112.sp,
                fontWeight = FontWeight.Black,
                drawStyle = Stroke(width = 3.5f),
            ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 4.dp)
                .background(Color.Transparent),
        ) {
            HomePosterCard(
                item = item,
                modifier = Modifier.width(142.dp),
                isWatched = watched,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
    }
}
