package com.nuvio.app.features.player

import kotlinx.serialization.Serializable

@Serializable
data class PlayerChapter(val title: String, val positionMs: Long)
