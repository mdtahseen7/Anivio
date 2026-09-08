package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.AniListMediaListEntry
import com.nuvio.app.core.anilist.displayTitle
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.anilist_episode_fallback_title
import org.jetbrains.compose.resources.getString

/** AniList list statuses that mean "part-way through". */
private val IN_PROGRESS = setOf(AniListListStatus.CURRENT, AniListListStatus.REPEATING)

/**
 * Projects the AniList "watching" list into home Continue Watching cards.
 *
 * AniList tracks *episodes completed*, not a position inside an episode, so every card here is a
 * next-up card: `progress + 1` is the episode to play, with no resume offset. Entries whose progress
 * has already reached the episode count are dropped — those are finished, just not marked completed.
 *
 * Items are additive: [excludeParentMetaIds] lets the caller drop anything the local watch-progress
 * engine is already showing, so a show tracked both ways appears once.
 */
fun aniListContinueWatchingItems(
    snapshot: AniListListsSnapshot,
    excludeParentMetaIds: Set<String> = emptySet(),
): List<ContinueWatchingItem> =
    snapshot.anime
        .asSequence()
        .filter { entry -> entry.status in IN_PROGRESS }
        .sortedByDescending { entry -> entry.updatedAt ?: 0L }
        .mapNotNull { entry -> entry.toContinueWatchingItem() }
        .filterNot { item -> item.parentMetaId in excludeParentMetaIds }
        .distinctBy(ContinueWatchingItem::parentMetaId)
        .toList()

private fun AniListMediaListEntry.toContinueWatchingItem(): ContinueWatchingItem? {
    val media = media ?: return null
    val title = media.displayTitle() ?: return null

    val watched = (progress ?: 0).coerceAtLeast(0)
    val nextEpisode = watched + 1
    // A finished-but-unmarked entry has nothing left to continue.
    media.episodes?.takeIf { it > 0 }?.let { total -> if (nextEpisode > total) return null }

    val parentMetaId = "$ANILIST_ID_PREFIX${media.id}"
    val poster = media.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
        ?: media.coverImage?.large?.takeIf { it.isNotBlank() }
    val banner = media.bannerImage?.takeIf { it.isNotBlank() }

    return ContinueWatchingItem(
        parentMetaId = parentMetaId,
        parentMetaType = "series",
        videoId = "$parentMetaId:$ANILIST_SEASON:$nextEpisode",
        title = title,
        subtitle = runBlocking { getString(Res.string.anilist_episode_fallback_title, nextEpisode) },
        // No per-episode still without a request per card; the series art reads fine on a next-up
        // card and the details page has the real episode thumbnails.
        imageUrl = banner ?: poster,
        poster = poster,
        background = banner,
        seasonNumber = ANILIST_SEASON,
        episodeNumber = nextEpisode,
        isNextUp = true,
        nextUpSeedSeasonNumber = ANILIST_SEASON,
        nextUpSeedEpisodeNumber = watched.takeIf { it > 0 },
        // AniList has no intra-episode position, so there is nothing to resume into.
        resumePositionMs = 0L,
        durationMs = 0L,
        progressFraction = 0f,
    )
}
