package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SameServerAutoPlayTest {
    private fun server(number: Int, addon: String = "plugin:anime") = StreamItem(
        addonId = addon, addonName = "Anime", name = "Server $number", url = "https://example.com/episode/$number.m3u8",
    )
    private fun select(streams: List<StreamItem>, key: String?) = StreamAutoPlaySelector.selectAutoPlayStream(
        streams = streams, mode = StreamAutoPlayMode.FIRST_STREAM, regexPattern = "",
        source = StreamAutoPlaySource.ALL_SOURCES, installedAddonNames = emptySet(),
        selectedAddons = emptySet(), selectedPlugins = emptySet(), preferredServerKey = key,
    )

    @Test fun preservesThirdServerWithinTheSameProvider() {
        val third = server(3)
        assertEquals(third, select(listOf(server(1), server(2), third), third.serverPreferenceKey()))
    }

    @Test fun missingServerRequiresManualSelection() {
        assertNull(select(listOf(server(1), server(2)), server(3).serverPreferenceKey()))
    }

    @Test fun matchingLabelFromAnotherProviderDoesNotWin() {
        val desired = server(3)
        assertEquals(desired, select(listOf(server(3, "plugin:other"), desired), desired.serverPreferenceKey()))
    }

    @Test fun newEpisodeUrlDoesNotChangeServerIdentity() {
        val previous = server(3)
        val next = previous.copy(url = "https://example.com/new-episode.m3u8")
        assertEquals(next, select(listOf(server(1), next), previous.serverPreferenceKey()))
    }

    @Test fun disablingPreferenceKeepsNormalAutoplay() {
        val first = server(1)
        assertEquals(first, select(listOf(first, server(3)), null))
    }
}
