package com.nuvio.app.features.details.components

import com.nuvio.app.features.plugins.MangaPluginChapter

internal const val CHAPTER_RANGE_SIZE = 10

internal data class ChapterRange(val start: Int, val end: Int) {
    val label: String get() = "$start – $end"
}

internal fun chapterNumberOf(chapter: MangaPluginChapter): Double? =
    chapter.number.trim().toDoubleOrNull()

/**
 * Groups chapters into 1–10, 11–20, … buckets by chapter number (ascending).
 * Each bucket's chapters are newest-first for display. Shared by the manga
 * details section and the reader's chapter picker so both page identically.
 */
internal fun buildChapterRanges(chapters: List<MangaPluginChapter>): List<Pair<ChapterRange, List<MangaPluginChapter>>> {
    if (chapters.isEmpty()) return emptyList()
    val numeric = chapters.mapNotNull { chapter ->
        chapterNumberOf(chapter)?.let { chapter to it }
    }.sortedBy { (_, number) -> number }
    val nonNumeric = chapters.filter { chapterNumberOf(it) == null }
    val buckets = sortedMapOf<Int, MutableList<MangaPluginChapter>>()
    numeric.forEach { (chapter, number) ->
        val bucket = (((number - 1) / CHAPTER_RANGE_SIZE).toInt()).coerceAtLeast(0)
        buckets.getOrPut(bucket) { mutableListOf() }.add(chapter)
    }
    if (nonNumeric.isNotEmpty()) {
        val lastBucket = buckets.keys.maxOrNull() ?: 0
        buckets.getOrPut(lastBucket) { mutableListOf() }.addAll(nonNumeric)
    }
    return buckets.map { (bucket, bucketChapters) ->
        val start = bucket * CHAPTER_RANGE_SIZE + 1
        val end = (bucket + 1) * CHAPTER_RANGE_SIZE
        ChapterRange(start, end) to bucketChapters.sortedByDescending { chapterNumberOf(it) ?: Double.MIN_VALUE }
    }
}

/** Sorts chapters oldest-first; used for reader prev/next navigation. */
internal fun sortChaptersAscending(chapters: List<MangaPluginChapter>): List<MangaPluginChapter> =
    chapters.sortedBy { chapterNumberOf(it) ?: Double.MAX_VALUE }
