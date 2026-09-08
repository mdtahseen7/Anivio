package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvio

/**
 * The Anivio wordmark. Drawn as text rather than bundled artwork: the inherited raster wordmarks
 * had "Nuvio" baked into the pixels, so every colourway would have needed redrawing.
 */
private const val AppWordmarkText = "Anivio"

/** Cap-height of the lettering relative to the height the caller asked for. */
private const val AppWordmarkFontHeightRatio = 0.68f

/** Guards against an unbounded parent handing us an effectively infinite height. */
private const val AppWordmarkMaxFontHeightDp = 96f

@Composable
internal fun AppBrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val tokens = MaterialTheme.nuvio
    val description = contentDescription
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val availableHeight = maxHeight.value.coerceAtMost(AppWordmarkMaxFontHeightDp)
        Text(
            text = AppWordmarkText,
            style = TextStyle(
                fontSize = (availableHeight * AppWordmarkFontHeightRatio).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
            color = tokens.colors.accent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = if (description != null) {
                Modifier.semantics { this.contentDescription = description }
            } else {
                Modifier
            },
        )
    }
}
