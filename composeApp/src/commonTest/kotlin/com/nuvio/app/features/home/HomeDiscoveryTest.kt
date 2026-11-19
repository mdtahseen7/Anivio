package com.nuvio.app.features.home

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.core.anilist.AniListMedia
import com.nuvio.app.core.anilist.toMetaPreview
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class HomeDiscoveryTest {
    private val discoveryKeys = HomeDiscoverySection.entries.map { it.key }

    @Test fun newSectionsAreInterspersedWithoutReorderingExistingCatalogs() {
        val existing = listOf("custom-pinned", "recent", "trending", "popular", "upcoming", "movies")
        val seeded = seedHomeDiscoveryOrder(existing, existing.drop(1).toSet(), discoveryKeys.toSet())
        assertEquals(existing, seeded.filterNot { it in discoveryKeys })
        assertEquals(listOf("custom-pinned", "recent", discoveryKeys[0], "trending", "popular", discoveryKeys[1], "upcoming", "movies", discoveryKeys[2]), seeded)
    }

    @Test fun savedDiscoveryOrderIsKeptAndNewSectionsAreOnlyInsertedOnce() {
        val saved = listOf(discoveryKeys[1], "popular", discoveryKeys[0], "movies", discoveryKeys[2])
        assertEquals(saved, seedHomeDiscoveryOrder(saved, setOf("popular", "movies"), emptySet()))
        assertEquals(listOf("popular") + discoveryKeys, seedHomeDiscoveryOrder(listOf("popular"), setOf("popular"), discoveryKeys.toSet()))
    }

    @Test fun discoveryAvoidsHeroDuplicationAndDeduplicatesItsDeck() {
        val hero = MetaPreview("hero", "series", "Hero", countryOfOrigin = "JP")
        val pick = MetaPreview("pick", "series", "Pick", countryOfOrigin = "JP")
        val another = MetaPreview("other", "series", "Another", countryOfOrigin = "JP")
        val result = buildHomeDiscoveryContent(
            listOf(section("popular", listOf(hero, pick, another)), section("trending", listOf(pick, another))),
            listOf(hero), emptySet(), emptySet(), false,
        )
        assertEquals(pick, result.spotlight)
        assertEquals(listOf(hero, another), result.shuffle)
        assertFalse(result.shuffle.any { it.stableKey() == result.spotlight?.stableKey() })
    }

    @Test fun upcomingStaysOutOfPicksAndRespectsTheHideUnreleasedPreference() {
        val released = MetaPreview("released", "series", "Released", countryOfOrigin = "JP")
        val later = MetaPreview("later", "series", "Later", rawReleaseDate = "2028-01-01", countryOfOrigin = "JP")
        val sooner = MetaPreview("sooner", "series", "Sooner", rawReleaseDate = "2027-01-01", countryOfOrigin = "JP")
        val sections = listOf(section("popular", listOf(released)), section("upcoming", listOf(later, sooner)))
        val visible = buildHomeDiscoveryContent(sections, emptyList(), emptySet(), emptySet(), false)
        assertEquals(released, visible.spotlight)
        assertEquals(listOf(sooner, later), visible.horizon)
        assertEquals(emptyList(), buildHomeDiscoveryContent(sections, emptyList(), emptySet(), emptySet(), true).horizon)
    }

    @Test fun donghuaAndShortEpisodesStayOutOfAllThreeSections() {
        val regular = MetaPreview("regular", "series", "Regular", countryOfOrigin = "JP", mediaFormat = "TV", episodeDurationMinutes = 24)
        val rejected = listOf(
            regular.copy(id = "donghua", countryOfOrigin = "CN"),
            regular.copy(id = "short-format", mediaFormat = "TV_SHORT"),
            regular.copy(id = "short-ona", mediaFormat = "ONA", episodeDurationMinutes = 14),
        )
        val allowed = listOf(regular, regular.copy(id = "another"), regular.copy(id = "third"))
        val result = buildHomeDiscoveryContent(listOf(section("popular", rejected + allowed), section("upcoming", rejected + allowed)),
            emptyList(), emptySet(), emptySet(), false)
        val visible = listOfNotNull(result.spotlight) + result.shuffle + result.horizon
        assertTrue(visible.isNotEmpty())
        assertFalse(visible.any { candidate -> rejected.any { it.id == candidate.id } })
        val excludedOnly = buildHomeDiscoveryContent(listOf(section("popular", rejected), section("upcoming", rejected)),
            emptyList(), emptySet(), emptySet(), false)
        assertEquals(null, excludedOnly.spotlight)
        assertTrue(excludedOnly.shuffle.isEmpty())
        assertTrue(excludedOnly.horizon.isEmpty())
    }

    @Test fun durationBoundaryAndAnnouncedAnimeAreHandledWithoutGuessing() {
        val regular = MetaPreview("regular", "series", "Regular", countryOfOrigin = " jp ", mediaFormat = "TV")
        assertTrue(regular.copy(episodeDurationMinutes = 15).isHomeDiscoveryEligible())
        assertFalse(regular.copy(episodeDurationMinutes = 14).isHomeDiscoveryEligible())
        assertFalse(regular.copy(mediaFormat = "tv_short").isHomeDiscoveryEligible())
        assertTrue(regular.copy(episodeDurationMinutes = null).isHomeDiscoveryEligible())
        assertFalse(regular.copy(countryOfOrigin = null).isHomeDiscoveryEligible())
        assertFalse(regular.copy(countryOfOrigin = "??").isHomeDiscoveryEligible())
    }



    @Test fun catalogMetadataSurvivesMappingAndShortSeasonIsNotShortForm() {
        val media = Json.decodeFromString<AniListMedia>("""{
            "id": 12, "countryOfOrigin": "JP", "format": "TV", "episodes": 12, "duration": 24,
            "title": {"english": "A normal twelve-episode season"},
            "coverImage": {"large": "https://example.test/poster.jpg", "color": "#438C75"}
        }""")
        val preview = assertNotNull(media.toMetaPreview())
        assertEquals("JP", preview.countryOfOrigin)
        assertEquals("TV", preview.mediaFormat)
        assertEquals(24, preview.episodeDurationMinutes)
        assertEquals("#438C75", preview.artworkColor)
        assertTrue(preview.isHomeDiscoveryEligible())
        assertFalse(assertNotNull(media.copy(countryOfOrigin = "CN").toMetaPreview()).isHomeDiscoveryEligible())
    }

    private fun section(id: String, items: List<MetaPreview>) = HomeCatalogSection(
        key = "anilist:series:$id", title = id, subtitle = "", addonName = "",
        target = CatalogTarget.AniList(catalogId = id, contentType = "series"), items = items,
    )
}
