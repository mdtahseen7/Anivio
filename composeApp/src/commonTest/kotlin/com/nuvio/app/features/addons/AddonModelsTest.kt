package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddonModelsTest {

    @Test
    fun `disabled addon is installed but not active`() {
        val addon = ManagedAddon(
            manifestUrl = "https://example.test/manifest.json",
            manifest = manifest(),
            enabled = false,
        )

        assertFalse(addon.isActive)
        assertEquals(0, listOf(addon).toOverview().activeAddons)
        assertEquals(0, listOf(addon).toOverview().totalCatalogs)
    }

    @Test
    fun `enabled addons helper filters disabled addons`() {
        val enabled = ManagedAddon(
            manifestUrl = "https://enabled.example/manifest.json",
            manifest = manifest(id = "enabled"),
            enabled = true,
        )
        val disabled = ManagedAddon(
            manifestUrl = "https://disabled.example/manifest.json",
            manifest = manifest(id = "disabled"),
            enabled = false,
        )

        assertEquals(listOf(enabled), listOf(enabled, disabled).enabledAddons())
        assertTrue(enabled.isActive)
    }

    @Test
    fun `content type helpers expose manifest types`() {
        val movieAddon = ManagedAddon(
            manifestUrl = "https://movie.example/manifest.json",
            manifest = manifest(id = "movie"),
        )
        val mangaAddon = ManagedAddon(
            manifestUrl = "https://manga.example/manifest.json",
            manifest = manifest(id = "manga").copy(
                types = listOf("manga"),
                resources = listOf(AddonResource(name = "stream", types = listOf("manga"))),
            ),
        )

        assertTrue(movieAddon.supportsContentType("MOVIE"))
        assertTrue(mangaAddon.supportsContentType("manga"))
        assertEquals(listOf("manga", "movie"), listOf(movieAddon, mangaAddon).contentTypes())
    }

    @Test
    fun `failed addon keeps a failed health status and reason`() {
        val addon = ManagedAddon(
            manifestUrl = "https://broken.example/manifest.json",
            manifest = manifest(),
            errorMessage = "timeout",
            lastFailureReason = "timeout",
        )

        assertEquals(AddonHealthStatus.Failed, addon.healthStatus)
        assertEquals("timeout", addon.lastFailureReason)
    }
}

private fun manifest(id: String = "addon") = AddonManifest(
    id = id,
    name = id,
    description = "",
    version = "1.0.0",
    resources = listOf(AddonResource(name = "catalog", types = listOf("movie"))),
    types = listOf("movie"),
    catalogs = listOf(AddonCatalog(type = "movie", id = "popular", name = "Popular")),
    transportUrl = "https://$id.example/manifest.json",
)
