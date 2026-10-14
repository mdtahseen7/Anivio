package com.nuvio.app.features.content

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The browsing surface currently shown by Home, Discover and Search. Library remains independent. */
enum class ContentMode { Anime, Manga }

object ContentModeRepository {
    private val _mode = MutableStateFlow(ContentMode.Anime)
    val mode: StateFlow<ContentMode> = _mode.asStateFlow()

    fun select(mode: ContentMode) { _mode.value = mode }
}
