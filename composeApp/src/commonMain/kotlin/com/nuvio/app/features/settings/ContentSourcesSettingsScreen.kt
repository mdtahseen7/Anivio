package com.nuvio.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.plugins.PluginRepository

private enum class ContentSourceTab { PLUGINS, ADDONS }

@Composable
fun ContentSourcesSettingsScreen(onBack: () -> Unit, startOnAddons: Boolean = false) {
    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        if (AppFeaturePolicy.pluginsEnabled) PluginRepository.initialize()
    }

    var tab by rememberSaveable {
        mutableStateOf(
            if (startOnAddons || !AppFeaturePolicy.pluginsEnabled) ContentSourceTab.ADDONS
            else ContentSourceTab.PLUGINS,
        )
    }

    NuvioScreen {
        stickyHeader {
            NuvioScreenHeader(
                title = "Content sources",
                onBack = onBack,
            )
        }
        if (AppFeaturePolicy.pluginsEnabled) {
            item { ContentSourceTabs(selected = tab, onSelected = { tab = it }) }
        }
        if (tab == ContentSourceTab.PLUGINS && AppFeaturePolicy.pluginsEnabled) {
            pluginsSettingsContent()
        } else {
            addonsSettingsContent()
        }
    }
}

@Composable
private fun ContentSourceTabs(
    selected: ContentSourceTab,
    onSelected: (ContentSourceTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ContentSourceTab.entries.forEach { tab ->
            Surface(
                modifier = Modifier.weight(1f).clickable { onSelected(tab) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected == tab) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = when (tab) {
                        ContentSourceTab.PLUGINS -> "Providers"
                        ContentSourceTab.ADDONS -> "Add-ons"
                    },
                    modifier = Modifier.padding(vertical = 11.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
