package com.nuvio.app.core.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nuvio.app.isWindows
import java.awt.event.MouseWheelEvent
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.windowsSmoothWheelScroll(state: ScrollableState): Modifier {
    if (!isWindows) return this
    return pointerInput(state) {
        val pixelsPerLine = 24.dp.toPx()
        val wheelDeltas = Channel<Float>(capacity = 16)
        coroutineScope {
            launch {
                for (firstDelta in wheelDeltas) {
                    // Separate mutations so a drag or scrollbar can interrupt a wheel animation.
                    launch {
                        state.scroll {
                            var remaining = firstDelta
                            var lastFrame = withFrameNanos { it }
                            while (abs(remaining) >= 0.5f) {
                                val frame = withFrameNanos { it }
                                val elapsedMs = ((frame - lastFrame) / 1_000_000.0).coerceIn(1.0, 40.0)
                                lastFrame = frame
                                while (true) {
                                    val next = wheelDeltas.tryReceive().getOrNull() ?: break
                                    remaining += next
                                }
                                val limit = pixelsPerLine * 18
                                remaining = remaining.coerceIn(-limit, limit)
                                val step = if (abs(remaining) <= 2f) {
                                    remaining
                                } else {
                                    remaining * (1 - exp(-elapsedMs / 42.0)).toFloat()
                                }
                                val consumed = scrollBy(step)
                                if (abs(consumed - step) > 1f) break
                                remaining -= step
                            }
                        }
                    }.join()
                }
            }

            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Scroll || event.changes.any { it.isConsumed }) continue
                    val wheel = event.nativeEvent as? MouseWheelEvent ?: continue
                    if (wheel.isShiftDown || wheel.isControlDown || wheel.isAltDown || wheel.isMetaDown) continue
                    if (wheel.scrollType != MouseWheelEvent.WHEEL_UNIT_SCROLL) continue

                    // Preserve precise trackpad deltas and horizontal gestures.
                    val notches = wheel.wheelRotation
                    if (notches == 0 || wheel.preciseWheelRotation != notches.toDouble()) continue
                    if (event.changes.any { it.scrollDelta.x != 0f }) continue
                    if (notches > 0 && !state.canScrollForward || notches < 0 && !state.canScrollBackward) continue
                    if (wheelDeltas.trySend(wheel.unitsToScroll * pixelsPerLine).isSuccess) {
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
}
