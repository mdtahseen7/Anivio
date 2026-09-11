package com.nuvio.app.features.anilist

/**
 * Disk slots for everything AniList-derived that is worth keeping between launches: the user's lists
 * ([AniListListCache]), the Continue Watching episode stills ([AniListEpisodeThumbnailCache]) and the
 * Discover genres and pages ([AniListDiscoverCache]).
 *
 * Keyed rather than one object per cache, the shape `ContinueWatchingEnrichmentStorage` uses. Every
 * key is profile-scoped by the platform implementation.
 */
internal expect object AniListCacheStorage {
    fun loadPayload(key: String): String?
    fun savePayload(key: String, payload: String)
    fun removePayload(key: String)
}

internal const val ANILIST_LIST_CACHE_KEY = "anilist_list_cache_payload"
internal const val ANILIST_EPISODE_THUMBNAIL_CACHE_KEY = "anilist_episode_thumbnail_cache_payload"
internal const val ANILIST_DISCOVER_FILTERS_CACHE_KEY = "anilist_discover_filters_cache_payload"
internal const val ANILIST_DISCOVER_PAGES_CACHE_KEY = "anilist_discover_pages_cache_payload"
internal const val ANILIST_ANIZIP_CACHE_KEY = "anilist_anizip_mappings_cache_payload"
internal const val ANILIST_TVDB_ARTWORK_CACHE_KEY = "anilist_tvdb_artwork_cache_payload"
internal const val ANILIST_MEDIA_DETAIL_CACHE_KEY = "anilist_media_detail_cache_payload"
internal const val ANILIST_TVDB_EPISODES_CACHE_KEY = "anilist_tvdb_episodes_cache_payload"
internal const val ANILIST_KITSU_EPISODES_CACHE_KEY = "anilist_kitsu_episodes_cache_payload"
internal const val ANILIST_TVDB_TOKEN_CACHE_KEY = "anilist_tvdb_token_cache_payload"
