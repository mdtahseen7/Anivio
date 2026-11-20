package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.nuvio.app.features.settings.AppBrandWordmark
import java.awt.EventQueue
import java.awt.event.MouseEvent

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FrameWindowScope.AnivioWindowTitleBar(
    windowState: WindowState,
    onClose: () -> Unit,
) {
    val maximized = windowState.placement == WindowPlacement.Maximized
    val toggleMaximize = {
        windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
            WindowPlacement.Floating
        } else {
            WindowPlacement.Maximized
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF20232C), Color(0xFF101116))))
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowDraggableArea(
            modifier = Modifier.weight(1f).fillMaxHeight().onPointerEvent(PointerEventType.Release) { event ->
                val mouse = event.nativeEvent as? MouseEvent
                if (mouse?.button == MouseEvent.BUTTON1 && mouse.clickCount == 2) {
                    // Finish the drag gesture before changing the frame's maximized bounds.
                    EventQueue.invokeLater { toggleMaximize() }
                }
            },
        ) {
            Box(Modifier.fillMaxHeight().padding(start = 15.dp), contentAlignment = Alignment.CenterStart) {
                AppBrandWordmark(Modifier.height(15.dp), contentDescription = "Anivio", tint = Color(0xFFF0F2F7))
            }
        }

        Row(
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1E26)).padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            WindowChromeButton(WindowChromeAction.Minimize) { windowState.isMinimized = true }
            WindowChromeButton(if (maximized) WindowChromeAction.Restore else WindowChromeAction.Maximize, toggleMaximize)
            WindowChromeButton(WindowChromeAction.Close, onClose)
        }
    }
}

private enum class WindowChromeAction(val label: String) {
    Minimize("Minimize"),
    Maximize("Maximize"),
    Restore("Restore window"),
    Close("Close Anivio"),
}

@Composable
private fun WindowChromeButton(action: WindowChromeAction, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val active = hovered || focused
    val background by animateColorAsState(
        targetValue = when {
            !active -> Color.Transparent
            action == WindowChromeAction.Close -> Color(0xFFC74655)
            else -> Color(0xFF383E51)
        },
        animationSpec = tween(130),
    )
    val ink = if (active) Color.White else Color(0xFFD4D8E5)

    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 29.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .semantics { contentDescription = action.label }
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
            val stroke = 1.3.dp.toPx()
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
                drawLine(ink, point(x1, y1), point(x2, y2), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            when (action) {
                WindowChromeAction.Minimize -> line(0.19f, 0.52f, 0.81f, 0.52f)
                WindowChromeAction.Maximize -> drawRoundRect(
                    color = ink,
                    topLeft = point(0.19f, 0.19f),
                    size = Size(size.width * 0.62f, size.height * 0.62f),
                    cornerRadius = CornerRadius(1.4.dp.toPx()),
                    style = Stroke(stroke),
                )
                WindowChromeAction.Restore -> {
                    line(0.42f, 0.18f, 0.81f, 0.18f)
                    line(0.81f, 0.18f, 0.81f, 0.59f)
                    drawRoundRect(
                        color = ink,
                        topLeft = point(0.20f, 0.38f),
                        size = Size(size.width * 0.42f, size.height * 0.42f),
                        cornerRadius = CornerRadius(1.1.dp.toPx()),
                        style = Stroke(stroke),
                    )
                }
                WindowChromeAction.Close -> {
                    line(0.22f, 0.22f, 0.78f, 0.78f)
                    line(0.78f, 0.22f, 0.22f, 0.78f)
                }
            }
        }
    }
}
