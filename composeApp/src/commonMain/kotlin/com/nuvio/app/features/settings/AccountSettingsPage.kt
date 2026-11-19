package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.anilist.AniListAuthRepository
import com.nuvio.app.features.anilist.AniListConnectionMode
import com.nuvio.app.features.debrid.DebridProviders
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.mal.MalAuthSettings
import com.nuvio.app.features.mal.MalConnectionMode
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.profiles.ProfileEditScreen
import com.nuvio.app.features.profiles.AvatarRepository
import com.nuvio.app.features.anilist.AniListStatisticsRepository
import com.nuvio.app.features.profile.ProfileShareSettingsRepository
import com.nuvio.app.features.profile.ProfileShareSheet
import com.nuvio.app.features.profile.ProfileStatsRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.auth_account_deletion_failed
import nuvio.composeapp.generated.resources.settings_account_delete_account
import nuvio.composeapp.generated.resources.settings_account_delete_account_description
import nuvio.composeapp.generated.resources.settings_account_delete_confirm_message
import nuvio.composeapp.generated.resources.settings_account_delete_confirm_title
import nuvio.composeapp.generated.resources.settings_account_email
import nuvio.composeapp.generated.resources.settings_account_linked_services
import nuvio.composeapp.generated.resources.settings_account_not_signed_in
import nuvio.composeapp.generated.resources.settings_account_profile
import nuvio.composeapp.generated.resources.settings_account_sign_out
import nuvio.composeapp.generated.resources.settings_account_sign_out_confirm_message
import nuvio.composeapp.generated.resources.settings_account_sign_out_confirm_title
import nuvio.composeapp.generated.resources.settings_account_status
import nuvio.composeapp.generated.resources.settings_account_status_anonymous
import nuvio.composeapp.generated.resources.settings_account_status_signed_in
import nuvio.composeapp.generated.resources.profile_edit_edit_title
import nuvio.composeapp.generated.resources.settings_account_share_profile
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.accountSettingsContent(
    isTablet: Boolean,
) {
    item {
        AccountSettingsBody(isTablet = isTablet)
    }
}

@Composable
private fun AccountSettingsBody(
    isTablet: Boolean,
) {
    val authState by AuthRepository.state.collectAsStateWithLifecycle()
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val aniListAuth by remember {
        AniListAuthRepository.ensureLoaded()
        AniListAuthRepository.uiState
    }.collectAsStateWithLifecycle()
    val malAuth by remember {
        MalAuthSettings.ensureLoaded()
        MalAuthSettings.uiState
    }.collectAsStateWithLifecycle()
    val debridSettings by remember {
        DebridSettingsRepository.ensureLoaded()
        DebridSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    var editProfile by remember { mutableStateOf(false) }
    var shareProfile by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    val deleteAccountFallbackMessage = stringResource(Res.string.auth_account_deletion_failed)
    val canDeleteAccount = AppFeaturePolicy.accountDeletionEnabled && authState is AuthState.Authenticated

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NuvioSurfaceCard {
            Text(
                text = stringResource(Res.string.compose_settings_page_account),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            when (val state = authState) {
                is AuthState.Authenticated -> {
                    AccountInfoRow(
                        label = stringResource(Res.string.settings_account_status),
                        value = if (state.isAnonymous) {
                            stringResource(Res.string.settings_account_status_anonymous)
                        } else {
                            stringResource(Res.string.settings_account_status_signed_in)
                        },
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                    state.email?.takeUnless { state.isAnonymous }?.let { email ->
                        Spacer(modifier = Modifier.height(8.dp))
                        AccountInfoRow(
                            label = stringResource(Res.string.settings_account_email),
                            value = email,
                        )
                    }
                    profileState.activeProfile?.takeIf { it.name.isNotBlank() }?.let { profile ->
                        Spacer(modifier = Modifier.height(8.dp))
                        AccountInfoRow(
                            label = stringResource(Res.string.settings_account_profile),
                            value = profile.name,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    AccountInfoRow(
                        label = stringResource(Res.string.settings_account_linked_services),
                        value = linkedServicesSummary(
                            aniListConnected = aniListAuth.mode == AniListConnectionMode.CONNECTED,
                            aniListUsername = aniListAuth.username,
                            malConnected = malAuth.mode == MalConnectionMode.CONNECTED,
                            malUsername = malAuth.username,
                            debridProviders = debridSettings.providerApiKeys.keys
                                .mapNotNull(DebridProviders::byId)
                                .filter { it.visibleInUi }
                                .map { it.shortName }
                                .sorted(),
                        ),
                    )
                }
                else -> {
                    Text(
                        text = stringResource(Res.string.settings_account_not_signed_in),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (profileState.activeProfile != null) {
            NuvioSurfaceCard {
                SettingsNavigationRow(
                    title = stringResource(Res.string.profile_edit_edit_title),
                    description = null,
                    icon = Icons.Rounded.Edit,
                    onClick = { editProfile = true },
                    isTablet = isTablet,
                )
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_account_share_profile),
                    description = null,
                    icon = Icons.Rounded.Share,
                    onClick = { shareProfile = true },
                    isTablet = isTablet,
                )
            }
        }

        NuvioPrimaryButton(
            text = stringResource(Res.string.settings_account_sign_out),
            onClick = { showSignOutConfirm = true },
        )

        if (canDeleteAccount) {
            DeleteAccountCard(
                errorMessage = deleteErrorMessage,
                onDeleteClick = {
                    deleteErrorMessage = null
                    showDeleteConfirm = true
                },
            )
        }
    }

    val currentProfile = profileState.activeProfile
    if (editProfile && currentProfile != null) {
        Dialog(
            onDismissRequest = { editProfile = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(0.95f).fillMaxHeight(0.9f),
                shape = MaterialTheme.nuvio.shapes.card,
            ) {
                ProfileEditScreen(
                    profile = currentProfile,
                    onBack = { editProfile = false },
                    onSaved = { editProfile = false },
                )
            }
        }
    }
    if (shareProfile && currentProfile != null) {
        val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()
        val shareSettings by ProfileShareSettingsRepository.settings.collectAsStateWithLifecycle()
        val stats by ProfileStatsRepository.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(currentProfile.profileIndex) {
            AvatarRepository.refreshAvatars()
            ProfileShareSettingsRepository.ensureLoaded()
            ProfileStatsRepository.start()
            AniListStatisticsRepository.refresh()
        }
        Dialog(
            onDismissRequest = { shareProfile = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 568.dp).fillMaxWidth(0.95f),
                shape = MaterialTheme.nuvio.shapes.card,
            ) {
                Column(Modifier.fillMaxHeight(0.9f).verticalScroll(rememberScrollState())) {
                    ProfileShareSheet(
                        stats = stats.stats,
                        profile = currentProfile,
                        avatars = avatars,
                        aniListAuth = aniListAuth,
                        malAuth = malAuth,
                        settings = shareSettings,
                        onDismiss = { shareProfile = false },
                    )
                }
            }
        }
    }

    NuvioStatusModal(
        title = stringResource(Res.string.settings_account_sign_out_confirm_title),
        message = stringResource(Res.string.settings_account_sign_out_confirm_message),
        isVisible = showSignOutConfirm,
        confirmText = stringResource(Res.string.settings_account_sign_out),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            showSignOutConfirm = false
            scope.launch { AuthRepository.signOut() }
        },
        onDismiss = { showSignOutConfirm = false },
    )

    NuvioStatusModal(
        title = stringResource(Res.string.settings_account_delete_confirm_title),
        message = stringResource(Res.string.settings_account_delete_confirm_message),
        isVisible = showDeleteConfirm,
        isBusy = isDeletingAccount,
        destructive = true,
        confirmText = stringResource(Res.string.settings_account_delete_account),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            if (isDeletingAccount) return@NuvioStatusModal
            isDeletingAccount = true
            scope.launch {
                val result = AuthRepository.deleteAccount()
                isDeletingAccount = false
                showDeleteConfirm = false
                deleteErrorMessage = if (result.isSuccess) {
                    null
                } else {
                    AuthRepository.error.value
                        ?: result.exceptionOrNull()?.message
                        ?: deleteAccountFallbackMessage
                }
            }
        },
        onDismiss = {
            if (!isDeletingAccount) {
                showDeleteConfirm = false
            }
        },
    )
}

@Composable
private fun DeleteAccountCard(
    errorMessage: String?,
    onDeleteClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    NuvioSurfaceCard {
        Text(
            text = stringResource(Res.string.settings_account_delete_account),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.settings_account_delete_account_description),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )
        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.danger,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onDeleteClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(NuvioTokens.Space.s48 + NuvioTokens.Space.s4),
            shape = tokens.shapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.colors.danger,
                contentColor = tokens.colors.textInverse,
            ),
        ) {
            Text(
                text = stringResource(Res.string.settings_account_delete_account),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * One-line summary of everything linked from this device: tracking usernames when connected plus
 * short debrid provider names, so the account page reads like a wallet of connected services.
 */
private fun linkedServicesSummary(
    aniListConnected: Boolean,
    aniListUsername: String?,
    malConnected: Boolean,
    malUsername: String?,
    debridProviders: List<String>,
): String = buildList {
    if (aniListConnected) {
        add(aniListUsername?.takeIf { it.isNotBlank() }?.let { "AniList ($it)" } ?: "AniList")
    }
    if (malConnected) {
        add(malUsername?.takeIf { it.isNotBlank() }?.let { "MyAnimeList ($it)" } ?: "MyAnimeList")
    }
    addAll(debridProviders)
}.joinToString(separator = ", ").ifBlank { "None" }

@Composable
private fun AccountInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
