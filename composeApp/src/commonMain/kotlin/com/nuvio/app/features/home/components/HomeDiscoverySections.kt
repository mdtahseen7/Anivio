package com.nuvio.app.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
    if (isDesktop) {
        DesktopHomeDiscoverySection(section, title, content, sectionPadding, onItemClick, onItemLongClick)
        return
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = sectionPadding, vertical = 12.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (section) {
                HomeDiscoverySection.Spotlight -> content.spotlight?.let { item ->
                    DiscoveryHeading(title)
                    HomeSpotlightCard(item, onItemClick, onItemLongClick)
                }
                HomeDiscoverySection.Shuffle -> HomeShuffleDeck(title, content.shuffle, onItemClick, onItemLongClick)
                HomeDiscoverySection.Horizon -> {
                    DiscoveryHeading(title)
                    HorizonTimeline(content.horizon.take(3), onItemClick, onItemLongClick)
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
