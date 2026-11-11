package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.ui.NuvioToastController
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_about_made_with
import nuvio.composeapp.generated.resources.compose_about_version_format
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.compose_settings_page_advanced
import nuvio.composeapp.generated.resources.compose_settings_page_appearance
import nuvio.composeapp.generated.resources.compose_settings_page_integrations
import nuvio.composeapp.generated.resources.compose_settings_page_licenses_attributions
import nuvio.composeapp.generated.resources.compose_settings_page_notifications
import nuvio.composeapp.generated.resources.compose_settings_page_playback
import nuvio.composeapp.generated.resources.compose_settings_page_privacy_policy
import nuvio.composeapp.generated.resources.compose_settings_page_reader
import nuvio.composeapp.generated.resources.compose_settings_page_about
import nuvio.composeapp.generated.resources.compose_settings_root_account_description
import nuvio.composeapp.generated.resources.compose_settings_root_appearance_description
import nuvio.composeapp.generated.resources.compose_settings_root_check_updates_description
import nuvio.composeapp.generated.resources.compose_settings_root_check_updates_title
import nuvio.composeapp.generated.resources.compose_settings_root_content_discovery_description
import nuvio.composeapp.generated.resources.compose_settings_root_downloads_description
import nuvio.composeapp.generated.resources.compose_settings_root_downloads_title
import nuvio.composeapp.generated.resources.compose_settings_root_general_section
import nuvio.composeapp.generated.resources.compose_settings_root_integrations_description
import nuvio.composeapp.generated.resources.compose_settings_root_notifications_description
import nuvio.composeapp.generated.resources.settings_incognito_mode_title
import nuvio.composeapp.generated.resources.settings_incognito_mode_description
import nuvio.composeapp.generated.resources.settings_section_personalize
import nuvio.composeapp.generated.resources.settings_section_services
import nuvio.composeapp.generated.resources.settings_section_system
import nuvio.composeapp.generated.resources.settings_section_watch
import nuvio.composeapp.generated.resources.settings_section_you
import nuvio.composeapp.generated.resources.compose_settings_root_privacy_policy_description
import nuvio.composeapp.generated.resources.compose_settings_root_reader_description
import nuvio.composeapp.generated.resources.compose_settings_root_switch_profile_description
import nuvio.composeapp.generated.resources.compose_settings_root_switch_profile_title
import nuvio.composeapp.generated.resources.compose_settings_root_tracking_description
import nuvio.composeapp.generated.resources.compose_settings_root_about_section
import nuvio.composeapp.generated.resources.compose_settings_root_account_section
import nuvio.composeapp.generated.resources.compose_settings_root_advanced_description
import nuvio.composeapp.generated.resources.compose_settings_root_advanced_section
import nuvio.composeapp.generated.resources.compose_settings_page_content_discovery
import nuvio.composeapp.generated.resources.compose_settings_page_tracking
import nuvio.composeapp.generated.resources.party_settings_description
import nuvio.composeapp.generated.resources.party_title
import nuvio.composeapp.generated.resources.settings_playback_subtitle
import nuvio.composeapp.generated.resources.updates_debug_test_description
import nuvio.composeapp.generated.resources.updates_debug_test_title
import nuvio.composeapp.generated.resources.about_page_subtitle
import nuvio.composeapp.generated.resources.about_licenses_attributions_subtitle
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource


internal fun LazyListScope.settingsRootContent(
    isTablet: Boolean,
    sectionPage: SettingsPage = SettingsPage.Root,
    onSectionClick: (SettingsPage) -> Unit,
    onPlaybackClick: () -> Unit,
    onReaderClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onAdvancedClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    incognitoModeEnabled: Boolean = false,
    onIncognitoModeChanged: (Boolean) -> Unit = {},
    onContentDiscoveryClick: () -> Unit,
    onIntegrationsClick: () -> Unit,
    onTrackingClick: () -> Unit,
    onAboutClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onLicensesAttributionsClick: () -> Unit,
    onCheckForUpdatesClick: (() -> Unit)? = null,
    onTestUpdateBannerClick: (() -> Unit)? = null,
    onDownloadsClick: () -> Unit,
    onAccountClick: () -> Unit,
    onSwitchProfileClick: (() -> Unit)? = null,
    onWatchPartyClick: (() -> Unit)? = null,
    showAccountSection: Boolean = true,
    showGeneralSection: Boolean = true,
    showAboutSection: Boolean = true,
    showAdvancedSection: Boolean = true,
    showAboutPage: Boolean = true,
) {
    val rows = mutableListOf<SettingsHubRow>()
    fun row(title: StringResource, description: StringResource, icon: ImageVector, action: () -> Unit) {
        rows += SettingsHubRow(title, description, icon, action)
    }
    fun page(target: SettingsPage, description: StringResource, icon: ImageVector) {
        row(target.titleRes, description, icon) { onSectionClick(target) }
    }
    when (sectionPage) {
        SettingsPage.Root -> {
            if (showAccountSection) page(SettingsPage.YourSpace, Res.string.settings_section_you_description, Icons.Rounded.AccountCircle)
            if (showGeneralSection) {
                page(SettingsPage.Personalize, Res.string.settings_section_personalize_description, Icons.Rounded.Palette)
                page(SettingsPage.WatchSettings, Res.string.settings_section_watch_description, Icons.Rounded.PlayArrow)
                page(SettingsPage.Services, Res.string.settings_section_services_description, Icons.Rounded.Link)
                page(SettingsPage.Anivio, Res.string.settings_section_anivio_description, Icons.Rounded.MenuBook)
            }
            if (showAdvancedSection) page(SettingsPage.SystemSettings, Res.string.settings_section_system_description, Icons.Rounded.Tune)
            if (showAboutSection) page(SettingsPage.AboutSettings, Res.string.about_page_subtitle, Icons.Rounded.Info)
        }
        SettingsPage.YourSpace -> {
            row(Res.string.compose_settings_page_account, Res.string.compose_settings_root_account_description, Icons.Rounded.AccountCircle, onAccountClick)
            onSwitchProfileClick?.let { row(Res.string.compose_settings_root_switch_profile_title, Res.string.compose_settings_root_switch_profile_description, Icons.Rounded.People, it) }
        }
        SettingsPage.Personalize -> {
            row(Res.string.compose_settings_page_appearance, Res.string.compose_settings_root_appearance_description, Icons.Rounded.Palette, onAppearanceClick)
            page(SettingsPage.Layout, Res.string.settings_layout_description, Icons.Rounded.Tune)
        }
        SettingsPage.WatchSettings -> {
            row(Res.string.compose_settings_page_content_discovery, Res.string.compose_settings_root_content_discovery_description, Icons.Rounded.Extension, onContentDiscoveryClick)
            row(Res.string.compose_settings_page_playback, Res.string.settings_playback_subtitle, Icons.Rounded.PlayArrow, onPlaybackClick)
        }
        SettingsPage.Services -> {
            row(Res.string.compose_settings_page_integrations, Res.string.compose_settings_root_integrations_description, Icons.Rounded.Link, onIntegrationsClick)
            row(Res.string.compose_settings_page_tracking, Res.string.compose_settings_root_tracking_description, Icons.Default.Sync, onTrackingClick)
        }
        SettingsPage.Anivio -> {
            page(SettingsPage.ProfileCustomization, Res.string.settings_profile_customization_description, Icons.Rounded.Palette)
            page(SettingsPage.ProfileSharing, Res.string.settings_profile_sharing_description, Icons.Rounded.People)
            row(Res.string.compose_settings_page_reader, Res.string.compose_settings_root_reader_description, Icons.Rounded.MenuBook, onReaderClick)
            row(Res.string.compose_settings_root_downloads_title, Res.string.compose_settings_root_downloads_description, Icons.Rounded.CloudDownload, onDownloadsClick)
            onWatchPartyClick?.let { row(Res.string.party_title, Res.string.party_settings_description, Icons.Rounded.Groups, it) }
        }
        SettingsPage.SystemSettings -> {
            row(Res.string.compose_settings_page_notifications, Res.string.compose_settings_root_notifications_description, Icons.Rounded.Notifications, onNotificationsClick)
            row(Res.string.compose_settings_page_advanced, Res.string.compose_settings_root_advanced_description, Icons.Rounded.Tune, onAdvancedClick)
        }
        SettingsPage.AboutSettings -> {
            if (showAboutPage) row(Res.string.compose_settings_page_about, Res.string.about_page_subtitle, Icons.Rounded.Info, onAboutClick)
            row(Res.string.compose_settings_page_privacy_policy, Res.string.compose_settings_root_privacy_policy_description, Icons.Rounded.Policy, onPrivacyPolicyClick)
            row(Res.string.compose_settings_page_licenses_attributions, Res.string.about_licenses_attributions_subtitle, Icons.Rounded.Info, onLicensesAttributionsClick)
            onCheckForUpdatesClick?.let { row(Res.string.compose_settings_root_check_updates_title, Res.string.compose_settings_root_check_updates_description, Icons.Rounded.CloudDownload, it) }
            onTestUpdateBannerClick?.let { row(Res.string.updates_debug_test_title, Res.string.updates_debug_test_description, Icons.Rounded.BugReport, it) }
        }
        else -> Unit
    }
    item {
        SettingsGroup(isTablet = isTablet) {
            rows.forEachIndexed { index, row ->
                if (index > 0) SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(row.title),
                    description = stringResource(row.description),
                    icon = row.icon,
                    isTablet = isTablet,
                    onClick = row.onClick,
                )
            }
        }
    }
    if (sectionPage == SettingsPage.SystemSettings) {
        item {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_incognito_mode_title),
                    description = if (incognitoModeEnabled) "Active — progress, history, reading progress, and tracking updates are paused." else stringResource(Res.string.settings_incognito_mode_description),
                    icon = Icons.Rounded.VisibilityOff,
                    checked = incognitoModeEnabled,
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        onIncognitoModeChanged(enabled)
                        NuvioToastController.show(if (enabled) "Incognito mode is on" else "Incognito mode is off")
                    },
                )
            }
        }
    }
}

private data class SettingsHubRow(
    val title: StringResource,
    val description: StringResource,
    val icon: ImageVector,
    val onClick: () -> Unit,
)
