package com.nuvio.app.features.home

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.catalog.CatalogTarget
import kotlinx.serialization.Serializable

/** Serializable so the home rows can be cached on disk between launches (`HomeCatalogCache`). */
@Serializable
data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val banner: String? = null,
    val logo: String? = null,
    val posterShape: PosterShape = PosterShape.Poster,
    val description: String? = null,
    val releaseInfo: String? = null,
    val rawReleaseDate: String? = null,
    val popularity: Double? = null,
    val voteCount: Int? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val rawPosterUrl: String? = null,
    val landscapePoster: String? = null,
    val rawLandscapePosterUrl: String? = null,
    val airingStatus: String? = null,
    val countryOfOrigin: String? = null,
    val mediaFormat: String? = null,
    val episodeDurationMinutes: Int? = null,
    val artworkColor: String? = null,
)

fun MetaPreview.stableKey(): String = "$type:$id"

enum class PosterShape {
    Poster,
    Square,
    Landscape,
}

data class HomeCatalogSection(
    val key: String,
    val title: String,
    val subtitle: String,
    val addonName: String,
    val target: CatalogTarget,
    val items: List<MetaPreview>,
    val availableItemCount: Int = items.size,
    val hasMore: Boolean = false,
)

fun HomeCatalogSection.canOpenCatalog(previewLimit: Int): Boolean =
    availableItemCount > previewLimit || hasMore

data class HomeUiState(
    val isLoading: Boolean = false,
    val heroItems: List<MetaPreview> = emptyList(),
    val sections: List<HomeCatalogSection> = emptyList(),
    val errorMessage: String? = null,
)

// addonManifestsLoading is intentionally ignored: rows render from the manifest URL
// directly, so waiting for every manifest only delays first paint. An addon whose
// manifest hasn't arrived yet has its rows pop in when it lands (HomeScreen
// re-triggers refresh on manifest-signature change). Kept as a parameter so the
// call site is unchanged.
internal fun shouldShowInitialHomeLoading(
    hasRenderableHomeRows: Boolean,
    @Suppress("UNUSED_PARAMETER") addonManifestsLoading: Boolean,
    homeCatalogLoading: Boolean,
): Boolean = !hasRenderableHomeRows && homeCatalogLoading

internal fun shouldShowHomeHeroSlot(
    heroEnabled: Boolean,
    hasHeroItems: Boolean,
    isResolvingHeroSources: Boolean,
    hasRenderableHomeRows: Boolean,
): Boolean = heroEnabled && (hasHeroItems || isResolvingHeroSources || hasRenderableHomeRows)

internal data class CatalogRequest(
    val addon: ManagedAddon,
    val catalogId: String,
    val catalogName: String,
    val type: String,
    val supportsPagination: Boolean,
)
