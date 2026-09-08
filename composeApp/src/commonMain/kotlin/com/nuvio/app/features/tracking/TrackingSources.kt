package com.nuvio.app.features.tracking

import com.nuvio.app.features.library.LibrarySourceMode
import kotlinx.serialization.Serializable

@Serializable
enum class WatchProgressSource {
    /** On-device progress, optionally mirrored through the app's own cloud sync. */
    LOCAL,
    ANILIST,
    MAL;

    val providerId: TrackingProviderId?
        get() = when (this) {
            LOCAL -> null
            ANILIST -> TrackingProviderId.ANILIST
            MAL -> TrackingProviderId.MAL
        }

    companion object {
        fun fromStorage(value: String?): WatchProgressSource =
            entries.firstOrNull { it.name == value } ?: DEFAULT_WATCH_PROGRESS_SOURCE
    }
}

/** Local progress is the default: it works before any account is connected. */
val DEFAULT_WATCH_PROGRESS_SOURCE: WatchProgressSource = WatchProgressSource.LOCAL

val DEFAULT_LIBRARY_SOURCE_MODE: LibrarySourceMode = LibrarySourceMode.LOCAL

fun librarySourceModeFromStorage(value: String?): LibrarySourceMode =
    LibrarySourceMode.entries.firstOrNull { it.name == value } ?: DEFAULT_LIBRARY_SOURCE_MODE

val LibrarySourceMode.providerId: TrackingProviderId?
    get() = when (this) {
        LibrarySourceMode.LOCAL -> null
        LibrarySourceMode.ANILIST -> TrackingProviderId.ANILIST
        LibrarySourceMode.MAL -> TrackingProviderId.MAL
    }

fun effectiveWatchProgressSource(
    requestedSource: WatchProgressSource,
    isProviderAuthenticated: (TrackingProviderId) -> Boolean,
): WatchProgressSource {
    val providerId = requestedSource.providerId ?: return WatchProgressSource.LOCAL
    return requestedSource.takeIf { isProviderAuthenticated(providerId) }
        ?: WatchProgressSource.LOCAL
}

fun effectiveLibrarySourceMode(
    requestedSource: LibrarySourceMode,
    isProviderAuthenticated: (TrackingProviderId) -> Boolean,
): LibrarySourceMode {
    val providerId = requestedSource.providerId ?: return LibrarySourceMode.LOCAL
    return requestedSource.takeIf { isProviderAuthenticated(providerId) }
        ?: LibrarySourceMode.LOCAL
}
