package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Circular translucent button, matching the floating navigation pill's treatment: the same 24dp haze
 * blur over the same charcoal fill, so overlay controls read as one family.
 *
 * Pass the same [HazeState] the content is a `hazeSource` for; without one it falls back to a more
 * opaque fill, exactly as [NuvioNavigationBar] does.
 */
@Composable
fun NuvioCircularGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    size: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) { blurRadius = 24.dp }
                } else {
                    Modifier
                },
            )
            .background(Color(0xFF1C1C1E).copy(alpha = if (hazeState != null) 0.55f else 0.82f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
