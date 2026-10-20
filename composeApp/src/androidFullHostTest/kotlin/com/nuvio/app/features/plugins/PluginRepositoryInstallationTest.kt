package com.nuvio.app.features.plugins

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PluginRepositoryInstallationTest {
    @Test
    fun addingRepositoryDoesNotDownloadProviderScripts() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            resetRepository()
            val repositoryUrl = server.url("/manifest.json").toString()
            server.enqueue(MockResponse().setBody(manifestJson()))

            val result = PluginRepository.addRepository(repositoryUrl)

            assertIs<AddPluginRepositoryResult.Success>(result)
            assertEquals("/manifest.json", server.takeRequest(1, TimeUnit.SECONDS)?.path)
            assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))

            val scrapers = PluginRepository.uiState.value.scrapers
            assertEquals(2, scrapers.size)
            assertTrue(scrapers.all { it.code.isBlank() })
            assertTrue(scrapers.none { it.enabled })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun enablingOneProviderDownloadsOnlyThatProvider() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            resetRepository()
            val repositoryUrl = addRepository(server)
            val provider = PluginRepository.uiState.value.scrapers.single { it.id.endsWith(":alpha") }
            server.enqueue(MockResponse().setBody("module.exports = 'alpha';"))

            PluginRepository.toggleScraper(provider.id, enabled = true)
            val state = awaitState { current -> current.scrapers.first { it.id == provider.id }.code.isNotBlank() }

            assertEquals("/alpha.js", server.takeRequest(1, TimeUnit.SECONDS)?.path)
            assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
            assertTrue(state.scrapers.first { it.id == provider.id }.enabled)
            assertTrue(state.scrapers.first { it.id == provider.id }.code.isNotBlank())
            assertTrue(state.scrapers.first { it.id.endsWith(":beta") }.code.isBlank())
            assertFalse(state.scrapers.first { it.id.endsWith(":beta") }.enabled)
            assertTrue(repositoryUrl in state.repositories.map { it.manifestUrl })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun refreshPreservesInstalledProvidersAndLeavesUnselectedProvidersUninstalled() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            resetRepository()
            val repositoryUrl = addRepository(server)
            val alpha = PluginRepository.uiState.value.scrapers.single { it.id.endsWith(":alpha") }
            server.enqueue(MockResponse().setBody("module.exports = 'alpha';"))
            PluginRepository.toggleScraper(alpha.id, enabled = true)
            awaitState { current -> current.scrapers.first { it.id == alpha.id }.code.isNotBlank() }
            assertEquals("/alpha.js", server.takeRequest(1, TimeUnit.SECONDS)?.path)

            server.enqueue(MockResponse().setBody(manifestJson(version = "2.0.0")))
            server.enqueue(MockResponse().setBody("module.exports = 'alpha refreshed';"))
            PluginRepository.refreshRepository(repositoryUrl)

            val state = awaitState { current ->
                current.repositories.single().isRefreshing.not() &&
                    current.repositories.single().version == "2.0.0"
            }

            assertEquals("/manifest.json", server.takeRequest(1, TimeUnit.SECONDS)?.path)
            assertEquals("/alpha.js", server.takeRequest(1, TimeUnit.SECONDS)?.path)
            assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))

            val refreshedAlpha = state.scrapers.first { it.id.endsWith(":alpha") }
            val unselectedBeta = state.scrapers.first { it.id.endsWith(":beta") }
            assertTrue(refreshedAlpha.enabled)
            assertEquals("module.exports = 'alpha refreshed';", refreshedAlpha.code)
            assertFalse(unselectedBeta.enabled)
            assertTrue(unselectedBeta.code.isBlank())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun failedProviderDownloadLeavesProviderDisabledAndObservable() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            resetRepository()
            addRepository(server)
            val provider = PluginRepository.uiState.value.scrapers.single { it.id.endsWith(":alpha") }
            server.enqueue(MockResponse().setResponseCode(500).setBody("download failed"))

            PluginRepository.toggleScraper(provider.id, enabled = true)
            awaitRequest(server, "/alpha.js")
            val failed = awaitState { current ->
                current.scrapers.first { it.id == provider.id }.downloadErrorMessage != null
            }.scrapers.first { it.id == provider.id }

            assertFalse(failed.enabled)
            assertTrue(failed.code.isBlank())
            assertTrue(failed.downloadErrorMessage != null)
        } finally {
            server.shutdown()
        }
    }

    private fun resetRepository() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nuvio_plugins", 0).edit().clear().commit()
        context.filesDir.resolve("nuvio_plugin_scrapers").deleteRecursively()
        PluginStorage.initialize(context)
        PluginRepository.clearLocalState()
    }

    private suspend fun addRepository(server: MockWebServer): String {
        val repositoryUrl = server.url("/manifest.json").toString()
        server.enqueue(MockResponse().setBody(manifestJson()))
        val result = PluginRepository.addRepository(repositoryUrl)
        assertIs<AddPluginRepositoryResult.Success>(result)
        assertEquals("/manifest.json", server.takeRequest(1, TimeUnit.SECONDS)?.path)
        return repositoryUrl
    }

    private suspend fun awaitState(predicate: (PluginsUiState) -> Boolean): PluginsUiState {
        var matchingState: PluginsUiState? = null
        withTimeout(5_000) {
            while (matchingState == null) {
                val state = PluginRepository.uiState.value
                if (predicate(state)) {
                    matchingState = state
                } else {
                    delay(25)
                }
            }
        }
        return matchingState!!
    }

    private fun awaitRequest(server: MockWebServer, expectedPath: String) {
        assertEquals(expectedPath, server.takeRequest(1, TimeUnit.SECONDS)?.path)
    }

    private fun manifestJson(version: String = "1.0.0"): String =
        """
        {
          "name": "Test providers",
          "version": "$version",
          "scrapers": [
            {"id": "alpha", "name": "Alpha", "version": "1.0.0", "filename": "alpha.js"},
            {"id": "beta", "name": "Beta", "version": "1.0.0", "filename": "beta.js"}
          ]
        }
        """.trimIndent()
}
