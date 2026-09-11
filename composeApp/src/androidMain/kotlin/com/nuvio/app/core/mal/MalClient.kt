package com.nuvio.app.core.mal

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/** Bounded MAL v2 transport supporting both public client-id and authenticated bearer requests. */
object MalClient {
    private const val API_BASE = "https://api.myanimelist.net/v2"
    private const val TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
    private const val USER_AGENT = "Anivio"
    private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    private const val MAX_ATTEMPTS = 3
    private const val FALLBACK_RETRY_MS = 1_000L
    private const val MAX_RETRY_MS = 15_000L
    private const val ANIME_LIST_FIELDS =
        "list_status,main_picture,alternative_titles,start_date,mean,media_type,status,genres,num_episodes"
    const val DEFAULT_PAGE_LIMIT = 100
    const val MAX_PAGE_LIMIT = 500

    private val log = Logger.withTag("MalClient")
    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    suspend fun getCurrentUser(accessToken: String): MalUser =
        request(
            method = "GET",
            url = "$API_BASE/users/@me?fields=id,name,picture",
            headers = bearerHeaders(accessToken),
        ).decode()

    suspend inline fun <reified T> getPublic(
        path: String,
        clientId: String,
        query: Map<String, String> = emptyMap(),
    ): T = request(
        method = "GET",
        url = apiUrl(path, query),
        headers = publicHeaders(clientId),
    ).decode()

    suspend inline fun <reified T> getBearer(
        path: String,
        accessToken: String,
        query: Map<String, String> = emptyMap(),
    ): T = request(
        method = "GET",
        url = apiUrl(path, query),
        headers = bearerHeaders(accessToken),
    ).decode()

    suspend fun getAnimeListPage(
        accessToken: String,
        limit: Int = DEFAULT_PAGE_LIMIT,
        offset: Int = 0,
    ): MalPage<MalAnimeListEntry> = getBearer(
        path = "users/@me/animelist",
        accessToken = accessToken,
        query = pageQuery(limit, offset) + mapOf(
            "fields" to ANIME_LIST_FIELDS,
            "nsfw" to "true",
        ),
    )

    suspend fun updateAnimeListStatus(
        accessToken: String,
        animeId: Int,
        status: String? = null,
        numEpisodesWatched: Int? = null,
    ): MalListStatus {
        require(animeId > 0) { "MAL anime id must be positive" }
        require(status != null || numEpisodesWatched != null) { "At least one MAL list field is required" }
        val form = buildMap {
            status?.let { put("status", it) }
            numEpisodesWatched?.let { put("num_watched_episodes", it.coerceAtLeast(0).toString()) }
        }
        return request(
            method = "PATCH",
            url = apiUrl("anime/$animeId/my_list_status", emptyMap()),
            headers = bearerHeaders(accessToken) +
                ("Content-Type" to "application/x-www-form-urlencoded"),
            body = form.toUrlEncodedForm(),
        ).decode()
    }

    suspend fun deleteAnimeListStatus(accessToken: String, animeId: Int) {
        require(animeId > 0) { "MAL anime id must be positive" }
        request(
            method = "DELETE",
            url = apiUrl("anime/$animeId/my_list_status", emptyMap()),
            headers = bearerHeaders(accessToken),
        )
    }

    suspend fun exchangeAuthorizationCode(
        clientId: String,
        redirectUri: String,
        code: String,
        codeVerifier: String,
    ): MalTokenResponse = tokenRequest(
        mapOf(
            "client_id" to clientId,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "code_verifier" to codeVerifier,
        ),
    )

    suspend fun refreshToken(clientId: String, refreshToken: String): MalTokenResponse =
        tokenRequest(
            mapOf(
                "client_id" to clientId,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
            ),
        )

    private suspend fun tokenRequest(form: Map<String, String>): MalTokenResponse =
        request(
            method = "POST",
            url = TOKEN_URL,
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body = form.toUrlEncodedForm(),
        ).decode()

    private fun Map<String, String>.toUrlEncodedForm(): String = entries.joinToString("&") { (key, value) ->
        "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
    }

    @PublishedApi
    internal fun apiUrl(path: String, query: Map<String, String>): String {
        val normalizedPath = path.trim().removePrefix("/")
        require(normalizedPath.isNotBlank() && !normalizedPath.startsWith("http")) { "Invalid MAL API path" }
        if (query.isEmpty()) return "$API_BASE/$normalizedPath"
        return "$API_BASE/$normalizedPath?" + query.entries.joinToString("&") { (key, value) ->
            "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
        }
    }

    fun pageQuery(limit: Int = DEFAULT_PAGE_LIMIT, offset: Int = 0): Map<String, String> = mapOf(
        "limit" to limit.coerceIn(1, MAX_PAGE_LIMIT).toString(),
        "offset" to offset.coerceAtLeast(0).toString(),
    )

    @PublishedApi
    internal fun publicHeaders(clientId: String): Map<String, String> {
        require(clientId.isNotBlank()) { "MAL client id is required" }
        return mapOf("X-MAL-CLIENT-ID" to clientId)
    }

    @PublishedApi
    internal fun bearerHeaders(accessToken: String): Map<String, String> {
        require(accessToken.isNotBlank()) { "MAL access token is required" }
        return mapOf("Authorization" to "Bearer $accessToken")
    }

    @PublishedApi
    internal suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String = "",
    ): String {
        var lastResponse: RawHttpResponse? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val response = try {
                httpRequestRaw(
                    method = method,
                    url = url,
                    headers = mapOf("Accept" to "application/json", "User-Agent" to USER_AGENT) + headers,
                    body = body,
                    maxResponseBodyBytes = MAX_RESPONSE_BYTES,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalStateException) {
                if (error.message?.contains("exceeds", ignoreCase = true) == true) {
                    throw MalApiException.ResponseTooLarge(error)
                }
                if (attempt == MAX_ATTEMPTS - 1) throw MalApiException.Network(error)
                delay(FALLBACK_RETRY_MS * (attempt + 1))
                return@repeat
            } catch (error: Throwable) {
                if (attempt == MAX_ATTEMPTS - 1) throw MalApiException.Network(error)
                delay(FALLBACK_RETRY_MS * (attempt + 1))
                return@repeat
            }
            lastResponse = response
            if (response.body.endsWith("\n...[truncated]")) {
                throw MalApiException.ResponseTooLarge(
                    IllegalStateException("MAL response exceeded $MAX_RESPONSE_BYTES bytes"),
                )
            }
            if (response.status in 200..299) return response.body
            if ((response.status == 429 || response.status >= 500) && attempt < MAX_ATTEMPTS - 1) {
                val waitMs = response.retryAfterMs() ?: FALLBACK_RETRY_MS * (attempt + 1)
                log.w { "MAL ${response.status}; retrying in ${waitMs}ms" }
                delay(waitMs.coerceAtMost(MAX_RETRY_MS))
                return@repeat
            }
            throw response.toException()
        }
        throw requireNotNull(lastResponse).toException()
    }

    @PublishedApi
    internal inline fun <reified T> String.decode(): T = try {
        json.decodeFromString(this)
    } catch (error: Throwable) {
        throw MalApiException.MalformedResponse("MyAnimeList returned a malformed response", error)
    }

    private fun RawHttpResponse.toException(): MalApiException {
        val detail = runCatching { json.decodeFromString<MalErrorPayload>(body) }.getOrNull()
        val message = detail?.message?.takeIf(String::isNotBlank)
            ?: detail?.error?.takeIf(String::isNotBlank)
            ?: "MyAnimeList request failed ($status)"
        return when (status) {
            401, 403 -> MalApiException.Unauthorized(message)
            429 -> MalApiException.RateLimited(retryAfterMs(), message)
            else -> MalApiException.Http(status, message)
        }
    }

    private fun RawHttpResponse.retryAfterMs(): Long? = headers["retry-after"]
        ?.substringBefore(',')
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it >= 0L }
        ?.times(1_000L)
}
