package com.nuvio.app.features.mal

import com.nuvio.app.features.anime.PublicAnimeRouter
import com.nuvio.app.features.tracking.TrackingProviderRegistry

/** Installs Android-only MAL ports after platform credential/cache storage has been initialized. */
object MalTrackingBootstrap {
    fun install() {
        MalAuthSettings.install(MalAuthRepository)
        MalAuthRepository.descriptor
        TrackingProviderRegistry.registerLibraryProvider(MalTrackingLibraryProvider)
        TrackingProviderRegistry.registerWatchedProvider(MalWatchedSyncAdapter)
        TrackingProviderRegistry.registerProgressProvider(MalTrackingProgressProvider)
        TrackingProviderRegistry.registerListWriter(MalListWriter)
        TrackingProviderRegistry.registerHistoryWriter(MalHistoryWriter)
        TrackingProviderRegistry.registerScrobbler(MalScrobbler)

        // Public catalog/metadata data needs only a client id, so it is independent of whether the
        // user has connected an account. Without a client id every MAL request would 401, which the
        // router would read as the fallback itself being broken, so stay unregistered in that case.
        if (MalConfig.isConfigured) {
            PublicAnimeRouter.installFallback(MalPublicAnimeProvider)
        }
    }
}
