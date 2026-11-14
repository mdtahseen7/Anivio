package com.nuvio.app.features.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.features.home.HomeDiscoveryContent
import com.nuvio.app.features.home.HomeDiscoverySection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

internal fun HomeDiscoveryContent.hasContent(section: HomeDiscoverySection): Boolean = when (section) {
    HomeDiscoverySection.Spotlight -> spotlight != null
    HomeDiscoverySection.Shuffle -> shuffle.size >= 2
    HomeDiscoverySection.Horizon -> horizon.isNotEmpty()
}

@Composable
internal fun HomeDiscoverySectionView(
    section: HomeDiscoverySection,
    title: String,
    content: HomeDiscoveryContent,
    sectionPadding: Dp,
    onItemClick: ((MetaPreview) -> Unit)?,
    onItemLongClick: ((MetaPreview) -> Unit)?,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = sectionPadding, vertical = 12.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (section) {
                HomeDiscoverySection.Spotlight -> content.spotlight?.let { item ->
                    DiscoveryHeading(title)
                    SpotlightCard(item, onItemClick, onItemLongClick)
                }
                HomeDiscoverySection.Shuffle -> ShuffleDeck(title, content.shuffle, onItemClick, onItemLongClick)
                HomeDiscoverySection.Horizon -> {
                    DiscoveryHeading(title)
                    HorizonTimeline(content.horizon, onItemClick, onItemLongClick)
                }
            }
        }
    }
}

@Composable
private fun DiscoveryHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.nuvio.colors.textPrimary)
}

@Composable
private fun SpotlightCard(item: MetaPreview, onClick: ((MetaPreview) -> Unit)?, onLongClick: ((MetaPreview) -> Unit)?) {
    val colors = MaterialTheme.nuvio.colors
    val image = item.banner ?: item.poster
    Box(
        Modifier.fillMaxWidth().heightIn(min = 350.dp).clip(RoundedCornerShape(28.dp))
            .background(colors.surface)
            .posterCardClickable(onClick?.let { { it(item) } }, onLongClick?.let { { it(item) } }, zoomImageUrl = image, zoomCornerRadius = 28.dp),
    ) {
        if (image != null) AsyncImage(image, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.05f),
            0.25f to Color.Black.copy(alpha = 0.72f),
            1f to Color(0xFF101214),
        )))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(top = 110.dp).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item.genres.isNotEmpty()) {
                Text(item.genres.take(2).joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = Color(0xFFD7DCE2))
            }
            Text(item.name, style = MaterialTheme.typography.headlineLarge, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val description = remember(item.description) {
                item.description.orEmpty().replace(Regex("<[^>]+>"), " ")
                    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                    .replace(Regex("\\s+"), " ").trim()
            }
            if (description.isNotEmpty()) Text(description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCED3D9), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.home_discovery_details), style = MaterialTheme.typography.titleSmall, color = Color.White)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun ShuffleDeck(title: String, items: List<MetaPreview>, onClick: ((MetaPreview) -> Unit)?, onLongClick: ((MetaPreview) -> Unit)?) {
    if (items.size < 2) return
    val colors = MaterialTheme.nuvio.colors
    var selected by rememberSaveable(items.map { it.stableKey() }) { mutableStateOf(0) }
    val index = selected.mod(items.size)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Surface(color = colors.surface, shape = CircleShape) {
            IconButton(onClick = { selected = (index + Random.nextInt(1, items.size)) % items.size }) {
                Icon(Icons.Rounded.Shuffle, stringResource(Res.string.home_discovery_another), tint = colors.accent)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(380.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.TopCenter) {
        for (depth in 2 downTo 1) {
            val item = items[(index + depth) % items.size]
            Box(
                Modifier.fillMaxWidth().height(352.dp).graphicsLayer {
                    rotationZ = if (depth == 2) -3f else 3f
                    scaleX = 1f - depth * 0.035f
                    translationY = (depth * 9).dp.toPx()
                    alpha = 0.8f
                }.clip(RoundedCornerShape(26.dp)).background(colors.surfaceElevated),
            ) {
                AsyncImage(item.banner ?: item.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        AnimatedContent(
            targetState = items[index],
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "home_discovery_shuffle",
        ) { item ->
            Column(
                Modifier.fillMaxWidth().height(352.dp).clip(RoundedCornerShape(26.dp)).background(colors.surface)
                    .posterCardClickable(onClick?.let { { it(item) } }, onLongClick?.let { { it(item) } },
                        zoomImageUrl = item.banner ?: item.poster, zoomCornerRadius = 26.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(238.dp).background(colors.surfaceElevated)) {
                    AsyncImage(item.banner ?: item.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, colors.surface.copy(alpha = 0.7f)))))
                }
                Row(Modifier.fillMaxWidth().weight(1f).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val caption = item.genres.take(2).joinToString(" · ").ifBlank { item.releaseInfo.orEmpty() }
                        if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun HorizonTimeline(items: List<MetaPreview>, onClick: ((MetaPreview) -> Unit)?, onLongClick: ((MetaPreview) -> Unit)?) {
    val colors = MaterialTheme.nuvio.colors
    Surface(shape = RoundedCornerShape(26.dp), color = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            items.forEachIndexed { index, item ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 102.dp)
                        .posterCardClickable(onClick?.let { { it(item) } }, onLongClick?.let { { it(item) } }, zoomImageUrl = item.poster),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.width(10.dp).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(8.dp).background(colors.accent, CircleShape))
                        if (index < items.lastIndex) Box(Modifier.width(1.dp).height(86.dp).background(colors.accent.copy(alpha = 0.22f)))
                    }
                    AsyncImage(item.poster ?: item.banner, null,
                        Modifier.size(width = 50.dp, height = 74.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceElevated), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val release = item.rawReleaseDate?.takeIf(String::isNotBlank) ?: item.releaseInfo?.takeIf(String::isNotBlank)
                        Text(release?.let(::formatReleaseDateForDisplay) ?: stringResource(Res.string.home_discovery_date_tba),
                            style = MaterialTheme.typography.labelMedium, color = colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.genres.isNotEmpty()) Text(item.genres.take(2).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
