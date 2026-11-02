package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.features.discord.DiscordAuth
import com.nuvio.app.features.discord.DiscordUser
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.*
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
) {
    item {
        AboutBody(isTablet = isTablet)
    }
}

@Composable
private fun AboutBody(
    isTablet: Boolean,
) {
    val ownerProfile = remember { mutableStateOf<DiscordUser?>(null) }
    LaunchedEffect(Unit) {
        DiscordAuth.ensureLoaded()
        while (true) {
            ownerProfile.value = DiscordAuth.fetchUser(DISCORD_OWNER_ID)
                ?: DiscordAuth.uiState.value.user?.takeIf { it.id == DISCORD_OWNER_ID }
            delay(DISCORD_OWNER_REFRESH_INTERVAL_MS)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isTablet) 28.dp else 24.dp),
    ) {
        NuvioSurfaceCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.about_app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
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

        NuvioSurfaceCard {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ownerProfile.value?.avatarUrl?.let { avatarUrl ->
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Anivio creator profile picture",
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Column {
                    Text(
                        text = "Created by",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = ownerProfile.value?.globalName
                            ?: ownerProfile.value?.username
                            ?: "Anivio creator",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Building Anivio with the community",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val DISCORD_OWNER_ID = "1015817994292711608"
private const val DISCORD_OWNER_REFRESH_INTERVAL_MS = 5 * 60 * 1_000L
