package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.anime.AnimeDataSourcePreference
import com.nuvio.app.features.cloudflare.CfCaptchaPickerSheet
import com.nuvio.app.features.cloudflare.CfClearanceRepository
import com.nuvio.app.features.cloudflare.buildCfCaptchaProviders
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.plugins.PluginRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.anilist_source_name
import nuvio.composeapp.generated.resources.anime_source_anilist_description
import nuvio.composeapp.generated.resources.anime_source_auto
import nuvio.composeapp.generated.resources.anime_source_auto_description
import nuvio.composeapp.generated.resources.anime_source_mal_description
import nuvio.composeapp.generated.resources.anime_source_mal_unavailable
import nuvio.composeapp.generated.resources.settings_anime_source_dialog_subtitle
import nuvio.composeapp.generated.resources.settings_anime_source_title
import nuvio.composeapp.generated.resources.tracking_source_mal
import nuvio.composeapp.generated.resources.settings_content_discovery_adult
import nuvio.composeapp.generated.resources.settings_content_discovery_adult_description
import nuvio.composeapp.generated.resources.settings_content_discovery_section_content
import nuvio.composeapp.generated.resources.compose_settings_page_addons
import nuvio.composeapp.generated.resources.compose_settings_page_plugins
import nuvio.composeapp.generated.resources.settings_content_discovery_addons_description
import nuvio.composeapp.generated.resources.settings_content_discovery_addons_description_appstore
import nuvio.composeapp.generated.resources.settings_content_discovery_plugins_description
import nuvio.composeapp.generated.resources.settings_content_discovery_section_sources
import nuvio.composeapp.generated.resources.settings_cloudflare_section
import nuvio.composeapp.generated.resources.settings_cloudflare_enable
import nuvio.composeapp.generated.resources.settings_cloudflare_enable_description
import nuvio.composeapp.generated.resources.settings_cloudflare_clear
import nuvio.composeapp.generated.resources.settings_cloudflare_clear_pending
import nuvio.composeapp.generated.resources.settings_cloudflare_clear_idle
import nuvio.composeapp.generated.resources.settings_cloudflare_reset
import nuvio.composeapp.generated.resources.settings_cloudflare_reset_description
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.contentDiscoveryContent(
    isTablet: Boolean,
    showPluginsEntry: Boolean,
    onAddonsClick: () -> Unit,
    onPluginsClick: () -> Unit,
) {
    item {
        // Collected here rather than threaded in as a parameter: this is the only row that needs it,
        // and the page is reached from four separate call sites.
        val catalogSettings by HomeCatalogSettingsRepository.uiState.collectAsStateWithLifecycle()
        var showSourcePicker by remember { mutableStateOf(false) }
        SettingsSection(
            title = stringResource(Res.string.settings_content_discovery_section_content),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_anime_source_title),
                    description = animeDataSourceLabel(catalogSettings.animeDataSource),
                    isTablet = isTablet,
                    onClick = { showSourcePicker = true },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_content_discovery_adult),
                    description = stringResource(Res.string.settings_content_discovery_adult_description),
                    checked = catalogSettings.adultContentEnabled,
                    isTablet = isTablet,
                    onCheckedChange = HomeCatalogSettingsRepository::setAdultContentEnabled,
                )
            }
        }

        if (showSourcePicker) {
            TrackingAdaptivePicker(
                isTablet = isTablet,
                title = stringResource(Res.string.settings_anime_source_title),
                subtitle = stringResource(Res.string.settings_anime_source_dialog_subtitle),
                selectedValue = catalogSettings.animeDataSource,
                options = animeDataSourceOptions(malAvailable = catalogSettings.malSourceAvailable),
                onSelected = HomeCatalogSettingsRepository::setAnimeDataSource,
                onDismiss = { showSourcePicker = false },
            )
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_content_discovery_section_sources),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_addons),
                    description = stringResource(
                        if (AppFeaturePolicy.personalMediaAddonCopyEnabled) {
                            Res.string.settings_content_discovery_addons_description_appstore
                        } else {
                            Res.string.settings_content_discovery_addons_description
                        },
                    ),
                    isTablet = isTablet,
                    onClick = onAddonsClick,
                )
                if (showPluginsEntry) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_plugins),
                        description = stringResource(Res.string.settings_content_discovery_plugins_description),
                        isTablet = isTablet,
                        onClick = onPluginsClick,
                    )
                }
            }
        }
    }

    if (showPluginsEntry) {
        item { CloudflareClearanceGroup(isTablet = isTablet) }
    }
}

@Composable
private fun CloudflareClearanceGroup(isTablet: Boolean) {
    val cfState by CfClearanceRepository.uiState.collectAsStateWithLifecycle()
    val pluginsState by PluginRepository.uiState.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(Res.string.settings_cloudflare_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_cloudflare_enable),
                description = stringResource(Res.string.settings_cloudflare_enable_description),
                checked = cfState.enabled,
                isTablet = isTablet,
                onCheckedChange = CfClearanceRepository::setEnabled,
            )
            if (cfState.enabled) {
                SettingsGroupDivider(isTablet = isTablet)
                val pending = cfState.challengeHosts.firstOrNull()
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_cloudflare_clear),
                    description = if (pending != null) {
                        stringResource(Res.string.settings_cloudflare_clear_pending, pending)
                    } else {
                        stringResource(Res.string.settings_cloudflare_clear_idle)
                    },
                    isTablet = isTablet,
                    onClick = { showPicker = true },
                )
                if (cfState.challengeHosts.isNotEmpty()) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_cloudflare_reset),
                        description = stringResource(Res.string.settings_cloudflare_reset_description),
                        isTablet = isTablet,
                        onClick = { CfClearanceRepository.clearAll() },
                    )
                }
            }
        }
    }

    if (showPicker) {
        CfCaptchaPickerSheet(
            providers = buildCfCaptchaProviders(
                scrapers = pluginsState.scrapers,
                challengeHosts = cfState.challengeHosts,
            ),
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun animeDataSourceLabel(source: AnimeDataSourcePreference): String = when (source) {
    AnimeDataSourcePreference.AUTO -> stringResource(Res.string.anime_source_auto)
    AnimeDataSourcePreference.ANILIST -> stringResource(Res.string.anilist_source_name)
    AnimeDataSourcePreference.MAL -> stringResource(Res.string.tracking_source_mal)
}

@Composable
private fun animeDataSourceOptions(
    malAvailable: Boolean,
): List<TrackingPickerOption<AnimeDataSourcePreference>> = listOf(
    TrackingPickerOption(
        value = AnimeDataSourcePreference.AUTO,
        title = stringResource(Res.string.anime_source_auto),
        description = stringResource(Res.string.anime_source_auto_description),
    ),
    TrackingPickerOption(
        value = AnimeDataSourcePreference.ANILIST,
        title = stringResource(Res.string.anilist_source_name),
        description = stringResource(Res.string.anime_source_anilist_description),
    ),
    TrackingPickerOption(
        value = AnimeDataSourcePreference.MAL,
        title = stringResource(Res.string.tracking_source_mal),
        description = stringResource(Res.string.anime_source_mal_description),
        enabled = malAvailable,
        unavailableReason = stringResource(Res.string.anime_source_mal_unavailable)
            .takeIf { !malAvailable },
    ),
)
