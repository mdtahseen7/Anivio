package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.profile.ProfileShareSettingsRepository
import com.nuvio.app.features.profile.ShareCardSettings

internal fun LazyListScope.profileSharingSettingsContent(isTablet: Boolean) {
    item {
        val settings by remember {
            ProfileShareSettingsRepository.ensureLoaded()
            ProfileShareSettingsRepository.settings
        }.collectAsStateWithLifecycle()
        ShareCardSettings(
            settings = settings,
            onSettingsChange = ProfileShareSettingsRepository::update,
        )
    }
}
