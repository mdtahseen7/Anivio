package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.VisibilityOff
import com.nuvio.app.core.ui.NuvioToastController
import nuvio.composeapp.generated.resources.settings_incognito_title
import nuvio.composeapp.generated.resources.settings_incognito_description
import nuvio.composeapp.generated.resources.settings_incognito_on
import nuvio.composeapp.generated.resources.settings_incognito_off
import nuvio.composeapp.generated.resources.compose_settings_root_downloads_title
import nuvio.composeapp.generated.resources.compose_settings_root_downloads_description
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import com.nuvio.app.features.party.PartyRepository
import nuvio.composeapp.generated.resources.party_settings_description
import nuvio.composeapp.generated.resources.party_title
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.nuvio.app.core.build.AppVersionConfig
import nuvio.composeapp.generated.resources.settings_page_layout
import nuvio.composeapp.generated.resources.settings_page_layout_description
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.compose_settings_page_advanced
import nuvio.composeapp.generated.resources.compose_settings_page_appearance
import nuvio.composeapp.generated.resources.compose_settings_page_integrations
import nuvio.composeapp.generated.resources.compose_settings_page_licenses_attributions
import nuvio.composeapp.generated.resources.compose_settings_page_notifications
import nuvio.composeapp.generated.resources.compose_settings_page_playback
import nuvio.composeapp.generated.resources.compose_settings_page_privacy_policy
import nuvio.composeapp.generated.resources.compose_settings_page_about
import nuvio.composeapp.generated.resources.compose_settings_root_account_description
import nuvio.composeapp.generated.resources.compose_settings_root_appearance_description
import nuvio.composeapp.generated.resources.compose_settings_root_check_updates_description
import nuvio.composeapp.generated.resources.compose_settings_root_check_updates_title
import nuvio.composeapp.generated.resources.compose_settings_root_content_discovery_description
import nuvio.composeapp.generated.resources.compose_settings_root_integrations_description
import nuvio.composeapp.generated.resources.compose_settings_root_notifications_description
import nuvio.composeapp.generated.resources.compose_settings_root_privacy_policy_description
import nuvio.composeapp.generated.resources.compose_settings_root_switch_profile_description
import nuvio.composeapp.generated.resources.compose_settings_root_switch_profile_title
import nuvio.composeapp.generated.resources.compose_settings_root_tracking_description
import nuvio.composeapp.generated.resources.compose_settings_root_about_section
import nuvio.composeapp.generated.resources.compose_settings_root_account_section
import nuvio.composeapp.generated.resources.compose_settings_root_advanced_description
import nuvio.composeapp.generated.resources.compose_settings_page_content_discovery
import nuvio.composeapp.generated.resources.compose_settings_page_tracking
import nuvio.composeapp.generated.resources.settings_playback_subtitle
import nuvio.composeapp.generated.resources.updates_debug_test_description
import nuvio.composeapp.generated.resources.updates_debug_test_title
import nuvio.composeapp.generated.resources.about_page_subtitle
import nuvio.composeapp.generated.resources.about_licenses_attributions_subtitle
import org.jetbrains.compose.resources.stringResource


internal fun LazyListScope.settingsRootContent(
    isTablet: Boolean,
    onPlaybackClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onLayoutClick: () -> Unit,
    onDownloadsClick: (() -> Unit)? = null,
    onWatchPartyClick: (() -> Unit)? = null,
    onAdvancedClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onContentDiscoveryClick: () -> Unit,
    onIntegrationsClick: () -> Unit,
    onTrackingClick: () -> Unit,
    onAboutClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onLicensesAttributionsClick: () -> Unit,
    onCheckForUpdatesClick: (() -> Unit)? = null,
    onTestUpdateBannerClick: (() -> Unit)? = null,
    onAccountClick: () -> Unit,
    onSwitchProfileClick: (() -> Unit)? = null,
    showNotificationsEntry: Boolean = true,
    showAccountSection: Boolean = true,
    showGeneralSection: Boolean = true,
    showAboutSection: Boolean = true,
    showAdvancedSection: Boolean = true,
    showAttribution: Boolean = true,
    showAboutPage: Boolean = true,
) {
    if (showAccountSection) {
        item {
            SettingsSection(
                title = stringResource(Res.string.compose_settings_root_account_section),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_account),
                        description = stringResource(Res.string.compose_settings_root_account_description),
                        icon = Icons.Rounded.AccountCircle,
                        isTablet = isTablet,
                        onClick = onAccountClick,
                    )
                    if (onSwitchProfileClick != null) {
                        SettingsGroupDivider(isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.compose_settings_root_switch_profile_title),
                            description = stringResource(Res.string.compose_settings_root_switch_profile_description),
                            icon = Icons.Rounded.People,
                            isTablet = isTablet,
                            onClick = onSwitchProfileClick,
                        )
                    }
                    if (onWatchPartyClick != null) {
                        val watchPartyAvailable by PartyRepository.available.collectAsStateWithLifecycle()
                        if (watchPartyAvailable) {
                            SettingsGroupDivider(isTablet)
                            SettingsNavigationRow(
                                title = stringResource(Res.string.party_title),
                                description = stringResource(Res.string.party_settings_description),
                                icon = Icons.Rounded.Groups,
                                isTablet = isTablet,
                                onClick = onWatchPartyClick,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showGeneralSection) {
        item {
            SettingsGroup(isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_appearance),
                    description = stringResource(Res.string.compose_settings_root_appearance_description),
                    icon = Icons.Rounded.Palette,
                    isTablet = isTablet,
                    onClick = onAppearanceClick,
                )
                SettingsGroupDivider(isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_page_layout),
                    description = stringResource(Res.string.settings_page_layout_description),
                    icon = Icons.Rounded.GridView,
                    isTablet = isTablet,
                    onClick = onLayoutClick,
                )
            }
        }
        item {
            SettingsGroup(isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_content_discovery),
                    description = stringResource(Res.string.compose_settings_root_content_discovery_description),
                    icon = Icons.Rounded.Extension,
                    isTablet = isTablet,
                    onClick = onContentDiscoveryClick,
                )
                SettingsGroupDivider(isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_playback),
                    description = stringResource(Res.string.settings_playback_subtitle),
                    icon = Icons.Rounded.PlayArrow,
                    isTablet = isTablet,
                    onClick = onPlaybackClick,
                )
            }
        }
        item {
            SettingsGroup(isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_integrations),
                    description = stringResource(Res.string.compose_settings_root_integrations_description),
                    icon = Icons.Rounded.Link,
                    isTablet = isTablet,
                    onClick = onIntegrationsClick,
                )
                SettingsGroupDivider(isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_tracking),
                    description = stringResource(Res.string.compose_settings_root_tracking_description),
                    icon = Icons.Default.Sync,
                    isTablet = isTablet,
                    onClick = onTrackingClick,
                )
            }
        }
        item {
            val incognitoEnabled by remember {
                IncognitoModeRepository.isEnabled()
                IncognitoModeRepository.enabled
            }.collectAsStateWithLifecycle()
            val enabledMessage = stringResource(Res.string.settings_incognito_on)
            val disabledMessage = stringResource(Res.string.settings_incognito_off)
            SettingsGroup(isTablet) {
                if (onDownloadsClick != null) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_root_downloads_title),
                        description = stringResource(Res.string.compose_settings_root_downloads_description),
                        icon = Icons.Rounded.CloudDownload,
                        isTablet = isTablet,
                        onClick = onDownloadsClick,
                    )
                    SettingsGroupDivider(isTablet)
                }
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_incognito_title),
                    description = stringResource(Res.string.settings_incognito_description),
                    icon = Icons.Rounded.VisibilityOff,
                    isTablet = isTablet,
                    checked = incognitoEnabled,
                    onCheckedChange = { enabled ->
                        IncognitoModeRepository.setEnabled(enabled)
                        NuvioToastController.show(if (enabled) enabledMessage else disabledMessage)
                    },
                )
            }
        }
    }
    if (showAdvancedSection) {
        item {
            SettingsGroup(isTablet) {
                if (showNotificationsEntry) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_notifications),
                        description = stringResource(Res.string.compose_settings_root_notifications_description),
                        icon = Icons.Rounded.Notifications,
                        isTablet = isTablet,
                        onClick = onNotificationsClick,
                    )
                    SettingsGroupDivider(isTablet)
                }
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_advanced),
                    description = stringResource(Res.string.compose_settings_root_advanced_description),
                    icon = Icons.Rounded.Tune,
                    isTablet = isTablet,
                    onClick = onAdvancedClick,
                )
            }
        }
    }
    if (showAboutSection) {
        item {
            SettingsSection(
                title = stringResource(Res.string.compose_settings_root_about_section),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    if (showAboutPage) {
                        SettingsNavigationRow(
                            title = stringResource(Res.string.compose_settings_page_about),
                            description = stringResource(Res.string.about_page_subtitle),
                            icon = Icons.Rounded.Info,
                            isTablet = isTablet,
                            onClick = onAboutClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                    }
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_privacy_policy),
                        description = stringResource(Res.string.compose_settings_root_privacy_policy_description),
                        icon = Icons.Rounded.Policy,
                        isTablet = isTablet,
                        onClick = onPrivacyPolicyClick,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_licenses_attributions),
                        description = stringResource(Res.string.about_licenses_attributions_subtitle),
                        icon = Icons.Rounded.Info,
                        isTablet = isTablet,
                        onClick = onLicensesAttributionsClick,
                    )
                    if (onCheckForUpdatesClick != null) {
                        SettingsGroupDivider(isTablet = isTablet)
                        UpdateChannelSettingsRow(isTablet = isTablet)
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.compose_settings_root_check_updates_title),
                            description = stringResource(Res.string.compose_settings_root_check_updates_description),
                            icon = Icons.Rounded.CloudDownload,
                            isTablet = isTablet,
                            onClick = onCheckForUpdatesClick,
                        )
                    }
                    if (onTestUpdateBannerClick != null) {
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.updates_debug_test_title),
                            description = stringResource(Res.string.updates_debug_test_description),
                            icon = Icons.Rounded.BugReport,
                            isTablet = isTablet,
                            onClick = onTestUpdateBannerClick,
                        )
                    }
                }
            }
        }
    }
    if (showAttribution) {
        item(key = "settings-attribution") {
            SettingsAttribution(isTablet = isTablet)
        }
    }
}
