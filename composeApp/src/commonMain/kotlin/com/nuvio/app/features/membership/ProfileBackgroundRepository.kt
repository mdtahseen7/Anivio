package com.nuvio.app.features.membership

import co.touchlab.kermit.Logger
import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.network.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val ProfileBackgroundBucket = "membership-profile-backgrounds"

data class ProfileBackgroundCatalogItem(
    val id: String,
    val displayName: String,
    val landscapeImageBytes: ByteArray? = null,
    val portraitImageBytes: ByteArray? = null,
    /** Anime banners are served directly by AniList, so they do not need to be duplicated in storage. */
    val landscapeImageUrl: String? = null,
    val assetVersion: Int,
)

object ProfileBackgroundRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("ProfileBackgroundRepository")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _catalog = MutableStateFlow<List<ProfileBackgroundCatalogItem>>(emptyList())
    val catalog: StateFlow<List<ProfileBackgroundCatalogItem>> = _catalog.asStateFlow()
    private var loadJob: Job? = null
    private var assetLoadJob: Job? = null
    private var remoteCatalog = emptyList<SupabaseProfileBackgroundCatalogItem>()
    private var animeCatalog = emptyList<ProfileBackgroundCatalogItem>()
    private var cacheHydrated = false
    private var remoteLoaded = false

    fun ensureLoaded() {
        hydrateFromCacheIfNeeded()
        if (remoteLoaded || loadJob?.isActive == true) return
        loadJob = scope.launch {
            val remoteItems = try {
                SupabaseProvider.client.postgrest
                    .rpc("get_member_profile_background_catalog")
                    .decodeList<SupabaseProfileBackgroundCatalogItem>()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log.w(error) { "Unable to load supporter profile backgrounds" }
                null
            }
            val animeItems = try {
                fetchAnimeBackgrounds()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log.w(error) { "Unable to load anime profile backgrounds" }
                emptyList()
            }
            animeCatalog = animeItems
            if (remoteItems != null) {
                remoteCatalog = remoteItems
                saveCachedCatalog(remoteItems)
                remoteLoaded = true
            }
            publishCatalog()
        }
    }

    fun loadSelectedAndPreload(id: String, portrait: Boolean) {
        ensureLoaded()
        remoteCatalog.firstOrNull { it.id == id }?.let { item ->
            loadCachedAndPublish(item, portrait)
        }
        assetLoadJob?.cancel()
        assetLoadJob = scope.launch {
            val initialSelected = remoteCatalog.firstOrNull { it.id == id }
            initialSelected?.let { loadAndPublish(it, portrait) }
            loadJob?.join()
            val selected = remoteCatalog.firstOrNull { it.id == id } ?: return@launch
            if (selected.assetVersion != initialSelected?.assetVersion) {
                loadAndPublish(selected, portrait)
            }
            coroutineScope {
                remoteCatalog
                    .filterNot { it.id == id }
                    .map { item -> launch { loadAndPublish(item, portrait) } }
                    .joinAll()
            }
        }
    }

    fun preloadLandscapeImages() {
        ensureLoaded()
        assetLoadJob?.cancel()
        assetLoadJob = scope.launch {
            val initialVersions = remoteCatalog.associate { it.id to it.assetVersion }
            preload(remoteCatalog, portrait = false)
            loadJob?.join()
            preload(
                items = remoteCatalog.filter { initialVersions[it.id] != it.assetVersion },
                portrait = false,
            )
        }
    }

    fun invalidate() {
        cacheHydrated = false
        remoteLoaded = false
        loadJob?.cancel()
        loadJob = null
        assetLoadJob?.cancel()
        assetLoadJob = null
        remoteCatalog = emptyList()
        animeCatalog = emptyList()
        _catalog.value = emptyList()
    }

    private fun hydrateFromCacheIfNeeded() {
        if (cacheHydrated) return
        cacheHydrated = true
        val payload = MemberAssetStorage.loadProfileBackgroundCatalogPayload().orEmpty().trim()
        if (payload.isEmpty()) return
        val stored = runCatching {
            json.decodeFromString<StoredProfileBackgroundCatalogPayload>(payload)
        }.getOrNull() ?: return
        remoteCatalog = stored.items
        publishCatalog()
    }

    private fun publishCatalog() {
        val current = _catalog.value.associateBy(ProfileBackgroundCatalogItem::id)
        val supporterCatalog = remoteCatalog.map { item ->
            val cached = current[item.id]?.takeIf { it.assetVersion == item.assetVersion }
            ProfileBackgroundCatalogItem(
                id = item.id,
                displayName = item.displayName,
                landscapeImageBytes = cached?.landscapeImageBytes,
                portraitImageBytes = cached?.portraitImageBytes,
                assetVersion = item.assetVersion,
            )
        }
        _catalog.value = animeCatalog + supporterCatalog
    }

    private fun saveCachedCatalog(items: List<SupabaseProfileBackgroundCatalogItem>) {
        MemberAssetStorage.saveProfileBackgroundCatalogPayload(
            json.encodeToString(StoredProfileBackgroundCatalogPayload(items)),
        )
    }

    private fun loadCachedAndPublish(item: SupabaseProfileBackgroundCatalogItem, portrait: Boolean) {
        val landscape = MemberAssetStorage.loadProfileBackground(
            "${item.id}-v${item.assetVersion}",
        )
        val bytes = if (portrait) {
            item.portraitStoragePath?.let {
                MemberAssetStorage.loadProfileBackground("${item.id}-portrait-v${item.assetVersion}")
            } ?: landscape
        } else {
            landscape
        } ?: return
        publishImage(item, portrait, bytes)
    }

    private suspend fun preload(items: List<SupabaseProfileBackgroundCatalogItem>, portrait: Boolean) {
        coroutineScope {
            items.map { item -> launch { loadAndPublish(item, portrait) } }.joinAll()
        }
    }

    private suspend fun loadAndPublish(item: SupabaseProfileBackgroundCatalogItem, portrait: Boolean) {
        try {
            val bytes = if (portrait) loadPortraitImage(item) else loadLandscapeImage(item)
            publishImage(item, portrait, bytes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w(error) { "Unable to load supporter profile background ${item.id}" }
        }
    }

    private fun publishImage(
        item: SupabaseProfileBackgroundCatalogItem,
        portrait: Boolean,
        bytes: ByteArray,
    ) {
        _catalog.update { catalog ->
            catalog.map { background ->
                if (background.id != item.id || background.assetVersion != item.assetVersion) {
                    background
                } else if (portrait) {
                    background.copy(portraitImageBytes = bytes)
                } else {
                    background.copy(landscapeImageBytes = bytes)
                }
            }
        }
    }

    private suspend fun fetchAnimeBackgrounds(): List<ProfileBackgroundCatalogItem> {
        val variables = ProfileBannerSources.indices.joinToString(separator = ", ") { "\$s$it: String" }
        val selections = ProfileBannerSources.indices.joinToString(separator = "\n") { index ->
            "b$index: Media(search: \$s$index, type: ANIME) { id bannerImage }"
        }
        val query = "query ($variables) {\n$selections\n}"
        val variablesJson = kotlinx.serialization.json.buildJsonObject {
            ProfileBannerSources.forEachIndexed { index, source -> put("s$index", source.searchTerm) }
        }
        val data = AniListClient.query(query, variablesJson)
        return ProfileBannerSources.mapIndexedNotNull { index, source ->
            val media = data["b$index"] as? JsonObject ?: return@mapIndexedNotNull null
            val imageUrl = media["bannerImage"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            val mediaId = media["id"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            ProfileBackgroundCatalogItem(
                id = "anilist-banner-$mediaId",
                displayName = source.displayName,
                landscapeImageUrl = imageUrl,
                assetVersion = 1,
            )
        }
    }

    private suspend fun loadPortraitImage(item: SupabaseProfileBackgroundCatalogItem): ByteArray {
        val portraitPath = item.portraitStoragePath ?: return loadLandscapeImage(item)
        return try {
            loadImage(
                cacheKey = "${item.id}-portrait-v${item.assetVersion}",
                storagePath = portraitPath,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w(error) { "Unable to load portrait background ${item.id}" }
            loadLandscapeImage(item)
        }
    }

    private suspend fun loadLandscapeImage(item: SupabaseProfileBackgroundCatalogItem): ByteArray =
        loadImage(
            cacheKey = "${item.id}-v${item.assetVersion}",
            storagePath = item.storagePath,
        )

    private suspend fun loadImage(cacheKey: String, storagePath: String): ByteArray =
        MemberAssetStorage.loadProfileBackground(cacheKey)
            ?: SupabaseProvider.client.storage[ProfileBackgroundBucket]
                .downloadAuthenticated(storagePath)
                .also { MemberAssetStorage.saveProfileBackground(cacheKey, it) }
}

private data class ProfileBannerSource(val displayName: String, val searchTerm: String)

/** A deliberately varied set of anime backdrops, resolved live so the art stays official and sharp. */
private val ProfileBannerSources = listOf(
    ProfileBannerSource("Frieren", "Sousou no Frieren"),
    ProfileBannerSource("The Apothecary Diaries", "Kusuriya no Hitorigoto"),
    ProfileBannerSource("Solo Leveling", "Ore dake Level Up na Ken"),
    ProfileBannerSource("Jujutsu Kaisen", "Jujutsu Kaisen"),
    ProfileBannerSource("Demon Slayer", "Kimetsu no Yaiba"),
    ProfileBannerSource("SPY x FAMILY", "SPY x FAMILY"),
    ProfileBannerSource("Chainsaw Man", "Chainsaw Man"),
    ProfileBannerSource("Attack on Titan", "Shingeki no Kyojin"),
    ProfileBannerSource("Bleach", "Bleach"),
    ProfileBannerSource("Mushoku Tensei", "Mushoku Tensei"),
)

@Serializable
private data class StoredProfileBackgroundCatalogPayload(
    val items: List<SupabaseProfileBackgroundCatalogItem> = emptyList(),
)

@Serializable
private data class SupabaseProfileBackgroundCatalogItem(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("portrait_storage_path") val portraitStoragePath: String? = null,
    @SerialName("asset_version") val assetVersion: Int,
)
