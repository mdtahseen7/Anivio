package com.nuvio.app.features.plugins

import kotlinx.coroutines.flow.StateFlow

expect object PluginRepository {
    val uiState: StateFlow<PluginsUiState>

    fun initialize()

    fun onProfileChanged(profileId: Int)

    fun clearLocalState()

    suspend fun pullFromServer(profileId: Int)

    suspend fun addRepository(rawUrl: String): AddPluginRepositoryResult

    fun removeRepository(manifestUrl: String)

    fun refreshAll()

    fun refreshRepository(manifestUrl: String, pushAfterRefresh: Boolean = false)

    fun toggleScraper(scraperId: String, enabled: Boolean)

    /**
     * Moves a provider one step within its own repository group. The manga server
     * dropdown and stream resolution follow this order.
     */
    fun moveScraper(scraperId: String, moveUp: Boolean)

    fun setPluginsEnabled(enabled: Boolean)

    fun setGroupStreamsByRepository(enabled: Boolean)

    fun getEnabledScrapersForType(type: String): List<PluginScraper>

    fun getEnabledMangaScrapers(): List<PluginScraper>

    suspend fun getMangaChapters(scraper: PluginScraper, mangaId: String, title: String): Result<List<MangaPluginChapter>>

    suspend fun getMangaChapterPages(scraper: PluginScraper, chapterId: String): Result<List<MangaPluginPage>>

    suspend fun testScraper(scraperId: String): Result<List<PluginRuntimeResult>>

    suspend fun executeScraper(
        scraper: PluginScraper,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): Result<List<PluginRuntimeResult>>
}
