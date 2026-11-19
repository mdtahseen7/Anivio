package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.AniListClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import co.touchlab.kermit.Logger

/**
 * One row in the signed-in user's AniList notification feed.
 *
 * AniList's notification union carries no per-item unread flag (verified against the live schema),
 * so "new" is derived locally from [createdAtEpochSec] versus the time the feed was last cleared.
 */
data class AniListNotification(
    val id: Long,
    val type: String,
    val createdAtEpochSec: Long? = null,
    val context: String? = null,
    val mediaId: Int? = null,
    val mediaTitle: String? = null,
    val mediaCover: String? = null,
    val mediaBanner: String? = null,
    val episode: Int? = null,
    val userId: Int? = null,
    val userName: String? = null,
    val userAvatar: String? = null,
    /** Server-provided free text (message body, deletion title, merge reason) when present. */
    val message: String? = null,
)

data class AniListNotificationsUiState(
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<AniListNotification> = emptyList(),
    /** Authoritative unread badge from `Viewer.unreadNotificationCount`. */
    val unreadCount: Int = 0,
    /** Epoch seconds of the newest notification the user has seen in-app. */
    val lastClearedAtEpochSec: Long? = null,
    val error: String? = null,
    /** True once `hasNextPage` came back false — no more pages to load. */
    val hasReachedEnd: Boolean = false,
) {
    fun isNew(item: AniListNotification): Boolean {
        val cleared = lastClearedAtEpochSec ?: return item.createdAtEpochSec != null
        return (item.createdAtEpochSec ?: 0L) > cleared
    }
}

/**
 * The signed-in AniList account's notification feed (`Page.notifications` union + `Viewer`).
 *
 * Every query carries the stored access token, so the shared client's cache keys are already
 * viewer-scoped. The badge count is the server's `Viewer.unreadNotificationCount`; clearing it
 * uses the documented `Viewer(resetNotificationCount: true)` read (a query, not a mutation).
 */
object AniListNotificationsRepository {
    private const val PAGE_SIZE = 25
    private const val MAX_ITEMS = 200

    private val log = Logger.withTag("AniListNotifications")
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(AniListNotificationsUiState())
    val uiState: StateFlow<AniListNotificationsUiState> = _uiState.asStateFlow()

    private val loadMutex = Mutex()
    private var page = 1

    /**
     * Opens the feed: resets to page 1, refetches, and refreshes the badge. Cheap enough to run
     * on every screen open; cached within [AniListClient]'s normal TTL otherwise.
     */
    suspend fun refresh() {
        val token = AniListAuthRepository.accessTokenOrNull()
        if (token == null) {
            _uiState.value = AniListNotificationsUiState()
            return
        }
        page = 1
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        loadPage(token, targetPage = 1)
    }

    /** Loads the next page for infinite scroll; no-op at the end, while loading, or signed out. */
    suspend fun loadMore() {
        val token = AniListAuthRepository.accessTokenOrNull() ?: return
        val state = _uiState.value
        if (state.hasReachedEnd || state.isLoading || state.isLoadingMore || state.items.isEmpty()) return
        _uiState.value = state.copy(isLoadingMore = true)
        loadPage(token, targetPage = page + 1)
    }

    /**
     * Marks the whole feed read: resets the server counter via the documented Viewer argument and
     * records "now" so nothing currently in the list renders as new afterwards.
     */
    suspend fun markAllRead(nowEpochSec: Long) {
        val token = AniListAuthRepository.accessTokenOrNull() ?: return
        try {
            AniListClient.query(
                query = "query { Viewer { id unreadNotificationCount(resetNotificationCount: true) } }",
                forceRefresh = true,
                accessToken = token,
            )
            _uiState.value = _uiState.value.copy(unreadCount = 0, lastClearedAtEpochSec = nowEpochSec)
            // The feed query (PAGE_QUERY) also carries the unread count and is cached for the normal
            // TTL, so without a forced refetch the badge would reappear from that stale cache on the
            // next open. Re-pull page 1 fresh so cache and state both reflect the reset.
            page = 1
            loadPage(token, targetPage = 1, forceRefresh = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "AniList mark-all-read failed" }
        }
    }

    private suspend fun loadPage(token: String, targetPage: Int, forceRefresh: Boolean = false) {
        loadMutex.withLock {
            try {
                val data = AniListClient.query(
                    query = PAGE_QUERY,
                    variables = buildJsonObject {
                        put("page", targetPage)
                        put("perPage", PAGE_SIZE)
                    },
                    accessToken = token,
                    forceRefresh = forceRefresh,
                )
                val unread = data["Viewer"]?.jsonObject
                    ?.get("unreadNotificationCount")?.jsonPrimitive?.intOrNull ?: 0
                val pageObj = data["Page"]?.jsonObject
                val hasNext = pageObj?.get("pageInfo")?.jsonObject
                    ?.get("hasNextPage")?.jsonPrimitive?.booleanOrNull ?: false
                val parsed = pageObj?.get("notifications")?.jsonArray.orEmpty()
                    .mapNotNull { element -> (element as? JsonObject)?.let(::parseNotification) }

                val existing = _uiState.value.items
                val merged = (if (targetPage == 1) parsed else existing + parsed)
                    .distinctBy { it.id }
                    .take(MAX_ITEMS)
                page = targetPage
                _uiState.value = _uiState.value.copy(
                    isAuthenticated = true,
                    isLoading = false,
                    isLoadingMore = false,
                    items = merged,
                    unreadCount = unread,
                    error = null,
                    hasReachedEnd = !hasNext || merged.size >= MAX_ITEMS,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) { "AniList notifications page $targetPage failed" }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = error.message?.takeIf { it.isNotBlank() } ?: "Failed to load notifications",
                )
            }
        }
    }

    private fun parseNotification(obj: JsonObject): AniListNotification? {
        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return null
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val media = obj["media"]?.jsonObject
        val mediaTitleObj = media?.get("title")?.jsonObject
        val user = obj["user"]?.jsonObject
        val thread = obj["thread"]?.jsonObject
        return AniListNotification(
            id = id,
            type = type,
            createdAtEpochSec = obj["createdAt"]?.jsonPrimitive?.longOrNull,
            context = obj["context"]?.jsonPrimitive?.contentOrNull,
            mediaId = media?.get("id")?.jsonPrimitive?.intOrNull,
            mediaTitle = (mediaTitleObj?.get("userPreferred") ?: mediaTitleObj?.get("romaji"))
                ?.jsonPrimitive?.contentOrNull,
            mediaCover = media?.get("coverImage")?.jsonObject?.get("medium")?.jsonPrimitive?.contentOrNull,
            mediaBanner = media?.get("bannerImage")?.jsonPrimitive?.contentOrNull,
            episode = obj["episode"]?.jsonPrimitive?.intOrNull,
            userId = user?.get("id")?.jsonPrimitive?.intOrNull,
            userName = user?.get("name")?.jsonPrimitive?.contentOrNull,
            userAvatar = user?.get("avatar")?.jsonObject?.get("medium")?.jsonPrimitive?.contentOrNull,
            message = obj["message"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: obj["deletedMediaTitle"]?.jsonPrimitive?.contentOrNull
                ?: obj["reason"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private val PAGE_QUERY = """
        query Notifications(${'$'}page: Int, ${'$'}perPage: Int) {
            Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { hasNextPage }
                notifications {
                    ... on AiringNotification { id type createdAt episode media { id bannerImage coverImage { medium } title { userPreferred romaji } } }
                    ... on FollowingNotification { id type createdAt user { id name avatar { medium } } }
                    ... on ActivityReplyNotification { id type createdAt activityId user { id name avatar { medium } } }
                    ... on ActivityMentionNotification { id type createdAt activityId user { id name avatar { medium } } }
                    ... on ActivityMessageNotification { id type createdAt message { id message(asHtml: false) } user { id name avatar { medium } } }
                    ... on ActivityReplySubscribedNotification { id type createdAt activityId user { id name avatar { medium } } }
                    ... on ActivityLikeNotification { id type createdAt activityId user { id name avatar { medium } } }
                    ... on ActivityReplyLikeNotification { id type createdAt activityId user { id name avatar { medium } } }
                    ... on ThreadCommentMentionNotification { id type createdAt thread { id title } user { id name avatar { medium } } }
                    ... on ThreadCommentReplyNotification { id type createdAt thread { id title } user { id name avatar { medium } } }
                    ... on ThreadCommentSubscribedNotification { id type createdAt thread { id title } user { id name avatar { medium } } }
                    ... on ThreadCommentLikeNotification { id type createdAt thread { id title } user { id name avatar { medium } } }
                    ... on ThreadLikeNotification { id type createdAt thread { id title } user { id name avatar { medium } } }
                    ... on MediaDeletionNotification { id type createdAt deletedMediaTitle }
                    ... on MediaDataChangeNotification { id type createdAt reason media { id title { userPreferred } } }
                    ... on MediaMergeNotification { id type createdAt deletedMediaTitles media { id title { userPreferred } } }
                }
            }
            Viewer { id unreadNotificationCount }
        }
    """.trimIndent()
}
