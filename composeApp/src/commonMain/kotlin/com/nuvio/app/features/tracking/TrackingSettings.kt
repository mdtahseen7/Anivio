package com.nuvio.app.features.tracking

import com.nuvio.app.features.library.LibrarySourceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sentinel for "no age limit" on Continue Watching. */
const val CONTINUE_WATCHING_DAYS_CAP_ALL = 0

/** Selectable cut-offs, newest first, with "all" last. */
val CONTINUE_WATCHING_DAYS_CAP_OPTIONS = listOf(7, 14, 30, 90, 180, 365, CONTINUE_WATCHING_DAYS_CAP_ALL)

/** Snaps a stored or synced value onto a supported option, defaulting to no limit. */
fun normalizeContinueWatchingDaysCap(days: Int): Int =
    if (days in CONTINUE_WATCHING_DAYS_CAP_OPTIONS) days else CONTINUE_WATCHING_DAYS_CAP_ALL

data class TrackingSettingsUiState(
    val librarySourceMode: LibrarySourceMode = DEFAULT_LIBRARY_SOURCE_MODE,
    val watchProgressSource: WatchProgressSource = DEFAULT_WATCH_PROGRESS_SOURCE,
    val continueWatchingDaysCap: Int = CONTINUE_WATCHING_DAYS_CAP_ALL,
)

/**
 * Provider-neutral store for tracking source preferences: which account backs the library, which
 * backs watch progress, and how far back Continue Watching reaches.
 *
 * Selecting a provider here does not check that it is connected — [effectiveLibrarySourceMode] and
 * [effectiveWatchProgressSource] resolve that at read time, so a disconnected account degrades to
 * local rather than stranding the user on an empty screen.
 */
object TrackingSettingsRepository {
    private val _uiState = MutableStateFlow(TrackingSettingsUiState())
    val uiState: StateFlow<TrackingSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        reload()
    }

    fun onProfileChanged() {
        // Preferences are profile scoped, so re-read rather than carry the previous profile's choice.
        hasLoaded = true
        reload()
    }

    fun clearLocalState() {
        hasLoaded = false
        _uiState.value = TrackingSettingsUiState()
    }

    fun setLibrarySourceMode(source: LibrarySourceMode) {
        ensureLoaded()
        TrackingSettingsStorage.saveLibrarySourceMode(source.name)
        _uiState.value = _uiState.value.copy(librarySourceMode = source)
    }

    fun setWatchProgressSource(source: WatchProgressSource, profileId: Int) {
        ensureLoaded()
        TrackingSettingsStorage.saveWatchProgressSource(source.name)
        _uiState.value = _uiState.value.copy(watchProgressSource = source)
    }

    fun setContinueWatchingDaysCap(days: Int) {
        ensureLoaded()
        val normalized = normalizeContinueWatchingDaysCap(days)
        TrackingSettingsStorage.saveContinueWatchingDaysCap(normalized)
        _uiState.value = _uiState.value.copy(continueWatchingDaysCap = normalized)
    }

    private fun reload() {
        _uiState.value = TrackingSettingsUiState(
            librarySourceMode = librarySourceModeFromStorage(
                TrackingSettingsStorage.loadLibrarySourceMode(),
            ),
            watchProgressSource = WatchProgressSource.fromStorage(
                TrackingSettingsStorage.loadWatchProgressSource(),
            ),
            continueWatchingDaysCap = normalizeContinueWatchingDaysCap(
                TrackingSettingsStorage.loadContinueWatchingDaysCap() ?: CONTINUE_WATCHING_DAYS_CAP_ALL,
            ),
        )
    }
}
