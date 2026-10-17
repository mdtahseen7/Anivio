package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioIconActionButton
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.plugins.AddPluginRepositoryResult
import com.nuvio.app.features.plugins.PluginRepository
import kotlinx.coroutines.launch

private enum class ContentSourceTab { INSTALLED, BROWSE }
private enum class ContentSourceFilter { ALL, ANIME, MANGA }

private data class MarketplaceEntry(
    val name: String,
    val description: String,
    val manifestUrl: String,
    val version: String,
    val category: ContentSourceFilter,
    val iconUrl: String?,
    val providerCount: Int,
)

private val marketplaceEntries = listOf(
    MarketplaceEntry(
        name = "Anivio Anime Providers",
        description = "Anime stream providers maintained for Anivio.",
        manifestUrl = "https://raw.githubusercontent.com/mdtahseen7/Anivio-providers/main/manifest.json",
        version = "1.9.4",
        category = ContentSourceFilter.ANIME,
        iconUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/crunchyroll.png",
        providerCount = 40,
    ),
    MarketplaceEntry(
        name = "Anivio Manga Providers",
        description = "Manga chapter and page providers for the Anivio reader.",
        manifestUrl = "https://raw.githubusercontent.com/mdtahseen7/Anivio-Manga-Providers/main/manifest.json",
        version = "1.1.0",
        category = ContentSourceFilter.MANGA,
        iconUrl = "https://mangadex.org/favicon.ico",
        providerCount = 6,
    ),
)

@Composable
fun ContentSourcesSettingsScreen(onBack: () -> Unit) {
    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        if (AppFeaturePolicy.pluginsEnabled) PluginRepository.initialize()
    }

    val addonState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val pluginState by PluginRepository.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ContentSourceTab.INSTALLED) }
    var filter by rememberSaveable { mutableStateOf(ContentSourceFilter.ALL) }

    NuvioScreen {
        stickyHeader {
            NuvioScreenHeader(
                title = "Content & Addons",
                onBack = onBack,
            )
        }
        item {
            ContentSourceTabs(selected = tab, onSelected = { tab = it })
        }
        item {
            ContentSourceFilters(selected = filter, onSelected = { filter = it })
        }
        if (tab == ContentSourceTab.INSTALLED) {
            item { NuvioSectionLabel("INSTALLED") }
            item {
                InstalledSummaryCard(
                    addonCount = addonState.addons.count { it.isActive },
                    pluginCount = if (AppFeaturePolicy.pluginsEnabled) pluginState.scrapers.count { it.enabled } else 0,
                )
            }
            item { NuvioSectionLabel("ADDONS") }
            addonsSettingsContent()
            if (AppFeaturePolicy.pluginsEnabled) {
                item { NuvioSectionLabel("PLUGINS") }
                pluginsSettingsContent()
            }
        } else {
            item {
                BrowseContent(
                    filter = filter,
                    pluginState = pluginState,
                    onInstall = { entry -> PluginRepository.addRepository(entry.manifestUrl) },
                    onUpdate = { entry ->
                        PluginRepository.refreshRepository(entry.manifestUrl, pushAfterRefresh = true)
                    },
                    showPlugins = AppFeaturePolicy.pluginsEnabled,
                )
            }
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
                    text = tab.name.lowercase().replaceFirstChar(Char::uppercase),
                    modifier = Modifier.padding(vertical = 11.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ContentSourceFilters(
    selected: ContentSourceFilter,
    onSelected: (ContentSourceFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ContentSourceFilter.entries.forEach { filter ->
            Surface(
                modifier = Modifier.clickable { onSelected(filter) },
                shape = RoundedCornerShape(50),
                color = if (selected == filter) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = filter.name.lowercase().replaceFirstChar(Char::uppercase),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun InstalledSummaryCard(addonCount: Int, pluginCount: Int) {
    NuvioSurfaceCard {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text("$addonCount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Active addons", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                Text("$pluginCount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Enabled plugins", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BrowseContent(
    filter: ContentSourceFilter,
    pluginState: com.nuvio.app.features.plugins.PluginsUiState,
    onInstall: suspend (MarketplaceEntry) -> AddPluginRepositoryResult,
    onUpdate: (MarketplaceEntry) -> Unit,
    showPlugins: Boolean,
) {
    val scope = rememberCoroutineScope()
    var customUrl by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingInstall by remember { mutableStateOf<MarketplaceEntry?>(null) }
    val installedUrls = remember(pluginState.repositories) { pluginState.repositories.map { it.manifestUrl }.toSet() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NuvioSectionLabel("ANIVIO MARKETPLACE")
        if (!showPlugins) {
            NuvioSurfaceCard { Text("Plugin installation is unavailable in this app build.") }
        }
        marketplaceEntries
            .filter { filter == ContentSourceFilter.ALL || it.category == filter }
            .forEach { entry ->
                MarketplaceCard(
                    entry = entry,
                    installed = entry.manifestUrl in installedUrls,
                    onInstall = { pendingInstall = entry },
                    onUpdate = { onUpdate(entry) },
                )
            }
        NuvioSectionLabel("ADD YOUR OWN")
        NuvioSurfaceCard {
            Text("Install a plugin repository from its manifest URL.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            NuvioInputField(
                value = customUrl,
                onValueChange = { customUrl = it; message = null },
                placeholder = "https://example.com/manifest.json",
            )
            Spacer(Modifier.height(10.dp))
            NuvioPrimaryButton(
                text = "Install custom plugin",
                enabled = customUrl.isNotBlank() && showPlugins,
                onClick = {
                    val url = customUrl.trim()
                    scope.launch {
                        when (val result = PluginRepository.addRepository(url)) {
                            is AddPluginRepositoryResult.Success -> message = "Installed ${result.repository.name}"
                            is AddPluginRepositoryResult.Error -> message = result.message
                        }
                    }
                },
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    pendingInstall?.let { entry ->
        NuvioStatusModal(
            title = "Review plugin installation",
            message = "${entry.name}\nVersion ${entry.version}\n${entry.providerCount} provider scripts\n\nThis plugin executes JavaScript inside Anivio and may contact provider websites. Review the source URL before installing:\n${entry.manifestUrl}",
            isVisible = true,
            confirmText = "Install",
            dismissText = "Cancel",
            onConfirm = {
                scope.launch {
                    val result = onInstall(entry)
                    message = when (result) {
                        is AddPluginRepositoryResult.Success -> "Installed ${result.repository.name}"
                        is AddPluginRepositoryResult.Error -> result.message
                    }
                    pendingInstall = null
                }
            },
            onDismiss = { pendingInstall = null },
        )
    }
}

@Composable
private fun MarketplaceCard(
    entry: MarketplaceEntry,
    installed: Boolean,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
) {
    NuvioSurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoxedIcon(entry.iconUrl)
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text("v${entry.version} • ${entry.providerCount} providers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (installed) {
                NuvioIconActionButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Update",
                    onClick = onUpdate,
                )
                Icon(Icons.Rounded.Check, contentDescription = "Installed", tint = MaterialTheme.colorScheme.primary)
            } else {
                NuvioIconActionButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Install",
                    onClick = onInstall,
                )
            }
        }
    }
}

@Composable
private fun BoxedIcon(url: String?) {
    Surface(
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (url == null) {
            Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.padding(14.dp))
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
