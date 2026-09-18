package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.anilist.TvdbSettingsRepository
import com.nuvio.app.features.discord.discordRichPresenceSupported
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_page_debrid
import nuvio.composeapp.generated.resources.compose_settings_page_mdblist_ratings
import nuvio.composeapp.generated.resources.compose_settings_page_tmdb_enrichment
import nuvio.composeapp.generated.resources.settings_integrations_debrid_description
import nuvio.composeapp.generated.resources.settings_integrations_discord_section
import nuvio.composeapp.generated.resources.settings_integrations_mdblist_description
import nuvio.composeapp.generated.resources.settings_integrations_section_title
import nuvio.composeapp.generated.resources.settings_integrations_tmdb_description
import nuvio.composeapp.generated.resources.settings_tvdb_api_key_label
import nuvio.composeapp.generated.resources.settings_tvdb_bundled_key_note
import nuvio.composeapp.generated.resources.settings_tvdb_description
import nuvio.composeapp.generated.resources.settings_tvdb_save
import nuvio.composeapp.generated.resources.settings_tvdb_title
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.integrationsContent(
    isTablet: Boolean,
    onTmdbClick: () -> Unit,
    onMdbListClick: () -> Unit,
    onDebridClick: () -> Unit,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_integrations_section_title),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_tmdb_enrichment),
                    description = stringResource(Res.string.settings_integrations_tmdb_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.Tmdb),
                    isTablet = isTablet,
                    onClick = onTmdbClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_mdblist_ratings),
                    description = stringResource(Res.string.settings_integrations_mdblist_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.MdbList),
                    isTablet = isTablet,
                    onClick = onMdbListClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_debrid),
                    description = stringResource(Res.string.settings_integrations_debrid_description),
                    isTablet = isTablet,
                    onClick = onDebridClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                // Inline rather than its own page: a single key is the whole TVDB configuration.
                TvdbApiKeyRow(isTablet = isTablet)
            }
        }
    }

    if (discordRichPresenceSupported) {
        item {
            SettingsSection(
                title = stringResource(Res.string.settings_integrations_discord_section),
                isTablet = isTablet,
            ) {
                DiscordConnectionCard(isTablet = isTablet)
            }
        }
    }
}

@Composable
private fun TvdbApiKeyRow(isTablet: Boolean) {
    val settings by remember {
        TvdbSettingsRepository.ensureLoaded()
        TvdbSettingsRepository.uiState
    }.collectAsStateWithLifecycle()

    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val verticalPadding = if (isTablet) 16.dp else 14.dp
    var draft by rememberSaveable(settings.apiKey) { mutableStateOf(settings.apiKey) }
    val normalizedDraft = draft.trim()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.settings_tvdb_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(Res.string.settings_tvdb_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!settings.hasUserApiKey) {
                Text(
                    text = stringResource(Res.string.settings_tvdb_bundled_key_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsSecretTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.settings_tvdb_api_key_label),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    draft = normalizedDraft
                    TvdbSettingsRepository.setApiKey(normalizedDraft)
                },
                enabled = normalizedDraft != settings.apiKey,
            ) {
                Text(text = stringResource(Res.string.settings_tvdb_save))
            }
        }
    }
}
