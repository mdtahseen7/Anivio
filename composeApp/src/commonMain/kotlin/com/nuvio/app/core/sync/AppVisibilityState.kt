package com.nuvio.app.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide mirror of [AppForegroundMonitor]'s latest event, for code that needs to *ask* whether
 * the app is visible rather than react to a change.
 *
 * [AppForegroundMonitor.events] is a cold flow, so a repository cannot consult it at the moment it
 * decides whether to start work. Background batch fetches want exactly that: when the screen goes
 * off, Android's doze tears down the radio, and any batch already queued proceeds to fail every
 * request one at a time — dozens of pointless attempts and log noise for work nobody is waiting on.
 *
 * Defaults to [AppVisibility.Foreground] so nothing is suppressed before the first event arrives.
 */
internal object AppVisibilityState {
    private val _visibility = MutableStateFlow(AppVisibility.Foreground)
    val visibility: StateFlow<AppVisibility> = _visibility.asStateFlow()

    val isForeground: Boolean
        get() = _visibility.value == AppVisibility.Foreground

    fun set(visibility: AppVisibility) {
        _visibility.value = visibility
    }
}
