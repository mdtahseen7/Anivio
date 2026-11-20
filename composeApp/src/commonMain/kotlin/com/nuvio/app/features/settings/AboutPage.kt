package com.nuvio.app.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSurfaceCard
import coil3.compose.AsyncImage
import nuvio.composeapp.generated.resources.*
import nuvio.composeapp.generated.resources.about_author_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit,
) {
    NuvioScreen(
        modifier = Modifier.fillMaxSize(),
    ) {
        stickyHeader {
            NuvioScreenHeader(
                title = stringResource(Res.string.compose_settings_page_about),
                onBack = onBack,
            )
        }
        aboutContent(isTablet = false)
    }
}

internal fun LazyListScope.aboutContent(
    isTablet: Boolean,
    onCommunityClick: (() -> Unit)? = null,
) {
    item {
        AboutBody(isTablet = isTablet, onCommunityClick = onCommunityClick)
    }
}

@Composable
private fun AboutBody(
    isTablet: Boolean,
    onCommunityClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isTablet) 28.dp else 24.dp),
    ) {
        NuvioSurfaceCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppBrandWordmark(
                    contentDescription = stringResource(Res.string.about_app_name),
                    modifier = Modifier.width(170.dp),
                )
                Text(
                    text = stringResource(
                        Res.string.about_app_version,
                        AppVersionConfig.VERSION_NAME,
                        AppVersionConfig.VERSION_CODE.toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.about_app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        CreatorSpotlight()
        if (onCommunityClick != null) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_page_community),
                    description = stringResource(Res.string.settings_community_description),
                    isTablet = isTablet,
                    onClick = onCommunityClick,
                )
            }
        }
    }
}

@Composable
private fun CreatorSpotlight() {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.30f), colors.surfaceContainerHigh)))
            .border(1.dp, colors.primary.copy(alpha = 0.26f), shape)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Creator spotlight",
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.86f))
                        .border(2.dp, colors.primary.copy(alpha = 0.72f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "S",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    AsyncImage(
                        model = "https://github.com/mdtahseen7.png?size=160",
                        contentDescription = "Anivio creator profile picture",
                        modifier = Modifier.size(78.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = stringResource(Res.string.about_author_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Creator of Anivio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                text = "Building a calmer, more personal way to discover, watch, and share the stories you love.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
