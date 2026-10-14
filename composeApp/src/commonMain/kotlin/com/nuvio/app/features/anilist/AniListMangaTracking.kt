package com.nuvio.app.features.anilist

/**
 * Records completed manga chapters on AniList.
 *
 * AniList stores manga progress as a single whole-chapter counter. Decimal and special chapters
 * therefore advance only to their completed whole chapter; this never claims that a later chapter
 * has been read.
 */
suspend fun recordAniListMangaChapterRead(
    contentId: String,
    chapterNumber: String,
): Boolean {
    val mediaId = aniListMediaIdOf(contentId) ?: return false
    val completedChapter = chapterNumber.toDoubleOrNull()?.toInt()?.takeIf { it > 0 } ?: return false
    val existing = AniListListRepository.snapshot().manga
        .firstOrNull { it.media?.id == mediaId }
        ?.progress
        ?: 0
    if (completedChapter <= existing) return true
    if (!AniListMutations.setProgress(mediaId, completedChapter)) return false
    AniListListRepository.refresh(forceRefresh = true)
    return true
}
