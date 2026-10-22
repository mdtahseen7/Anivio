package com.nuvio.app.features.anime

import com.nuvio.app.core.anilist.AniListUnavailableException
import com.nuvio.app.core.anilist.AniListRateLimitedException
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PublicAnimeRouterTest {
    @AfterTest
    fun tearDown() = PublicAnimeRouter.resetForTests()

    @Test
    fun unavailableOpensCircuitAndNextRequestUsesFallback() = runBlocking {
        val fallback = FakeFallback()
        PublicAnimeRouter.installFallback(fallback)

        val first = PublicAnimeRouter.route(
            aniList = { throw AniListUnavailableException(503) },
            fallback = { "mal" },
        )
        val second = PublicAnimeRouter.route(
            aniList = { "anilist" },
            fallback = { "mal" },
        )

        assertEquals("mal", first)
        assertEquals("mal", second)
        assertEquals(PublicAnimeProvider.MAL, PublicAnimeRouter.activeProvider)
    }

    @Test
    fun forceProbeRecoversToAniList() = runBlocking {
        PublicAnimeRouter.installFallback(FakeFallback())
        PublicAnimeRouter.route(
            aniList = { throw AniListUnavailableException(503) },
            fallback = { "mal" },
        )

        val result = PublicAnimeRouter.route(
            forceProbe = true,
            aniList = { "anilist" },
            fallback = { "mal" },
        )

        assertEquals("anilist", result)
        assertEquals(PublicAnimeProvider.ANILIST, PublicAnimeRouter.activeProvider)
    }

    @Test
    fun ordinaryAndCancellationFailuresDoNotFailOver() = runBlocking {
        PublicAnimeRouter.installFallback(FakeFallback())
        assertFailsWith<IllegalArgumentException> {
            PublicAnimeRouter.route(
                aniList = { throw IllegalArgumentException("bad query") },
                fallback = { "mal" },
            )
        }
        assertFailsWith<CancellationException> {
            PublicAnimeRouter.route(
                aniList = { throw CancellationException() },
                fallback = { "mal" },
            )
        }
        assertEquals(PublicAnimeProvider.ANILIST, PublicAnimeRouter.activeProvider)
    }

    @Test
    fun rateLimitIsNotAnOutageBoundary() = runBlocking {
        PublicAnimeRouter.installFallback(FakeFallback())

        assertFailsWith<AniListRateLimitedException> {
            PublicAnimeRouter.route(
                aniList = { throw AniListRateLimitedException() },
                fallback = { "mal" },
            )
        }

        assertEquals(PublicAnimeProvider.ANILIST, PublicAnimeRouter.activeProvider)
    }

    @Test
    fun optionalFallbackRunsWhenMalIsNotInstalled() = runBlocking {
        val result = PublicAnimeRouter.routeWithOptionalFallback(
            aniList = { throw AniListUnavailableException(503) },
            fallback = { "anivio" },
        )

        assertEquals("anivio", result)
        assertEquals(PublicAnimeProvider.ANILIST, PublicAnimeRouter.activeProvider)
    }

    @Test
    fun explicitAnivioSelectionUsesOptionalFallbackWithoutMal() = runBlocking {
        PublicAnimeRouter.setPreference(AnimeDataSourcePreference.ANIVIO)

        val result = PublicAnimeRouter.routeWithOptionalFallback(
            aniList = { error("AniList should not be called for an explicit Anivio selection") },
            fallback = { "anivio" },
        )

        assertEquals("anivio", result)
        assertEquals(PublicAnimeProvider.ANIVIO, PublicAnimeRouter.activeProvider)
    }

    @Test
    fun fallbackKeepsEpisodeIdentitySeparateFromExternalProviderIdentity() = runBlocking {
        val fallbackResult = PublicAnimeMetaResult(
            meta = MetaDetails(
                id = "anilist:99",
                type = "series",
                name = "Fallback title",
                videos = listOf(
                    MetaVideo(
                        id = "mal:99:1:1",
                        title = "Episode 1",
                        season = 1,
                        episode = 1,
                    ),
                ),
            ),
            externalFallbackId = "tt1234567",
        )
        val fallback = FakeFallback(detailsResult = fallbackResult)
        PublicAnimeRouter.installFallback(fallback)

        val result = PublicAnimeRouter.route(
            aniList = { throw AniListUnavailableException(503) },
            fallback = { requireNotNull(it.details("anilist:99", "series", false)) },
        )

        assertEquals("anilist:99", result.meta.id)
        assertEquals("mal:99:1:1", result.meta.videos.single().id)
        assertEquals("tt1234567", result.externalFallbackId)
    }

    private class FakeFallback(
        private val detailsResult: PublicAnimeMetaResult? = null,
    ) : PublicAnimeFallbackProvider {
        override suspend fun catalog(catalogId: String, contentType: String, page: Int, maxItems: Int?, searchQuery: String?, forceRefresh: Boolean) = CatalogPage(emptyList(), 0, null)
        override suspend fun homeRows(catalogIds: List<String>, maxItems: Int?, forceRefresh: Boolean) = emptyMap<String, CatalogPage>()
        override suspend fun discover(page: Int, contentType: String, genre: String?, sort: String, forceRefresh: Boolean) = CatalogPage(emptyList(), 0, null)
        override suspend fun details(id: String, contentType: String, forceRefresh: Boolean): PublicAnimeMetaResult? = detailsResult
    }
}
