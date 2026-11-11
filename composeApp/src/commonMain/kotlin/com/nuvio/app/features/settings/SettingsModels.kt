package com.nuvio.app.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_category_about
import nuvio.composeapp.generated.resources.compose_settings_category_general
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.compose_settings_page_addons
import nuvio.composeapp.generated.resources.compose_settings_page_advanced
import nuvio.composeapp.generated.resources.compose_settings_page_appearance
import nuvio.composeapp.generated.resources.compose_settings_page_content_discovery
import nuvio.composeapp.generated.resources.compose_settings_page_debrid
import nuvio.composeapp.generated.resources.compose_settings_page_continue_watching
import nuvio.composeapp.generated.resources.compose_settings_page_homescreen
import nuvio.composeapp.generated.resources.compose_settings_page_integrations
import nuvio.composeapp.generated.resources.compose_settings_page_licenses_attributions
import nuvio.composeapp.generated.resources.compose_settings_page_mdblist_ratings
import nuvio.composeapp.generated.resources.compose_settings_page_meta_screen
import nuvio.composeapp.generated.resources.compose_settings_page_notifications
import nuvio.composeapp.generated.resources.compose_settings_page_playback
import nuvio.composeapp.generated.resources.compose_settings_page_plugins
import nuvio.composeapp.generated.resources.compose_settings_page_poster_customization
import nuvio.composeapp.generated.resources.compose_settings_page_reader
import nuvio.composeapp.generated.resources.compose_settings_page_root
import nuvio.composeapp.generated.resources.compose_settings_page_streams
import nuvio.composeapp.generated.resources.compose_settings_page_about
import nuvio.composeapp.generated.resources.compose_settings_page_privacy_policy
import nuvio.composeapp.generated.resources.compose_settings_page_tmdb_enrichment
import nuvio.composeapp.generated.resources.compose_settings_page_trakt
import nuvio.composeapp.generated.resources.compose_settings_page_tracking
import nuvio.composeapp.generated.resources.settings_account
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

internal enum class SettingsCategory(
    val labelRes: StringResource,
    val icon: ImageVector,
) {
    Account(Res.string.settings_account, Icons.Rounded.AccountCircle),
    General(Res.string.compose_settings_category_general, Icons.Rounded.Settings),
    About(Res.string.compose_settings_category_about, Icons.Rounded.Info),
    Advanced(Res.string.compose_settings_page_advanced, Icons.Rounded.Tune),
}

internal enum class SettingsPage(
    val titleRes: StringResource,
    val category: SettingsCategory,
    val parentPage: SettingsPage?,
) {
    Root(
        titleRes = Res.string.compose_settings_page_root,
        category = SettingsCategory.General,
        parentPage = null,
    ),
    YourSpace(Res.string.settings_section_you, SettingsCategory.Account, Root),
    WatchSettings(Res.string.settings_section_watch, SettingsCategory.General, Root),
    Personalize(Res.string.settings_section_personalize, SettingsCategory.General, Root),
    Services(Res.string.settings_section_services, SettingsCategory.General, Root),
    SystemSettings(Res.string.settings_section_system, SettingsCategory.Advanced, Root),
    Anivio(Res.string.settings_section_anivio, SettingsCategory.General, Root),
    AboutSettings(Res.string.compose_settings_root_about_section, SettingsCategory.About, Root),
    Layout(Res.string.settings_layout_title, SettingsCategory.General, Personalize),
    Account(
        titleRes = Res.string.compose_settings_page_account,
        category = SettingsCategory.Account,
        parentPage = YourSpace,
    ),
    About(
        titleRes = Res.string.compose_settings_page_about,
        category = SettingsCategory.About,
        parentPage = AboutSettings,
    ),
    PrivacyPolicy(
        titleRes = Res.string.compose_settings_page_privacy_policy,
        category = SettingsCategory.About,
        parentPage = AboutSettings,
    ),
    LicensesAttributions(
        titleRes = Res.string.compose_settings_page_licenses_attributions,
        category = SettingsCategory.About,
        parentPage = AboutSettings,
    ),
    Playback(
        titleRes = Res.string.compose_settings_page_playback,
        category = SettingsCategory.General,
        parentPage = WatchSettings,
    ),
    Reader(
        titleRes = Res.string.compose_settings_page_reader,
        category = SettingsCategory.General,
        parentPage = Anivio,
    ),
    Appearance(
        titleRes = Res.string.compose_settings_page_appearance,
        category = SettingsCategory.General,
        parentPage = Personalize,
    ),
    Streams(
        titleRes = Res.string.compose_settings_page_streams,
        category = SettingsCategory.General,
        parentPage = Layout,
    ),
    Advanced(
        titleRes = Res.string.compose_settings_page_advanced,
        category = SettingsCategory.Advanced,
        parentPage = SystemSettings,
    ),
    Notifications(
        titleRes = Res.string.compose_settings_page_notifications,
        category = SettingsCategory.General,
        parentPage = SystemSettings,
    ),
    ContinueWatching(
        titleRes = Res.string.compose_settings_page_continue_watching,
        category = SettingsCategory.General,
        parentPage = Layout,
    ),
    PosterCustomization(
        titleRes = Res.string.compose_settings_page_poster_customization,
        category = SettingsCategory.General,
        parentPage = Layout,
    ),
    ContentDiscovery(
        titleRes = Res.string.compose_settings_page_content_discovery,
        category = SettingsCategory.General,
        parentPage = WatchSettings,
    ),
    Addons(
        titleRes = Res.string.compose_settings_page_addons,
        category = SettingsCategory.General,
        parentPage = ContentDiscovery,
    ),
    Plugins(
        titleRes = Res.string.compose_settings_page_plugins,
        category = SettingsCategory.General,
        parentPage = ContentDiscovery,
    ),
    Homescreen(
        titleRes = Res.string.compose_settings_page_homescreen,
        category = SettingsCategory.General,
        parentPage = Layout,
    ),
    MetaScreen(
        titleRes = Res.string.compose_settings_page_meta_screen,
        category = SettingsCategory.General,
        parentPage = Layout,
    ),
    Integrations(
        titleRes = Res.string.compose_settings_page_integrations,
        category = SettingsCategory.General,
        parentPage = Services,
    ),
    TmdbEnrichment(
        titleRes = Res.string.compose_settings_page_tmdb_enrichment,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    MdbListRatings(
        titleRes = Res.string.compose_settings_page_mdblist_ratings,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    Debrid(
        titleRes = Res.string.compose_settings_page_debrid,
        category = SettingsCategory.General,
        parentPage = Integrations,
    ),
    TraktAuthentication(
        // Keep the enum name for saved navigation-state compatibility.
        titleRes = Res.string.compose_settings_page_tracking,
        category = SettingsCategory.Account,
        parentPage = Services,
    ),
    PlaybackPlayer(Res.string.settings_playback_section_player, SettingsCategory.General, Playback),
    PlaybackLanguages(Res.string.settings_playback_section_subtitle_audio, SettingsCategory.General, Playback),
    PlaybackSubtitles(Res.string.settings_playback_section_subtitle_rendering, SettingsCategory.General, Playback),
    PlaybackStreams(Res.string.settings_playback_section_stream_selection, SettingsCategory.General, Playback),
    PlaybackAutoPlay(Res.string.settings_playback_section_stream_auto_play, SettingsCategory.General, Playback),
    PlaybackVideo(Res.string.settings_playback_section_decoder, SettingsCategory.General, Playback),
    PlaybackSkip(Res.string.settings_playback_section_skip_segments, SettingsCategory.General, Playback),
    PlaybackUpNext(Res.string.settings_playback_section_next_episode, SettingsCategory.General, Playback),
    PlaybackP2p(Res.string.settings_playback_section_p2p, SettingsCategory.General, Playback),
}

internal val SettingsPage.opensInlineOnTablet: Boolean
    get() = parentPage != null

internal fun SettingsPage.previousPage(): SettingsPage? = parentPage

