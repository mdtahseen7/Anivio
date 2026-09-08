package com.nuvio.app.core.anilist

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape

/** The one AniList `format` that is a film rather than an episodic release. */
private const val MOVIE_FORMAT = "MOVIE"

private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val LINE_BREAK_REGEX = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val EXCESS_NEWLINE_REGEX = Regex("\n{3,}")

/**
 * The app's content type for this title. AniList models anime films as `format: MOVIE`; everything
 * else (TV, ONA, OVA, SPECIAL, …) is episodic and maps to `series`.
 */
fun AniListMedia.aniListContentType(): String =
    if (format?.uppercase() == MOVIE_FORMAT) "movie" else "series"

/** Stable app-wide id for this title. Episodes append `:1:<episode>`. */
fun AniListMedia.aniListMetaId(): String = "anilist:$id"

fun AniListMedia.displayTitle(): String? =
    title?.english?.takeIf { it.isNotBlank() }
        ?: title?.romaji?.takeIf { it.isNotBlank() }
        ?: title?.nativeTitle?.takeIf { it.isNotBlank() }

/** Returns null for entries too incomplete to show — no title, or no artwork to render. */
fun AniListMedia.toMetaPreview(): MetaPreview? {
    val name = displayTitle() ?: return null
    val poster = coverImage?.extraLarge?.takeIf { it.isNotBlank() }
        ?: coverImage?.large?.takeIf { it.isNotBlank() }
        ?: return null

    return MetaPreview(
        id = aniListMetaId(),
        type = aniListContentType(),
        name = name,
        poster = poster,
        banner = bannerImage?.takeIf { it.isNotBlank() },
        posterShape = PosterShape.Poster,
        description = description?.stripAniListMarkup(),
        releaseInfo = (seasonYear ?: startDate?.year)?.toString(),
        // Left null when AniList's date is too fuzzy to be a real one, so the unreleased filter
        // falls back to comparing releaseInfo's year instead of treating it as aired.
        rawReleaseDate = startDate?.toIsoDateOrNull(),
        popularity = popularity?.toDouble(),
        imdbRating = averageScore?.takeIf { it > 0 }?.let { (it / 10.0).toString() },
        genres = genres,
    )
}

/**
 * AniList descriptions still carry `<br>` and inline tags even with `asHtml: false`, plus HTML
 * entities. Flatten them to plain text.
 */
fun String.stripAniListMarkup(): String? =
    replace(LINE_BREAK_REGEX, "\n")
        .replace(HTML_TAG_REGEX, "")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace(EXCESS_NEWLINE_REGEX, "\n\n")
        .trim()
        .takeIf { it.isNotEmpty() }
