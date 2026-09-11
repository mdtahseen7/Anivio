package com.nuvio.app.features.watchprogress

import com.nuvio.app.features.tracking.WatchProgressSource

internal fun projectWatchProgressSourceEntries(
    source: WatchProgressSource,
    nuvioEntries: Collection<WatchProgressEntry>,
    providerEntries: Collection<WatchProgressEntry>,
): List<WatchProgressEntry> = when {
    source.providerId == null -> nuvioEntries.toList()
    source.mergesLocalProgress -> mergeLocalProgressIntoProviderEntries(
        nuvioEntries = nuvioEntries,
        providerEntries = providerEntries,
    )
    else -> providerEntries.toList()
}

/**
 * Overlays on-device playback positions onto a tracking provider's entries.
 *
 * The two sources know different things and neither is a superset of the other. AniList knows a
 * watched-episode count for every show on every device; it has no notion of being 12 minutes into an
 * episode, which is why selecting it as the watch-history source made every resume timeline vanish.
 * The device knows the position precisely, but only for what was played here.
 *
 * Resolved per show, one entry out per show — matching how provider-only mode behaves, so nothing
 * downstream sees a show twice:
 *
 *  - Provider only: pass it through.
 *  - Local only: pass it through. Something was played that the provider has not been told about, or
 *    is not on AniList at all.
 *  - Both, same episode: the provider entry carrying the local position. The common case, and the
 *    one the timelines were missing from.
 *  - Both, different episodes: whichever was updated more recently wins. Local newer means playback
 *    the provider has not caught up with; provider newer means the count advanced on another device
 *    and the local position belongs to an episode already behind.
 */
private fun mergeLocalProgressIntoProviderEntries(
    nuvioEntries: Collection<WatchProgressEntry>,
    providerEntries: Collection<WatchProgressEntry>,
): List<WatchProgressEntry> {
    if (providerEntries.isEmpty()) return nuvioEntries.toList()

    // Most recent local entry per show. A show can have several (one per episode played), and only
    // the latest describes where the user actually is.
    val latestLocalByContent = nuvioEntries
        .groupBy { it.parentMetaId }
        .mapValues { (_, entries) -> entries.maxBy { it.lastUpdatedEpochMs } }

    val merged = providerEntries.map { providerEntry ->
        val local = latestLocalByContent[providerEntry.parentMetaId] ?: return@map providerEntry
        when {
            local.isSameEpisodeAs(providerEntry) -> providerEntry.withLocalPlaybackPosition(local)
            local.lastUpdatedEpochMs > providerEntry.lastUpdatedEpochMs ->
                local.withProviderAttribution(providerEntry)
            else -> providerEntry
        }
    }

    val providerContentIds = providerEntries.mapTo(mutableSetOf()) { it.parentMetaId }
    val localOnly = nuvioEntries.filter { it.parentMetaId !in providerContentIds }

    return merged + localOnly
}

private fun WatchProgressEntry.isSameEpisodeAs(other: WatchProgressEntry): Boolean =
    seasonNumber == other.seasonNumber && episodeNumber == other.episodeNumber

/**
 * Takes the position, duration and stream attribution from a local entry.
 *
 * Guarded on the local entry actually having a measured position: a freshly created local row with
 * `durationMs == 0` would otherwise overwrite the provider's percentage with nothing and leave the
 * card looking unwatched. `progressPercent` is cleared because the provider's whole-show percentage
 * is not this episode's, and [WatchProgressEntry.progressFraction] prefers it when present.
 */
private fun WatchProgressEntry.withLocalPlaybackPosition(local: WatchProgressEntry): WatchProgressEntry {
    if (local.durationMs <= 0L || local.lastPositionMs <= 0L) return this
    return copy(
        lastPositionMs = local.lastPositionMs,
        durationMs = local.durationMs,
        progressPercent = null,
        isCompleted = isCompleted || local.isCompleted,
        lastUpdatedEpochMs = maxOf(lastUpdatedEpochMs, local.lastUpdatedEpochMs),
        // Resume needs the stream it was playing, not just the position.
        lastSourceUrl = local.lastSourceUrl ?: lastSourceUrl,
        lastStreamTitle = local.lastStreamTitle ?: lastStreamTitle,
        lastStreamSubtitle = local.lastStreamSubtitle ?: lastStreamSubtitle,
        providerName = local.providerName ?: providerName,
        providerAddonId = local.providerAddonId ?: providerAddonId,
        episodeThumbnail = episodeThumbnail ?: local.episodeThumbnail,
    )
}

/**
 * Keeps a local entry but borrows the provider's artwork and tracking ids.
 *
 * Without the tracking ids the card would lose its link back to the AniList record, so marking it
 * watched would no longer push anywhere.
 */
private fun WatchProgressEntry.withProviderAttribution(provider: WatchProgressEntry): WatchProgressEntry =
    copy(
        logo = logo ?: provider.logo,
        poster = poster ?: provider.poster,
        background = background ?: provider.background,
        episodeThumbnail = episodeThumbnail ?: provider.episodeThumbnail,
        trackingProviderId = provider.trackingProviderId ?: trackingProviderId,
        trackingProviderItemId = provider.trackingProviderItemId ?: trackingProviderItemId,
        trackingSourceUrl = provider.trackingSourceUrl ?: trackingSourceUrl,
    )
