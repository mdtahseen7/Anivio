package com.nuvio.app.features.home

import com.nuvio.app.features.catalog.CatalogTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
        val hero = MetaPreview("hero", "series", "Hero")
        val pick = MetaPreview("pick", "series", "Pick")
        val another = MetaPreview("other", "series", "Another")
        val result = buildHomeDiscoveryContent(
            listOf(section("popular", listOf(hero, pick, another)), section("trending", listOf(pick, another))),
            listOf(hero), emptySet(), emptySet(), false,
        )
        assertEquals(pick, result.spotlight)
        assertEquals(listOf(hero, another), result.shuffle)
        assertFalse(result.shuffle.any { it.stableKey() == result.spotlight?.stableKey() })
    }

    @Test fun upcomingStaysOutOfPicksAndRespectsTheHideUnreleasedPreference() {
        val released = MetaPreview("released", "series", "Released")
        val later = MetaPreview("later", "series", "Later", rawReleaseDate = "2028-01-01")
        val sooner = MetaPreview("sooner", "series", "Sooner", rawReleaseDate = "2027-01-01")
        val sections = listOf(section("popular", listOf(released)), section("upcoming", listOf(later, sooner)))
        val visible = buildHomeDiscoveryContent(sections, emptyList(), emptySet(), emptySet(), false)
        assertEquals(released, visible.spotlight)
        assertEquals(listOf(sooner, later), visible.horizon)
        assertEquals(emptyList(), buildHomeDiscoveryContent(sections, emptyList(), emptySet(), emptySet(), true).horizon)
    }

    private fun section(id: String, items: List<MetaPreview>) = HomeCatalogSection(
        key = "anilist:series:$id", title = id, subtitle = "", addonName = "",
        target = CatalogTarget.AniList(catalogId = id, contentType = "series"), items = items,
    )
}
