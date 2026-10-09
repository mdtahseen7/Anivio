package com.nuvio.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Shared press micro-interaction: the element dips slightly while held and springs back on release.
 * Driven by the same [interactionSource] you hand to the element's `clickable`/`Button`, so it reads
 * real presses rather than guessing. Extracted from the idiom that was copy-pasted across several
 * screens; prefer this over re-rolling [collectIsPressedAsState] + [graphicsLayer] by hand.
 *
 * @param pressedScale scale while held (1f = none). ~0.96 for buttons, slightly subtler for posters.
 * @param haptic emit a light tick on press-down. Opt-in — leave off for list/grid items so scrolling
 *   taps don't buzz constantly; reserve it for deliberate CTAs.
 */
@Composable
fun Modifier.nuvioPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
    haptic: Boolean = false,
): Modifier {
    val motion = MaterialTheme.nuvio.motion
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = motion.fastMillis, easing = motion.standard),
        label = "nuvioPressScale",
    )
    if (haptic) {
        val hapticFeedback = LocalHapticFeedback.current
        LaunchedEffect(pressed) {
            if (pressed) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
