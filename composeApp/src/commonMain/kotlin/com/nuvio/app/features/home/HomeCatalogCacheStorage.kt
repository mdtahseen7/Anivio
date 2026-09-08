package com.nuvio.app.features.home

/**
 * Disk slot for [HomeCatalogCache]'s payload: one profile-scoped string, the same shape every other
 * cache in the app uses (`StreamLinkCacheStorage`, `ContinueWatchingEnrichmentStorage`).
 */
internal expect object HomeCatalogCacheStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
    fun clearPayload()
}
