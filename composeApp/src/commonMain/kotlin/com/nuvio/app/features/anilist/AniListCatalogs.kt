package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.ANILIST_MEDIA_FIELDS
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.home.HomeCatalogDefinition
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSource
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.anilist_row_movies
import nuvio.composeapp.generated.resources.anilist_row_popular
import nuvio.composeapp.generated.resources.anilist_row_recently_released
import nuvio.composeapp.generated.resources.anilist_row_trending
import nuvio.composeapp.generated.resources.anilist_row_upcoming
import nuvio.composeapp.generated.resources.anilist_source_name
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** Synthetic addon id for AniList rows, so keys keep the `<addon>:<type>:<catalogId>` shape. */
const val ANILIST_ADDON_ID = "anilist"

/**
 * Bumped whenever [ANILIST_CATALOGS] changes, so cached home sections keyed on
 * [HomeCatalogDefinition.cacheKey] are invalidated.
 */
private const val ANILIST_CATALOG_VERSION = "2"

/** Page size used for the home-row preview fetch. */
private const val DEFAULT_HOME_PER_PAGE = 25

/**
 * Recently-released is a per-episode feed, so the same show recurs across the page. Ask for more
 * rows than we need and let de-duplication trim it back.
 */
private const val AIRING_HOME_PER_PAGE = 50

/** AniList caps `perPage` at 50. */
const val ANILIST_MAX_PER_PAGE = 50

/**
 * The `media(...)` argument that hides adult titles, or nothing when the user has opted in.
 *
 * Opting in *omits* the filter rather than passing `isAdult: true`, which would return adult titles
 * exclusively instead of alongside everything else.
 */
internal fun aniListAdultFilter(includeAdult: Boolean): String =
    if (includeAdult) "" else ", isAdult: false"

internal data class AniListCatalog(
    val id: String,
    val titleResource: StringResource,
    val contentType: String,
    /** Whether this row seeds the hero carousel by default. */
    val isDefaultHeroSource: Boolean = false,
    /** `airingSchedules` rows carry one entry per episode rather than per title. */
    val isAiringFeed: Boolean = false,
    val homePerPage: Int = DEFAULT_HOME_PER_PAGE,
    /** The inner selection for a `Page`, minus `pageInfo`. */
    val selection: (nowSeconds: Long, includeAdult: Boolean) -> String,
)

internal val ANILIST_CATALOGS: List<AniListCatalog> = listOf(
    AniListCatalog(
        id = "recently-released",
        titleResource = Res.string.anilist_row_recently_released,
        contentType = "series",
        isAiringFeed = true,
        homePerPage = AIRING_HOME_PER_PAGE,
        selection = { nowSeconds, _ ->
            """
            airingSchedules(airingAt_lesser: $nowSeconds, sort: TIME_DESC) {
                episode
                airingAt
                media { $ANILIST_MEDIA_FIELDS }
            }
            """
        },
    ),
    AniListCatalog(
        id = "trending",
        titleResource = Res.string.anilist_row_trending,
        contentType = "series",
        isDefaultHeroSource = true,
        selection = { _, includeAdult ->
            "media(type: ANIME, sort: TRENDING_DESC${aniListAdultFilter(includeAdult)}) { $ANILIST_MEDIA_FIELDS }"
        },
    ),
    AniListCatalog(
        id = "popular",
        titleResource = Res.string.anilist_row_popular,
        contentType = "series",
        selection = { _, includeAdult ->
            "media(type: ANIME, sort: POPULARITY_DESC${aniListAdultFilter(includeAdult)}) { $ANILIST_MEDIA_FIELDS }"
        },
    ),
    AniListCatalog(
        id = "upcoming",
        titleResource = Res.string.anilist_row_upcoming,
        contentType = "series",
        selection = { _, includeAdult ->
            "media(type: ANIME, status: NOT_YET_RELEASED, sort: POPULARITY_DESC" +
                "${aniListAdultFilter(includeAdult)}) { $ANILIST_MEDIA_FIELDS }"
        },
    ),
    AniListCatalog(
        id = "movies",
        titleResource = Res.string.anilist_row_movies,
        contentType = "movie",
        selection = { _, includeAdult ->
            "media(type: ANIME, format: MOVIE, sort: POPULARITY_DESC" +
                "${aniListAdultFilter(includeAdult)}) { $ANILIST_MEDIA_FIELDS }"
        },
    ),
)

internal fun aniListCatalog(catalogId: String): AniListCatalog? =
    ANILIST_CATALOGS.firstOrNull { it.id == catalogId }

/**
 * Truncated to a 10-minute bucket so repeated refreshes reuse the same query string — and
 * therefore the same [com.nuvio.app.core.anilist.AniListClient] cache entry — instead of missing
 * the cache on every tick of the clock.
 */
internal fun aniListNowBucketSeconds(): Long {
    val nowSeconds = EpisodeReleaseDatePlatform.nowEpochMs() / 1_000L
    return (nowSeconds / 600L) * 600L
}

/**
 * Signature contribution for the AniList rows. Deliberately string-resource free: this is called
 * from composition via `buildHomeCatalogRefreshSignature`, so it must not block on resource loading.
 */
fun aniListCatalogRefreshSignature(): String =
    aniListCatalogDescriptorSignature() + ":" + ANILIST_CATALOGS.joinToString(",") { it.id }

/**
 * Carries the adult preference so cached home sections are invalidated when it is toggled — the row
 * keys would otherwise still resolve to pages fetched under the previous filter.
 */
internal fun aniListCatalogDescriptorSignature(): String {
    val includeAdult = HomeCatalogSettingsRepository.snapshot().adultContentEnabled
    return "anilist-v$ANILIST_CATALOG_VERSION:adult=$includeAdult"
}

/** Home rows backed by AniList. Ordered first so they lead the home screen on a fresh install. */
fun buildAniListCatalogDefinitions(): List<HomeCatalogDefinition> {
    val sourceName = runBlocking { getString(Res.string.anilist_source_name) }
    return ANILIST_CATALOGS.map { catalog ->
        val title = runBlocking { getString(catalog.titleResource) }
        HomeCatalogDefinition(
            key = "$ANILIST_ADDON_ID:${catalog.contentType}:${catalog.id}",
            // Both titles are the row name: "Trending - Series" would be noise in an anime-only
            // app, so the "show catalog type" preference intentionally has no effect here.
            defaultTitle = title,
            catalogName = title,
            addonName = sourceName,
            manifestUrl = "",
            type = catalog.contentType,
            catalogId = catalog.id,
            supportsPagination = true,
            descriptorSignature = aniListCatalogDescriptorSignature(),
            source = HomeCatalogSource.ANILIST,
            defaultHeroSourceEnabled = catalog.isDefaultHeroSource,
        )
    }
}
