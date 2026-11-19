package com.nuvio.app.features.anime

import com.nuvio.app.core.anilist.AniListUnavailableException
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.details.MetaDetails
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException

/** Public anime data supplied by the Android MAL implementation when AniList is unavailable. */
interface PublicAnimeFallbackProvider {
    suspend fun catalog(
        catalogId: String,
        contentType: String,
        page: Int,
        maxItems: Int?,
        searchQuery: String?,
        forceRefresh: Boolean,
    ): CatalogPage

    suspend fun homeRows(
        catalogIds: List<String>,
        maxItems: Int?,
        forceRefresh: Boolean,
    ): Map<String, CatalogPage>

    suspend fun discover(
        page: Int,
        contentType: String,
        genre: String?,
        sort: String,
        forceRefresh: Boolean,
    ): CatalogPage

    suspend fun details(
        id: String,
        contentType: String,
        forceRefresh: Boolean,
    ): PublicAnimeMetaResult?
}

data class PublicAnimeMetaResult(
    val meta: MetaDetails,
    val externalFallbackId: String?,
)

enum class PublicAnimeProvider { ANILIST, MAL }

/**
 * User-facing choice of where anime catalogs, Discover and metadata come from.
 *
 * [AUTO] keeps the AniList-primary circuit with automatic failover. The explicit values pin the
 * source and disable failover in that direction, which is what someone who prefers MAL's data — or
 * distrusts it — actually wants.
 */
enum class AnimeDataSourcePreference { AUTO, ANILIST, MAL }

/**
 * Process-wide AniList-primary circuit. Only [AniListUnavailableException] opens it; programming,
 * query, mapping and cancellation failures are never hidden by a provider switch.
 */
object PublicAnimeRouter {
    private const val PROBE_INTERVAL_MS = 2 * 60 * 1000L

    private val fallbackRef = atomic<PublicAnimeFallbackProvider?>(null)
    private val providerRef = atomic(PublicAnimeProvider.ANILIST)
    private val nextProbeAtRef = atomic(0L)
    private val preferenceRef = atomic(AnimeDataSourcePreference.AUTO)

    /** What the next request will actually use, accounting for both the pin and the circuit. */
    val activeProvider: PublicAnimeProvider
        get() = when (preferenceRef.value) {
            AnimeDataSourcePreference.ANILIST -> PublicAnimeProvider.ANILIST
            // A pin to MAL is meaningless without an installed provider; report the truth.
            AnimeDataSourcePreference.MAL ->
                if (fallbackRef.value != null) PublicAnimeProvider.MAL else PublicAnimeProvider.ANILIST
            AnimeDataSourcePreference.AUTO -> providerRef.value
        }

    val preference: AnimeDataSourcePreference
        get() = preferenceRef.value

    /** True once a fallback provider is installed, i.e. MAL is a selectable source. */
    val isFallbackAvailable: Boolean
        get() = fallbackRef.value != null

    fun setPreference(value: AnimeDataSourcePreference) {
        if (preferenceRef.value == value) return
        preferenceRef.value = value
        // Drop any open circuit so an explicit switch takes effect on the next request rather than
        // waiting out the probe interval.
        recoverToAniList()
    }

    fun installFallback(provider: PublicAnimeFallbackProvider?) {
        fallbackRef.value = provider
        if (provider == null) recoverToAniList()
    }

    suspend fun <T> fallbackOnly(block: suspend (PublicAnimeFallbackProvider) -> T): T? =
        fallbackRef.value?.let { block(it) }

    suspend fun <T> route(
        forceProbe: Boolean = false,
        aniList: suspend () -> T,
        fallback: suspend (PublicAnimeFallbackProvider) -> T,
    ): T {
        val provider = fallbackRef.value ?: return aniList()
        return when (preferenceRef.value) {
            AnimeDataSourcePreference.ANILIST -> aniList()
            AnimeDataSourcePreference.MAL -> fallback(provider)
            AnimeDataSourcePreference.AUTO -> routeAuto(forceProbe, aniList, fallback, provider)
        }
    }

    private suspend fun <T> routeAuto(
        forceProbe: Boolean,
        aniList: suspend () -> T,
        fallback: suspend (PublicAnimeFallbackProvider) -> T,
        provider: PublicAnimeFallbackProvider,
    ): T {
        val now = EpisodeReleaseDatePlatform.nowEpochMs()
        val shouldTryAniList = providerRef.value == PublicAnimeProvider.ANILIST ||
            forceProbe || now >= nextProbeAtRef.value

        if (shouldTryAniList) {
            try {
                return aniList().also { recoverToAniList() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: AniListUnavailableException) {
                if (NetworkStatusRepository.uiState.value.isOfflineLike) throw error
                providerRef.value = PublicAnimeProvider.MAL
                nextProbeAtRef.value = now + PROBE_INTERVAL_MS
            }
        }

        return fallback(provider)
    }

    internal fun recoverToAniList() {
        providerRef.value = PublicAnimeProvider.ANILIST
        nextProbeAtRef.value = 0L
    }

    internal fun resetForTests() {
        fallbackRef.value = null
        preferenceRef.value = AnimeDataSourcePreference.AUTO
        recoverToAniList()
    }
}
