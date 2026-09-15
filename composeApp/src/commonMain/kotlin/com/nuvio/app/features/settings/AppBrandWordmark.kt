package com.nuvio.app.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_wordmark

/**
 * The Anivio wordmark, drawn from the supplied artwork.
 *
 * Previously this was rendered as the literal text "Anivio" in the accent colour, because the
 * inherited rasters had "Nuvio" baked into the pixels and there was no Anivio artwork to use. There
 * is now, so this draws the real mark.
 *
 * The caller sets the height and the width follows from the artwork's aspect ratio — the source is
 * cropped to its ink, so the height the caller asks for is the height the lettering actually occupies
 * with no invisible padding above or below.
 */
@Composable
internal fun AppBrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(Res.drawable.app_logo_wordmark),
        contentDescription = contentDescription,
        // Fit rather than FillBounds: a wordmark stretched off its aspect ratio looks broken, and the
        // parent in the settings footer constrains height only.
        contentScale = ContentScale.Fit,
        modifier = modifier.wrapContentWidth(),
    )
}
