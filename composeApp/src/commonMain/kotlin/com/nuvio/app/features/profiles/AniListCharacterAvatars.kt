package com.nuvio.app.features.profiles

import co.touchlab.kermit.Logger
import com.nuvio.app.core.anilist.AniListClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Kept small so one show never floods the picker and pushes the other seven off-screen. */
private const val CharactersPerAnime = 12

/** Prefix that keeps a persisted `avatarId` self-describing and distinct from older catalogue ids. */
private const val CharacterIdPrefix = "anilist-character:"

private val log = Logger.withTag("AniListCharacterAvatars")

private data class AvatarAnimeSource(val displayLabel: String, val searchTerm: String)

/**
 * Search terms are AniList's romaji titles rather than the labels shown in the picker, because
 * `Media(search:)` resolves romaji far more reliably than English localisations.
 */
private val AvatarAnimeSources = listOf(
    AvatarAnimeSource("Rent-a-Girlfriend", "Kanojo, Okarishimasu"),
    AvatarAnimeSource("The Apothecary Diaries", "Kusuriya no Hitorigoto"),
    AvatarAnimeSource("Mushoku Tensei", "Mushoku Tensei"),
    AvatarAnimeSource("Re:Zero", "Re:Zero kara Hajimeru Isekai Seikatsu"),
    AvatarAnimeSource("Bleach", "Bleach"),
    AvatarAnimeSource("Sword Art Online", "Sword Art Online"),
    AvatarAnimeSource("Fate", "Fate/stay night"),
    AvatarAnimeSource("Date A Live", "Date A Live"),
)

/**
 * The intended group order for the picker, exposed so a cached catalogue can be restored into it
 * instead of falling back to an alphabetical order that would scramble the curation.
 */
internal val aniListAvatarAnimeLabels: List<String> = AvatarAnimeSources.map { it.displayLabel }

private data class AniListCharacter(val id: Int, val name: String, val imageUrl: String)

/**
 * Fetches the character catalogue, grouped by show via [AvatarCatalogItem.category].
 *
 * Every show travels in one aliased query: AniList allows 30 requests/minute, so eight separate
 * searches would spend a quarter of that budget on what a user perceives as one screen opening.
 *
 * Returns an empty list on failure — a picker with no choices is a degraded screen, not a reason to
 * fail profile editing.
 */
internal suspend fun fetchAniListCharacterAvatars(forceRefresh: Boolean = false): List<AvatarCatalogItem> =
    try {
        val data = AniListClient.query(
            query = charactersQuery(),
            variables = searchVariables(),
            forceRefresh = forceRefresh,
        )

        // A character can be credited across several entries of the same franchise; keep the first
        // sighting so the picker never shows the same face twice.
        val seenCharacterIds = mutableSetOf<Int>()
        buildList {
            AvatarAnimeSources.forEachIndexed { index, anime ->
                data.characterNodes(aliasFor(index))
                    .mapNotNull { it.toCharacterOrNull() }
                    .filter { seenCharacterIds.add(it.id) }
                    .forEachIndexed { position, character ->
                        add(character.toAvatarCatalogItem(anime.displayLabel, position))
                    }
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        log.w(error) { "Unable to load AniList character avatars" }
        emptyList()
    }

private fun aliasFor(index: Int): String = "a$index"

/**
 * Titles here contain commas, colons and slashes, so search terms travel as GraphQL variables
 * rather than being interpolated into the document where they would need escaping.
 */
private fun charactersQuery(): String {
    val parameters = AvatarAnimeSources.indices.joinToString(separator = ", ") { "${'$'}s$it: String" }
    val selections = AvatarAnimeSources.indices.joinToString(separator = "\n") { index ->
        """
        ${aliasFor(index)}: Media(search: ${'$'}s$index, type: ANIME) {
            id
            characters(sort: [ROLE, FAVOURITES_DESC], page: 1, perPage: $CharactersPerAnime) {
                nodes { id name { full } image { large medium } }
            }
        }
        """.trimIndent()
    }
    return "query ($parameters) {\n$selections\n}"
}

private fun searchVariables(): JsonObject = buildJsonObject {
    AvatarAnimeSources.forEachIndexed { index, anime -> put("s$index", anime.searchTerm) }
}

private fun JsonObject.characterNodes(alias: String): List<JsonObject> =
    ((this[alias] as? JsonObject)?.get("characters") as? JsonObject)
        ?.let { it["nodes"] as? JsonArray }
        ?.mapNotNull { it as? JsonObject }
        .orEmpty()

/** Skips anything unusable: a nameless entry or one with no artwork cannot be shown as an avatar. */
private fun JsonObject.toCharacterOrNull(): AniListCharacter? {
    val id = (this["id"] as? JsonPrimitive)?.intOrNull ?: return null
    val name = ((this["name"] as? JsonObject)?.get("full") as? JsonPrimitive)?.nonBlankContent()
        ?: return null
    val image = this["image"] as? JsonObject
    val imageUrl = (image?.get("large") as? JsonPrimitive)?.nonBlankContent()
        ?: (image?.get("medium") as? JsonPrimitive)?.nonBlankContent()
        ?: return null
    return AniListCharacter(id = id, name = name, imageUrl = imageUrl)
}

private fun JsonPrimitive.nonBlankContent(): String? =
    contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

/**
 * `storagePath` holds the absolute AniList CDN URL on purpose: [avatarStorageUrl] passes anything
 * already carrying a scheme straight through, so these render with no extra plumbing.
 */
private fun AniListCharacter.toAvatarCatalogItem(
    animeLabel: String,
    position: Int,
): AvatarCatalogItem = AvatarCatalogItem(
    id = "$CharacterIdPrefix$id",
    displayName = name,
    storagePath = imageUrl,
    category = animeLabel,
    // Position mirrors AniList's ROLE/FAVOURITES ordering, so leads land ahead of background cast.
    sortOrder = position,
)
