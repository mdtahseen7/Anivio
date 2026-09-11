package com.nuvio.app.features.tracking

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackingLibrarySemanticStatusTest {
    @Test
    fun `selecting status mirrors equivalent semantic status to other provider`() {
        val tabs = tabs()
        val result = toggleTrackingLibraryMembership(
            tabs = tabs,
            membership = tabs.associate { it.key to false },
            key = "anilist:paused",
        )

        assertTrue(result.getValue("anilist:paused"))
        assertTrue(result.getValue("mal:on_hold"))
        assertFalse(result.getValue("mal:watching"))
    }

    @Test
    fun `clearing status clears equivalent status without touching different status`() {
        val tabs = tabs()
        val result = toggleTrackingLibraryMembership(
            tabs = tabs,
            membership = mapOf(
                "anilist:paused" to true,
                "mal:on_hold" to true,
                "mal:watching" to false,
            ),
            key = "anilist:paused",
        )

        assertFalse(result.getValue("anilist:paused"))
        assertFalse(result.getValue("mal:on_hold"))
        assertFalse(result.getValue("mal:watching"))
    }

    private fun tabs() = listOf(
        TrackingLibraryTab(
            key = "anilist:paused",
            title = "Paused",
            providerId = TrackingProviderId.ANILIST,
            kind = TrackingLibraryTabKind.STATUS,
            selectionGroup = "anilist:status",
            semanticStatus = TrackingListStatus.ON_HOLD,
        ),
        TrackingLibraryTab(
            key = "mal:on_hold",
            title = "On Hold",
            providerId = TrackingProviderId.MAL,
            kind = TrackingLibraryTabKind.STATUS,
            selectionGroup = "mal:status",
            semanticStatus = TrackingListStatus.ON_HOLD,
        ),
        TrackingLibraryTab(
            key = "mal:watching",
            title = "Watching",
            providerId = TrackingProviderId.MAL,
            kind = TrackingLibraryTabKind.STATUS,
            selectionGroup = "mal:status",
            semanticStatus = TrackingListStatus.WATCHING,
        ),
    )
}
