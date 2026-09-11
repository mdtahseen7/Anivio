package com.nuvio.app.features.tracking

import com.nuvio.app.features.library.LibrarySourceMode
import kotlinx.serialization.Serializable

@Serializable
enum class WatchProgressSource {
    /** On-device progress, optionally mirrored through the app's own cloud sync. */
    LOCAL,
    ANILIST,
    MAL,

    /**
     * AniList decides *which* shows are in progress; the device decides *where* in the episode you
     * are.
     *
     * AniList only stores a watched-episode count, so pure [ANILIST] can never draw a resume
     * timeline or resume mid-episode — the bars simply were not there. This keeps AniList as the
     * cross-device list of what you are watching and overlays the on-device position on top.
     */
    ANILIST_LOCAL;

    val providerId: TrackingProviderId?
        get() = when (this) {
            LOCAL -> null
            ANILIST, ANILIST_LOCAL -> TrackingProviderId.ANILIST
            MAL -> TrackingProviderId.MAL
        }

    /** True when on-device positions are layered over the provider's episode counts. */
    val mergesLocalProgress: Boolean
        get() = this == ANILIST_LOCAL

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
