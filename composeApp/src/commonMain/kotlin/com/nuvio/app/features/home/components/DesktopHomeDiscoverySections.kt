package com.nuvio.app.features.home.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.anilist.stripAniListMarkup
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioDesktopDragScroll
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.features.home.HomeDiscoveryContent
import com.nuvio.app.features.home.HomeDiscoverySection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DesktopHomeDiscoverySection(
    section: HomeDiscoverySection,
    title: String,
    content: HomeDiscoveryContent,
    autoAdvance: Boolean,
    sectionPadding: Dp,
    onOpen: ((MetaPreview) -> Unit)?,
    onLongClick: ((MetaPreview) -> Unit)?,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = sectionPadding, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (section) {
            HomeDiscoverySection.Spotlight -> DesktopDiscoveryPicks(
                title = title,
                candidates = listOfNotNull(content.spotlight) + content.shuffle.take(3),
                shuffle = false,
                autoAdvance = autoAdvance,
                onOpen = onOpen,
                onLongClick = onLongClick,
            )
            HomeDiscoverySection.Shuffle -> DesktopDiscoveryPicks(
                title = title,
                candidates = content.shuffle,
                shuffle = true,
                autoAdvance = autoAdvance,
                onOpen = onOpen,
                onLongClick = onLongClick,
            )
            HomeDiscoverySection.Horizon -> DesktopHorizon(title, content.horizon, onOpen, onLongClick)
        }
    }
}

@Composable
private fun DesktopDiscoveryPicks(
    title: String,
    candidates: List<MetaPreview>,
    shuffle: Boolean,
    autoAdvance: Boolean,
    onOpen: ((MetaPreview) -> Unit)?,
    onLongClick: ((MetaPreview) -> Unit)?,
) {
    val firstKey = candidates.firstOrNull()?.stableKey()
    var shuffledKey by remember(firstKey) { mutableStateOf(if (shuffle) candidates.randomOrNull()?.stableKey() else null) }
    var selectedKey by remember(firstKey) { mutableStateOf(shuffledKey ?: firstKey) }
    val picks = remember(candidates, shuffledKey) {
        val shuffled = candidates.firstOrNull { it.stableKey() == shuffledKey }
        (listOfNotNull(shuffled) + candidates.filterNot { it.stableKey() == shuffledKey }).take(4)
    }
    val selected = picks.firstOrNull { it.stableKey() == selectedKey } ?: picks.firstOrNull() ?: return
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }

    ScreenActivityEffect(picks, selectedKey, autoAdvance, hovered, focused) { active ->
        if (!active || !autoAdvance || hovered || focused || picks.size < 2) return@ScreenActivityEffect
        delay(8_000)
        selectedKey = picks[(picks.indexOf(selected) + 1) % picks.size].stableKey()
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        if (shuffle) {
            OutlinedButton(onClick = {
                shuffledKey = candidates.filterNot { it.stableKey() == selected.stableKey() }.randomOrNull()?.stableKey()
                selectedKey = shuffledKey
            }) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(Res.string.home_discovery_shuffle_action))
            }
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .hoverable(interaction)
            .onFocusChanged { focused = it.hasFocus }
            .focusGroup(),
    ) {
        if (maxWidth >= 860.dp && picks.size > 1) {
            val featureHeight = (maxWidth * 0.35f).coerceIn(370.dp, 490.dp)
            val picksWidth = (maxWidth * 0.28f).coerceIn(255.dp, 350.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Crossfade(
                    targetState = selected,
                    modifier = Modifier.weight(1f).height(featureHeight),
                    animationSpec = tween(340),
                ) { item ->
                    DesktopDiscoveryFeature(item, onOpen, onLongClick, Modifier.fillMaxSize())
                }
                Column(Modifier.width(picksWidth).height(featureHeight), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    picks.forEach { pick ->
                        val isSelected = pick.stableKey() == selected.stableKey()
                        val colors = MaterialTheme.nuvio.colors
                        Surface(
                            modifier = Modifier.weight(1f),
                            onClick = { selectedKey = pick.stableKey() },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) colors.surfaceElevated else colors.surface,
                            border = if (isSelected) BorderStroke(1.dp, colors.accent.copy(alpha = 0.65f)) else null,
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                NuvioAsyncImage(
                                    pick.poster ?: pick.banner, null,
                                    Modifier.size(width = 46.dp, height = 65.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(pick.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val metadata = listOfNotNull(pick.releaseInfo) + pick.genres.take(2)
                                    if (metadata.isNotEmpty()) {
                                        Text(metadata.joinToString(" · "), color = colors.textMuted,
                                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Crossfade(
                    targetState = selected,
                    modifier = Modifier.fillMaxWidth().height(330.dp),
                    animationSpec = tween(340),
                ) { item ->
                    DesktopDiscoveryFeature(item, onOpen, onLongClick, Modifier.fillMaxSize())
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(picks, key = { it.stableKey() }) { pick ->
                        FilterChip(
                            selected = pick.stableKey() == selected.stableKey(),
                            onClick = { selectedKey = pick.stableKey() },
                            label = { Text(pick.name, Modifier.widthIn(max = 160.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopDiscoveryFeature(
    item: MetaPreview,
    onOpen: ((MetaPreview) -> Unit)?,
    onLongClick: ((MetaPreview) -> Unit)?,
    modifier: Modifier,
) {
    val artwork = rememberDiscoveryArtwork(item)
    val banner = artwork.banner?.takeIf(String::isNotBlank)
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.clip(shape).background(Color(0xFF11151D))
            .posterCardClickable(onOpen?.let { { it(artwork) } }, onLongClick?.let { { it(artwork) } },
                zoomImageUrl = banner ?: artwork.poster, zoomCornerRadius = 20.dp, hoverScaleEnabled = false),
        contentAlignment = Alignment.BottomStart,
    ) {
        NuvioAsyncImage(
            banner ?: artwork.poster,
            contentDescription = null,
            modifier = if (banner == null) {
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.52f)
            } else {
                Modifier.matchParentSize()
            },
            contentScale = if (banner == null) ContentScale.Fit else ContentScale.Crop,
        )
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.2f), Color.Transparent),
        )))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0.27f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.92f),
        )))
        Column(
            Modifier.widthIn(max = 550.dp).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            DiscoveryTitleLogo(artwork, Modifier.widthIn(max = 320.dp), maxHeight = 100.dp)
            val metadata = listOfNotNull(item.releaseInfo) + item.genres.take(3)
            if (metadata.isNotEmpty()) {
                Text(metadata.joinToString(" · "), color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val synopsis = remember(item.description) { item.description?.stripAniListMarkup()?.trim().orEmpty() }
            if (synopsis.isNotBlank()) {
                Text(synopsis, color = Color.White.copy(alpha = 0.83f), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (onOpen != null) {
                Button(onClick = { onOpen(artwork) }, colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White, contentColor = Color(0xFF15171C),
                )) {
                    Text(stringResource(Res.string.home_discovery_details))
                }
            }
        }
    }
}

@Composable
private fun DesktopHorizon(
    title: String,
    titles: List<MetaPreview>,
    onOpen: ((MetaPreview) -> Unit)?,
    onLongClick: ((MetaPreview) -> Unit)?,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        FilledTonalIconButton(
            enabled = listState.canScrollBackward,
            onClick = { scope.launch {
                val step = if (listState.firstVisibleItemScrollOffset == 0) 1 else 0
                listState.animateScrollToItem((listState.firstVisibleItemIndex - step).coerceAtLeast(0))
            } },
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(Res.string.home_discovery_previous_release))
        }
        FilledTonalIconButton(
            enabled = listState.canScrollForward,
            onClick = { scope.launch {
                listState.animateScrollToItem((listState.firstVisibleItemIndex + 1).coerceAtMost(titles.lastIndex))
            } },
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(Res.string.home_discovery_next_release))
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth * 0.37f).coerceIn(minOf(maxWidth, 325.dp), minOf(maxWidth, 440.dp))
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().nuvioDesktopDragScroll(listState),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(titles, key = { it.stableKey() }) { item ->
                val colors = MaterialTheme.nuvio.colors
                val shape = RoundedCornerShape(16.dp)
                Surface(color = colors.surface, shape = shape, modifier = Modifier.width(cardWidth).clip(shape)
                    .posterCardClickable(onOpen?.let { { it(item) } }, onLongClick?.let { { it(item) } }, zoomImageUrl = item.poster)) {
                    Row(Modifier.height(172.dp).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NuvioAsyncImage(
                            item.poster ?: item.banner, null,
                            Modifier.size(width = 100.dp, height = 148.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceElevated),
                            contentScale = ContentScale.Crop,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val release = item.rawReleaseDate?.takeIf(String::isNotBlank)
                                ?: item.releaseInfo?.takeIf(String::isNotBlank)
                            Text(release?.let(::formatReleaseDateForDisplay) ?: stringResource(Res.string.home_discovery_date_tba),
                                color = colors.accent, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val synopsis = remember(item.description) { item.description?.stripAniListMarkup()?.trim().orEmpty() }
                            if (synopsis.isNotBlank()) {
                                Text(synopsis, color = colors.textMuted, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (item.genres.isNotEmpty()) {
                                Text(item.genres.take(3).joinToString(" · "), color = colors.textMuted,
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
