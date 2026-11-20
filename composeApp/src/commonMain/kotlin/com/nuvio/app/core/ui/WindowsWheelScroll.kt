package com.nuvio.app.core.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier

internal expect fun Modifier.windowsSmoothWheelScroll(state: ScrollableState): Modifier
