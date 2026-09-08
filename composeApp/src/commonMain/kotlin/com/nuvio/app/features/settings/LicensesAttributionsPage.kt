package com.nuvio.app.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.features.cloud.PremiumizeCloudLibraryPosterUrl
import com.nuvio.app.features.cloud.TorboxCloudLibraryPosterUrl
import com.nuvio.app.features.cloud.cloudLibraryDisplayArtworkUrl
import com.nuvio.app.isIos
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val TmdbUrl = "https://www.themoviedb.org"
private const val AniListUrl = "https://anilist.co"
private const val AniZipUrl = "https://ani.zip"
private const val KitsuUrl = "https://kitsu.app"
private const val FanartUrl = "https://fanart.tv"
private const val TvdbUrl = "https://thetvdb.com"
private const val ImdbDatasetsUrl = "https://developer.imdb.com/non-commercial-datasets/"
private const val PremiumizeUrl = "https://www.premiumize.me"
private const val TorboxUrl = "https://torbox.app"
private const val MdbListUrl = "https://mdblist.com"
private const val IntroDbUrl = "https://introdb.app/"
private const val MpvKitUrl = "https://github.com/mpvkit/MPVKit"
private const val ApacheLicenseUrl = "https://www.apache.org/licenses/LICENSE-2.0"

/**
 * Several data providers ship no logo we can bundle, so the icon is fetched from the favicon
 * service instead of adding hand-traced artwork that would misrepresent their brands.
 */
private fun faviconUrl(domain: String): String =
    "https://www.google.com/s2/favicons?domain=$domain&sz=128"

private data class AttributionItem(
    val titleRes: StringResource,
    val bodyRes: StringResource,
    val logo: IntegrationLogo?,
    val logoUrl: String? = null,
    val link: String,
)

private data class LicenseItem(
    val titleRes: StringResource,
    val bodyRes: StringResource,
    val licenseRes: StringResource? = null,
    val link: String? = null,
)

@Composable
fun LicensesAttributionsSettingsScreen(
    onBack: () -> Unit,
) {
    NuvioScreen(
        modifier = Modifier.fillMaxSize(),
    ) {
        stickyHeader {
            NuvioScreenHeader(
                title = stringResource(Res.string.compose_settings_page_licenses_attributions),
                onBack = onBack,
            )
        }
        licensesAttributionsContent(isTablet = false)
    }
}

internal fun LazyListScope.licensesAttributionsContent(
    isTablet: Boolean,
) {
    item {
        LicensesAttributionsBody(isTablet = isTablet)
    }
}

@Composable
private fun LicensesAttributionsBody(
    isTablet: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isTablet) 28.dp else 24.dp),
    ) {
        PlainSettingsStack(
            title = stringResource(Res.string.settings_licenses_attributions_section_app),
            isTablet = isTablet,
        ) {
            val appItems = appLicenseItems()
            appItems.forEachIndexed { index, item ->
                LicenseRow(
                    item = item,
                    isTablet = isTablet,
                )
                if (index != appItems.lastIndex) {
                    PlainStackDivider()
                }
            }
        }

        PlainSettingsStack(
            title = stringResource(Res.string.settings_licenses_attributions_section_data),
            isTablet = isTablet,
        ) {
            val items = attributionItems()
            items.forEachIndexed { index, item ->
                AttributionRow(
                    item = item,
                    isTablet = isTablet,
                )
                if (index != items.lastIndex) {
                    PlainStackDivider()
                }
            }
        }

        PlainSettingsStack(
            title = stringResource(Res.string.settings_licenses_attributions_section_playback),
            isTablet = isTablet,
        ) {
            LicenseRow(
                item = platformLicenseItem(),
                isTablet = isTablet,
            )
        }
    }
}

@Composable
internal fun PlainSettingsStack(
    title: String,
    isTablet: Boolean,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(if (isTablet) 12.dp else 10.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun AttributionRow(
    item: AttributionItem,
    isTablet: Boolean,
) {
    val uriHandler = LocalUriHandler.current
    val title = stringResource(item.titleRes)
    LinkedPlainRow(
        title = title,
        body = stringResource(item.bodyRes),
        link = item.link,
        isTablet = isTablet,
        leading = when {
            item.logo != null -> item.logo.let { logo ->
                {
                    IntegrationLogoImage(
                        painter = integrationLogoPainter(logo),
                        contentDescription = title,
                        isTablet = isTablet,
                    )
                }
            }
            item.logoUrl != null -> item.logoUrl.let { logoUrl ->
                {
                    ProviderLogoImage(
                        url = logoUrl,
                        contentDescription = title,
                        isTablet = isTablet,
                    )
                }
            }
            else -> null
        },
        onOpen = { uriHandler.openUri(item.link) },
    )
}

@Composable
private fun LicenseRow(
    item: LicenseItem,
    isTablet: Boolean,
) {
    val uriHandler = LocalUriHandler.current
    val itemBody = stringResource(item.bodyRes)
    val itemLicense = item.licenseRes?.let { stringResource(it) }
    val body = buildString {
        append(itemBody)
        if (itemLicense != null) {
            append("\n")
            append(itemLicense)
        }
    }
    val title = stringResource(item.titleRes)
    val link = item.link
    if (link == null) {
        PlainRow(
            title = title,
            body = body,
            isTablet = isTablet,
        )
    } else {
        LinkedPlainRow(
            title = title,
            body = body,
            link = link,
            isTablet = isTablet,
            onOpen = { uriHandler.openUri(link) },
        )
    }
}

/** A [LinkedPlainRow] without the trailing link line or the open-in-new affordance. */
@Composable
internal fun PlainRow(
    title: String,
    body: String,
    isTablet: Boolean,
    leading: (@Composable () -> Unit)? = null,
) {
    val verticalPadding = if (isTablet) 18.dp else 16.dp
    val horizontalPadding = if (isTablet) 4.dp else 0.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(if (isTablet) 18.dp else 14.dp),
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun LinkedPlainRow(
    title: String,
    body: String,
    link: String,
    isTablet: Boolean,
    leading: (@Composable () -> Unit)? = null,
    onOpen: () -> Unit,
) {
    val verticalPadding = if (isTablet) 18.dp else 16.dp
    val horizontalPadding = if (isTablet) 4.dp else 0.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(if (isTablet) 18.dp else 14.dp),
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = link,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(if (isTablet) 22.dp else 20.dp)
                .alpha(0.72f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IntegrationLogoImage(
    painter: Painter,
    contentDescription: String,
    isTablet: Boolean,
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = Modifier
            .padding(top = 2.dp)
            .size(if (isTablet) 46.dp else 40.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun ProviderLogoImage(
    url: String,
    contentDescription: String,
    isTablet: Boolean,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = Modifier
            .padding(top = 2.dp)
            .size(if (isTablet) 46.dp else 40.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
internal fun PlainStackDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
    )
}

private fun attributionItems(): List<AttributionItem> = listOf(
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_anilist_title,
        bodyRes = Res.string.settings_licenses_attributions_anilist_body,
        logo = null,
        logoUrl = faviconUrl("anilist.co"),
        link = AniListUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_anizip_title,
        bodyRes = Res.string.settings_licenses_attributions_anizip_body,
        logo = null,
        logoUrl = faviconUrl("ani.zip"),
        link = AniZipUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_kitsu_title,
        bodyRes = Res.string.settings_licenses_attributions_kitsu_body,
        logo = null,
        logoUrl = faviconUrl("kitsu.app"),
        link = KitsuUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_tvdb_title,
        bodyRes = Res.string.settings_licenses_attributions_tvdb_body,
        logo = null,
        logoUrl = faviconUrl("thetvdb.com"),
        link = TvdbUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_fanart_title,
        bodyRes = Res.string.settings_licenses_attributions_fanart_body,
        logo = null,
        logoUrl = faviconUrl("fanart.tv"),
        link = FanartUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_tmdb_title,
        bodyRes = Res.string.settings_licenses_attributions_tmdb_body,
        logo = IntegrationLogo.Tmdb,
        link = TmdbUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_premiumize_title,
        bodyRes = Res.string.settings_licenses_attributions_premiumize_body,
        logo = null,
        logoUrl = PremiumizeCloudLibraryPosterUrl,
        link = PremiumizeUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_torbox_title,
        bodyRes = Res.string.settings_licenses_attributions_torbox_body,
        logo = null,
        logoUrl = cloudLibraryDisplayArtworkUrl(TorboxCloudLibraryPosterUrl),
        link = TorboxUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_mdblist_title,
        bodyRes = Res.string.settings_licenses_attributions_mdblist_body,
        logo = IntegrationLogo.MdbList,
        link = MdbListUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_introdb_title,
        bodyRes = Res.string.settings_licenses_attributions_introdb_body,
        logo = IntegrationLogo.IntroDb,
        link = IntroDbUrl,
    ),
    AttributionItem(
        titleRes = Res.string.settings_licenses_attributions_imdb_title,
        bodyRes = Res.string.settings_licenses_attributions_imdb_body,
        logo = IntegrationLogo.Imdb,
        link = ImdbDatasetsUrl,
    ),
)

private fun appLicenseItems(): List<LicenseItem> = listOf(
    LicenseItem(
        titleRes = Res.string.settings_licenses_attributions_anivio_title,
        bodyRes = Res.string.settings_licenses_attributions_anivio_body,
    ),
)

private fun platformLicenseItem(): LicenseItem =
    if (isIos) {
        LicenseItem(
            titleRes = Res.string.settings_licenses_attributions_mpvkit_title,
            bodyRes = Res.string.settings_licenses_attributions_mpvkit_body,
            licenseRes = Res.string.settings_licenses_attributions_mpvkit_license,
            link = MpvKitUrl,
        )
    } else {
        LicenseItem(
            titleRes = Res.string.settings_licenses_attributions_exoplayer_title,
            bodyRes = Res.string.settings_licenses_attributions_exoplayer_body,
            licenseRes = Res.string.settings_licenses_attributions_exoplayer_license,
            link = ApacheLicenseUrl,
        )
    }
