package com.nuvio.app.features.player.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import co.touchlab.kermit.Logger
import java.awt.EventQueue
import java.awt.Window

/**
 * Compose can receive a nested repaint from Windows during a native resize. Its interop guard
 * throws before entering the nested frame, so retry on the event queue after the outer frame.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal class WindowsRenderExceptionHandlerFactory(
    private val fallback: WindowExceptionHandlerFactory,
) : WindowExceptionHandlerFactory {
    override fun exceptionHandler(window: Window): WindowExceptionHandler {
        val defaultHandler = fallback.exceptionHandler(window)
        var redrawQueued = false
        return WindowExceptionHandler { error ->
            val isNestedRedraw = error is IllegalStateException &&
                error.message == "Reentry into ignoringRedrawRequests is not allowed" &&
                error.stackTrace.firstOrNull()?.className == "androidx.compose.ui.viewinterop.SwingInteropContainer"

            if (!isNestedRedraw) {
                defaultHandler.onException(error)
            } else if (!redrawQueued) {
                redrawQueued = true
                log.d { "Deferring a nested Swing interop redraw" }
                EventQueue.invokeLater {
                    redrawQueued = false
                    if (window.isShowing) window.repaint()
                }
            }
        }
    }

    private companion object {
        val log = Logger.withTag("WindowsRedraw")
    }
}
