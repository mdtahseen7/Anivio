package com.nuvio.app.features.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AvatarAccessTest {
    @Test
    fun supporterAvatarUsesAuthenticatedLocalAsset() {
        val supporter = AvatarCatalogItem(
            id = "supporter-gold",
            storagePath = "private/gold.png",
            localImageUrl = "file:///cache/supporter-gold.png",
            memberOnly = true,
        )

        assertEquals("file:///cache/supporter-gold.png", avatarImageUrl(supporter))
        assertNull(avatarImageUrl(supporter.copy(localImageUrl = null)))
    }
}
