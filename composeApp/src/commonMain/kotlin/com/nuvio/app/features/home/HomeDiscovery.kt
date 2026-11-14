package com.nuvio.app.features.home

import com.nuvio.app.features.watching.application.WatchingState
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

internal enum class HomeDiscoverySection(val key: String, val title: StringResource, val description: StringResource) {
    Spotlight("anivio:discovery:spotlight", Res.string.home_discovery_spotlight, Res.string.home_discovery_spotlight_description),
    Shuffle("anivio:discovery:shuffle", Res.string.home_discovery_shuffle, Res.string.home_discovery_shuffle_description),
    Horizon("anivio:discovery:horizon", Res.string.home_discovery_horizon, Res.string.home_discovery_horizon_description);

    companion object {
        fun fromKey(key: String): HomeDiscoverySection? = entries.firstOrNull { it.key == key }
    }
}

/** Seed new sections between existing catalogs without changing their relative order. */
internal fun seedHomeDiscoveryOrder(keys: List<String>, catalogKeys: Set<String>, missing: Set<String>): List<String> {
    if (missing.isEmpty()) return keys
    val result = mutableListOf<String>()
    var catalogs = 0
    val pending = HomeDiscoverySection.entries.map { it.key }.filter { it in missing }.toMutableList()
    keys.filterNot { it in missing }.forEach { key ->
        result += key
        if (key in catalogKeys) {
            catalogs++
            val section = when (catalogs) {
                1 -> HomeDiscoverySection.Spotlight
                3 -> HomeDiscoverySection.Shuffle
                5 -> HomeDiscoverySection.Horizon
                else -> null
            }
            if (section != null && pending.remove(section.key)) result += section.key
        }
    }
    return result + pending
}

internal data class HomeDiscoveryContent(
    val spotlight: MetaPreview?,
    val shuffle: List<MetaPreview>,
    val horizon: List<MetaPreview>,
)

internal fun buildHomeDiscoveryContent(
    sections: List<HomeCatalogSection>,
    heroItems: List<MetaPreview>,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    hideUnreleased: Boolean,
): HomeDiscoveryContent {
    val heroKeys = heroItems.mapTo(mutableSetOf()) { it.stableKey() }
    val pool = sections.filterNot { it.key.endsWith(":upcoming") }
        .flatMap { it.items }.distinctBy { it.stableKey() }
    val unwatched = pool.filterNot {
        WatchingState.isPosterWatched(watchedKeys, it, fullyWatchedSeriesKeys)
    }
    val candidates = unwatched.ifEmpty { pool }
    val spotlight = candidates.firstOrNull { it.stableKey() !in heroKeys } ?: candidates.firstOrNull()
    return HomeDiscoveryContent(
        spotlight = spotlight,
        shuffle = candidates.filterNot { it.stableKey() == spotlight?.stableKey() }.take(16),
        horizon = if (hideUnreleased) emptyList() else sections.filter { it.key.endsWith(":upcoming") }
            .flatMap { it.items }.distinctBy { it.stableKey() }
            .sortedBy { it.rawReleaseDate?.takeIf(String::isNotBlank) ?: "9999" }.take(3),
    )
}
