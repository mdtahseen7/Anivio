package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.features.anilist.AniListHeroArtwork
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.home.withResolvedDiscoveryArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resolve only the displayed pick, sharing the hero cache and its miss/retry window. */
@Composable
internal fun rememberDiscoveryArtwork(item: MetaPreview): MetaPreview {
    val key = item.stableKey()
    var artwork by remember(key) { mutableStateOf(AniListHeroArtwork.snapshot()[key]) }
    LaunchedEffect(key, item.poster, item.banner, item.logo) {
        artwork = withContext(Dispatchers.Default) {
            if (AniListHeroArtwork.hasUnresolved(listOf(item))) {
                AniListHeroArtwork.resolve(listOf(item.withResolvedDiscoveryArtwork(AniListHeroArtwork.snapshot()[key])))
            }
            AniListHeroArtwork.snapshot()[key]
        }
    }
    return item.withResolvedDiscoveryArtwork(artwork)
}

/** Logos retain their native proportions; loading errors (including unsupported SVG) use text. */
@Composable
internal fun DiscoveryTitleLogo(item: MetaPreview, modifier: Modifier = Modifier, maxHeight: Dp = 96.dp) {
    val logo = item.logo?.takeIf(String::isNotBlank)
    var failed by remember(item.stableKey(), logo) { mutableStateOf(false) }
    var loaded by remember(item.stableKey(), logo) { mutableStateOf(false) }
    var aspect by remember(item.stableKey(), logo) { mutableFloatStateOf(2.6f) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (logo == null || failed) {
            Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis)
        } else {
            val height = (maxWidth / aspect).coerceIn(42.dp, maxHeight)
            Box(contentAlignment = Alignment.CenterStart) {
                AsyncImage(
                    model = logo,
                    contentDescription = if (loaded) item.name else null,
                    modifier = Modifier.fillMaxWidth().height(height),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onSuccess = { state ->
                        val image = state.result.image
                        if (image.width > 0 && image.height > 0) aspect = (image.width.toFloat() / image.height).coerceIn(0.75f, 10f)
                        loaded = true
                    },
                    onError = { failed = true },
                )
                if (!loaded) Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
