package com.nuvio.app.core.tracking

import com.nuvio.app.features.anilist.AniListAuthRepository
import com.nuvio.app.features.anilist.AniListHistoryWriter
import com.nuvio.app.features.anilist.AniListListWriter
import com.nuvio.app.features.anilist.AniListScrobbler
import com.nuvio.app.features.anilist.AniListTrackingLibraryProvider
import com.nuvio.app.features.anilist.AniListTrackingProgressProvider
import com.nuvio.app.features.anilist.AniListWatchedSyncAdapter
import com.nuvio.app.features.tracking.TrackingProviderRegistry

fun ensureTrackingProvidersRegistered() {
    AniListAuthRepository.descriptor
    TrackingProviderRegistry.registerLibraryProvider(AniListTrackingLibraryProvider)
    TrackingProviderRegistry.registerWatchedProvider(AniListWatchedSyncAdapter)
    TrackingProviderRegistry.registerProgressProvider(AniListTrackingProgressProvider)
    TrackingProviderRegistry.registerListWriter(AniListListWriter)
    TrackingProviderRegistry.registerHistoryWriter(AniListHistoryWriter)
    TrackingProviderRegistry.registerScrobbler(AniListScrobbler)
}
