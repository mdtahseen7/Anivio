package com.nuvio.app.features.details

import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.anilist.AniListAuthRepository
import com.nuvio.app.features.details.components.DantotsuCommentSort
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DantotsuUser(
    val id: String,
    val username: String,
    val profilePictureUrl: String? = null,
    val isBanned: Boolean = false,
    val isAdmin: Boolean = false,
    val isMod: Boolean = false,
    val totalVotes: Int = 0,
)

data class DantotsuComment(
    val id: Int,
    val userId: String,
    val mediaId: Int,
    val parentCommentId: Int? = null,
    val content: String,
    val timestamp: String,
    val deleted: Boolean = false,
    val tag: Int? = null,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val userVoteType: Int? = null,
    val totalVotes: Int = 0,
    val replyCount: Int = 0,
    val username: String,
    val profilePictureUrl: String? = null,
    val isAdmin: Boolean = false,
    val isMod: Boolean = false,
    val replies: List<DantotsuComment> = emptyList(),
)

data class DantotsuCommentPage(
    val comments: List<DantotsuComment>,
    val totalPages: Int,
)

data class DantotsuAuthSession(
    val token: String,
    val user: DantotsuUser?,
)

internal class DantotsuCommentsException(message: String) : Exception(message)

/** Thin, protocol-focused client for the authenticated Dantotsu comments service. */
internal object DantotsuCommentsApi {
    private const val BASE_URL = "https://api.dantotsu.app"
    private const val TOKEN_CACHE_WINDOW_MS = 6 * 24 * 60 * 60 * 1000L
    private const val USER_AGENT = "Anivio"
    private const val APP_AUTH = "6*45Qp%W2RS@t38jkXoSKY588Ynj%n"
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private var cachedSession: DantotsuAuthSession? = null
    private var cachedAtEpochMs: Long = 0L
    private var cachedAniListTokenHash: Int? = null

    suspend fun comments(
        mediaId: Int,
        page: Int,
        sort: DantotsuCommentSort,
    ): DantotsuCommentPage = requestWithReauth { token ->
        val response = raw(
            method = "GET",
            path = "/comments/$mediaId/${page + 1}?sort=${sort.apiValue}",
            token = token,
        )
        response.requireSuccess().toCommentPage()
    }

    suspend fun replies(commentId: Int): List<DantotsuComment> = requestWithReauth { token ->
        raw("GET", "/comments/parent/$commentId/1", token = token)
            .requireSuccess()
            .toCommentPage().comments
    }

    suspend fun create(
        mediaId: Int,
        userId: String,
        content: String,
        parentCommentId: Int?,
        tag: Int? = null,
    ): DantotsuComment = requestWithReauth { token ->
        val fields = buildList {
            add("user_id" to userId)
            add("media_id" to mediaId.toString())
            add("content" to content)
            tag?.let { add("tag" to it.toString()) }
            parentCommentId?.let { add("parent_comment_id" to it.toString()) }
        }
        val response = raw(
            method = "POST",
            path = "/comments",
            token = token,
            body = form(fields),
            contentType = "application/x-www-form-urlencoded",
        )
        response.requireSuccess().toReturnedComment()
    }

    suspend fun edit(commentId: Int, content: String): Boolean = requestWithReauth { token ->
        raw(
            method = "PATCH",
            path = "/comments/$commentId",
            token = token,
            body = form(listOf("content" to content)),
            contentType = "application/x-www-form-urlencoded",
        ).isSuccess()
    }

    suspend fun delete(commentId: Int): Boolean = requestWithReauth { token ->
        raw("DELETE", "/comments/$commentId", token = token).isSuccess()
    }

    suspend fun vote(commentId: Int, score: Int): Boolean = requestWithReauth { token ->
        raw("POST", "/comments/vote/$commentId/$score", token = token).isSuccess()
    }

    suspend fun report(commentId: Int, reason: String): Boolean = requestWithReauth { token ->
        raw(
            method = "POST",
            path = "/report/$commentId",
            token = token,
            body = form(listOf("reason" to reason)),
            contentType = "application/x-www-form-urlencoded",
        ).isSuccess()
    }

    suspend fun ban(username: String): Boolean = requestWithReauth { token ->
        raw("POST", "/ban/${username.encodeURLParameter()}", token = token).isSuccess()
    }

    fun cachedUser(): DantotsuUser? = cachedSession?.user

    fun invalidateSession() {
        cachedSession = null
        cachedAtEpochMs = 0L
        cachedAniListTokenHash = null
    }

    private suspend fun <T> requestWithReauth(block: suspend (String) -> T): T {
        val firstToken = ensureSession().token
        return try {
            block(firstToken)
        } catch (error: DantotsuUnauthorizedException) {
            invalidateSession()
            block(ensureSession().token)
        }
    }

    private suspend fun ensureSession(): DantotsuAuthSession {
        val now = com.nuvio.app.core.time.EpisodeReleaseDatePlatform.nowEpochMs()
        val aniListToken = AniListAuthRepository.accessTokenOrNull()
            ?: throw DantotsuCommentsException("Connect AniList to use comments.")
        if (cachedAniListTokenHash != null && cachedAniListTokenHash != aniListToken.hashCode()) {
            invalidateSession()
        }
        cachedSession?.takeIf { now - cachedAtEpochMs < TOKEN_CACHE_WINDOW_MS }?.let { return it }
        val response = raw(
            method = "POST",
            path = "/authenticate",
            body = form(listOf("token" to aniListToken)),
            contentType = "application/x-www-form-urlencoded",
            token = null,
        )
        if (response.status == 401) throw DantotsuCommentsException("AniList authentication was rejected.")
        val session = response.requireSuccess().toAuthSession()
        cachedSession = session
        cachedAtEpochMs = now
        cachedAniListTokenHash = aniListToken.hashCode()
        return session
    }

    private suspend fun raw(
        method: String,
        path: String,
        token: String?,
        body: String = "",
        contentType: String? = null,
    ): RawHttpResponse {
        val headers = buildMap {
            put("Accept", "application/json")
            put("User-Agent", USER_AGENT)
            put("appauth", APP_AUTH)
            token?.let { put("Authorization", it) }
            contentType?.let { put("Content-Type", it) }
        }
        return httpRequestRaw(
            method = method,
            url = "$BASE_URL$path",
            headers = headers,
            body = body,
        )
    }

    private fun RawHttpResponse.requireSuccess(): String {
        if (status == 401) throw DantotsuUnauthorizedException()
        if (status !in 200..299) {
            val reason = runCatching {
                json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            throw DantotsuCommentsException(reason ?: "Comments request failed ($status).")
        }
        return body
    }

    private fun RawHttpResponse.isSuccess(): Boolean {
        if (status == 401) throw DantotsuUnauthorizedException()
        return status in 200..299
    }

    private fun String.toAuthSession(): DantotsuAuthSession {
        val root = json.parseToJsonElement(this).jsonObject
        val token = root.string("authToken") ?: throw DantotsuCommentsException("Comments authentication returned no token.")
        return DantotsuAuthSession(token = token, user = root["user"]?.jsonObject?.toUser())
    }

    private fun String.toCommentPage(): DantotsuCommentPage {
        val root = json.parseToJsonElement(this).jsonObject
        val comments = root["comments"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toComment() }
        return DantotsuCommentPage(
            comments = comments,
            totalPages = root.int("totalPages") ?: 1,
        )
    }

    private fun String.toReturnedComment(): DantotsuComment {
        val comment = json.parseToJsonElement(this).jsonObject.toComment()
            ?: throw DantotsuCommentsException("Comments service returned an invalid comment.")
        return comment.copy(
            username = cachedSession?.user?.username.orEmpty().ifBlank { comment.username },
            profilePictureUrl = cachedSession?.user?.profilePictureUrl ?: comment.profilePictureUrl,
        )
    }

    private fun JsonObject.toComment(): DantotsuComment? {
        val id = int("commentId", "comment_id", "id") ?: return null
        return DantotsuComment(
            id = id,
            userId = string("userId", "user_id").orEmpty(),
            mediaId = int("mediaId", "media_id") ?: 0,
            parentCommentId = int("parentCommentId", "parent_comment_id"),
            content = string("content").orEmpty(),
            timestamp = string("timestamp").orEmpty(),
            deleted = bool("deleted"),
            tag = int("tag"),
            upvotes = int("upvotes") ?: 0,
            downvotes = int("downvotes") ?: 0,
            userVoteType = int("userVoteType", "user_vote_type"),
            totalVotes = int("totalVotes", "total_votes") ?: 0,
            replyCount = int("replyCount", "reply_count") ?: 0,
            username = string("username").orEmpty(),
            profilePictureUrl = string("profilePictureUrl", "profile_picture_url"),
            isAdmin = bool("isAdmin", "is_admin"),
            isMod = bool("isMod", "is_mod"),
        )
    }

    private fun JsonObject.toUser(): DantotsuUser = DantotsuUser(
        id = string("id", "user_id").orEmpty(),
        username = string("username").orEmpty(),
        profilePictureUrl = string("profilePictureUrl", "profile_picture_url"),
        isBanned = bool("isBanned", "is_banned"),
        isAdmin = bool("isAdmin", "is_admin"),
        isMod = bool("isMod", "is_mod"),
        totalVotes = int("totalVotes", "total_votes") ?: 0,
    )

    private fun JsonObject.string(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.contentOrNull }

    private fun JsonObject.int(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.intOrNull }

    private fun JsonObject.bool(vararg keys: String): Boolean {
        val primitive = keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive } ?: return false
        return primitive.booleanOrNull ?: primitive.intOrNull?.let { it != 0 } ?: false
    }

    private fun form(fields: List<Pair<String, String>>): String = fields.joinToString("&") { (key, value) ->
        "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
    }

    private class DantotsuUnauthorizedException : Exception()
}
