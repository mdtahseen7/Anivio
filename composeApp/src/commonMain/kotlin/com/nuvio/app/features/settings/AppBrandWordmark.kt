package com.nuvio.app.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.nuvio.app.core.ui.nuvio
import org.jetbrains.compose.resources.imageResource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_av_wordmark

/**
 * Shared AV + ANIVIO PNG lockup. Theme tint keeps the transparent artwork readable on both
 * light and dark surfaces; callers with a fixed-color surface can supply their own tint.
 *
 * The caller sets the height and the width follows from the artwork's aspect ratio — the source is
 * cropped to its ink, so the height the caller asks for is the height the logo actually occupies
 * with no invisible padding above or below.
 */
@Composable
internal fun AppBrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.nuvio.colors.textPrimary,
) {
    Image(
        painter = BitmapPainter(imageResource(Res.drawable.app_logo_av_wordmark)),
        colorFilter = ColorFilter.tint(tint),
        contentDescription = contentDescription,
        // Fit rather than FillBounds: a wordmark stretched off its aspect ratio looks broken, and the
        // parent in the settings footer constrains height only.
        contentScale = ContentScale.Fit,
        modifier = modifier.wrapContentWidth(),
    )
}
