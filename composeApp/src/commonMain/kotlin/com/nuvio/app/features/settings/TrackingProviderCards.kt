package com.nuvio.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.anilist.AniListAuthError
import com.nuvio.app.features.anilist.AniListAuthRepository
import com.nuvio.app.features.anilist.AniListAuthUiState
import com.nuvio.app.features.anilist.AniListConnectionMode
import com.nuvio.app.features.mal.MalAuthError
import com.nuvio.app.features.mal.MalAuthSettings
import com.nuvio.app.features.mal.MalAuthUiState
import com.nuvio.app.features.mal.MalConnectionMode
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.rating_mal
import nuvio.composeapp.generated.resources.settings_anilist_approval_redirect
import nuvio.composeapp.generated.resources.settings_anilist_authorization_expired
import nuvio.composeapp.generated.resources.settings_anilist_authorization_revoked
import nuvio.composeapp.generated.resources.settings_anilist_connect
import nuvio.composeapp.generated.resources.settings_anilist_connected_as
import nuvio.composeapp.generated.resources.settings_anilist_connected_description
import nuvio.composeapp.generated.resources.settings_anilist_default_user
import nuvio.composeapp.generated.resources.settings_anilist_disconnect
import nuvio.composeapp.generated.resources.settings_anilist_finish_sign_in
import nuvio.composeapp.generated.resources.settings_anilist_invalid_callback
import nuvio.composeapp.generated.resources.settings_anilist_missing_credentials
import nuvio.composeapp.generated.resources.settings_anilist_open_login
import nuvio.composeapp.generated.resources.settings_anilist_sign_in_description
import nuvio.composeapp.generated.resources.settings_anilist_sign_in_failed
import nuvio.composeapp.generated.resources.settings_anilist_website
import nuvio.composeapp.generated.resources.settings_mal_connect
import nuvio.composeapp.generated.resources.settings_mal_connected_as
import nuvio.composeapp.generated.resources.settings_mal_connected_description
import nuvio.composeapp.generated.resources.settings_mal_default_user
import nuvio.composeapp.generated.resources.settings_mal_disconnect
import nuvio.composeapp.generated.resources.settings_mal_finish_sign_in
import nuvio.composeapp.generated.resources.settings_mal_approval_redirect
import nuvio.composeapp.generated.resources.settings_mal_invalid_callback
import nuvio.composeapp.generated.resources.settings_mal_missing_credentials
import nuvio.composeapp.generated.resources.settings_mal_open_login
import nuvio.composeapp.generated.resources.settings_mal_sign_in_description
import nuvio.composeapp.generated.resources.settings_mal_sign_in_failed
import nuvio.composeapp.generated.resources.settings_mal_authorization_expired
import nuvio.composeapp.generated.resources.settings_mal_authorization_revoked
import nuvio.composeapp.generated.resources.settings_mal_website
import nuvio.composeapp.generated.resources.settings_tracking_disconnect_description
import nuvio.composeapp.generated.resources.settings_tracking_disconnect_title
import nuvio.composeapp.generated.resources.settings_tracking_failed_open_browser
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal enum class TrackingBrand(val displayName: String) {
    /** The app itself: on-device library and progress. */
    LOCAL("Anivio"),
    ANILIST("AniList"),
    MAL("MyAnimeList"),
    TMDB("TMDB"),
}

internal enum class TrackingConnectionCardMode {
    DISCONNECTED,
    AWAITING_APPROVAL,
    CONNECTED,
}

/**
 * Whether a brand can currently back the library or watch-progress source. Local is always
 * available; connected tracker availability is supplied by the provider registry.
 */
internal fun isTrackingBrandAvailable(
    brand: TrackingBrand,
    aniListConnected: Boolean,
    malConnected: Boolean,
): Boolean = when (brand) {
    TrackingBrand.LOCAL,
    TrackingBrand.TMDB,
    -> true
    TrackingBrand.ANILIST -> aniListConnected
    TrackingBrand.MAL -> malConnected
}

internal fun AniListConnectionMode.toTrackingConnectionCardMode(): TrackingConnectionCardMode = when (this) {
    AniListConnectionMode.DISCONNECTED -> TrackingConnectionCardMode.DISCONNECTED
    AniListConnectionMode.AWAITING_APPROVAL -> TrackingConnectionCardMode.AWAITING_APPROVAL
    AniListConnectionMode.CONNECTED -> TrackingConnectionCardMode.CONNECTED
}

internal fun MalConnectionMode.toTrackingConnectionCardMode(): TrackingConnectionCardMode = when (this) {
    MalConnectionMode.DISCONNECTED -> TrackingConnectionCardMode.DISCONNECTED
    MalConnectionMode.AWAITING_APPROVAL -> TrackingConnectionCardMode.AWAITING_APPROVAL
    MalConnectionMode.CONNECTED -> TrackingConnectionCardMode.CONNECTED
}

@Composable
internal fun TrackingProviderCards(
    isTablet: Boolean,
    aniListUiState: AniListAuthUiState,
    malUiState: MalAuthUiState,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useTwoColumns = maxWidth >= 600.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (isTablet) 16.dp else 12.dp),
        ) {
            if (useTwoColumns) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AniListProviderCard(
                        uiState = aniListUiState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    MalProviderCard(
                        uiState = malUiState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                AniListProviderCard(
                    uiState = aniListUiState,
                    modifier = Modifier.fillMaxWidth(),
                )
                MalProviderCard(
                    uiState = malUiState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AniListProviderCard(
    uiState: AniListAuthUiState,
    modifier: Modifier,
) {
    TrackingProviderCard(
        brand = TrackingBrand.ANILIST,
        mode = uiState.mode.toTrackingConnectionCardMode(),
        credentialsConfigured = uiState.credentialsConfigured,
        isLoading = uiState.isLoading,
        connectedLabel = stringResource(
            Res.string.settings_anilist_connected_as,
            uiState.username ?: stringResource(Res.string.settings_anilist_default_user),
        ),
        connectedDescription = stringResource(Res.string.settings_anilist_connected_description),
        avatarUrl = uiState.avatarUrl,
        signInDescription = stringResource(Res.string.settings_anilist_sign_in_description),
        finishSignInLabel = stringResource(Res.string.settings_anilist_finish_sign_in),
        approvalDescription = stringResource(Res.string.settings_anilist_approval_redirect),
        connectLabel = stringResource(Res.string.settings_anilist_connect),
        openLoginLabel = stringResource(Res.string.settings_anilist_open_login),
        disconnectLabel = stringResource(Res.string.settings_anilist_disconnect),
        missingCredentialsMessage = stringResource(Res.string.settings_anilist_missing_credentials),
        errorMessage = aniListErrorMessage(uiState.error),
        websiteLabel = stringResource(Res.string.settings_anilist_website),
        websiteUrl = ANILIST_WEBSITE_URL,
        onConnectRequested = AniListAuthRepository::onConnectRequested,
        onResumeAuthorization = {
            AniListAuthRepository.pendingAuthorizationUrl()
                ?: AniListAuthRepository.onConnectRequested()
        },
        onCancelAuthorization = AniListAuthRepository::onCancelAuthorization,
        onDisconnect = AniListAuthRepository::onDisconnectRequested,
        modifier = modifier,
    )
}

@Composable
private fun MalProviderCard(
    uiState: MalAuthUiState,
    modifier: Modifier,
) {
    val username = uiState.username?.takeIf(String::isNotBlank) ?: "MyAnimeList user"
    TrackingProviderCard(
        brand = TrackingBrand.MAL,
        mode = uiState.mode.toTrackingConnectionCardMode(),
        credentialsConfigured = uiState.credentialsConfigured,
        isLoading = uiState.isLoading,
        connectedLabel = "Connected as $username",
        connectedDescription = "Your MyAnimeList library and completed-episode progress are linked.",
        avatarUrl = uiState.avatarUrl,
        signInDescription = stringResource(Res.string.settings_mal_sign_in_description),
        finishSignInLabel = "Finish MyAnimeList sign in",
        approvalDescription = "Approve Anivio in your browser, then return to the app.",
        connectLabel = stringResource(Res.string.settings_mal_connect),
        openLoginLabel = "Open MyAnimeList login",
        disconnectLabel = "Disconnect MyAnimeList",
        missingCredentialsMessage = "MyAnimeList client ID is not configured.",
        errorMessage = malErrorMessage(uiState.error),
        websiteLabel = stringResource(Res.string.settings_mal_website),
        websiteUrl = MAL_WEBSITE_URL,
        onConnectRequested = MalAuthSettings::onConnectRequested,
        onResumeAuthorization = {
            MalAuthSettings.pendingAuthorizationUrl() ?: MalAuthSettings.onConnectRequested()
        },
        onCancelAuthorization = MalAuthSettings::onCancelAuthorization,
        onDisconnect = MalAuthSettings::onDisconnectRequested,
        modifier = modifier,
    )
}

@Composable
private fun TrackingProviderCard(
    brand: TrackingBrand,
    mode: TrackingConnectionCardMode,
    credentialsConfigured: Boolean,
    isLoading: Boolean,
    connectedLabel: String,
    connectedDescription: String,
    signInDescription: String,
    finishSignInLabel: String,
    approvalDescription: String,
    connectLabel: String,
    openLoginLabel: String,
    disconnectLabel: String,
    missingCredentialsMessage: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    statusMessage: String? = null,
    errorMessage: String? = null,
    websiteLabel: String? = null,
    websiteUrl: String? = null,
    onConnectRequested: () -> String?,
    onResumeAuthorization: () -> String?,
    onCancelAuthorization: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val uriHandler = LocalUriHandler.current
    val failedOpenBrowserMessage = stringResource(Res.string.settings_tracking_failed_open_browser)
    var browserError by rememberSaveable { mutableStateOf(false) }
    var showDisconnectDialog by rememberSaveable { mutableStateOf(false) }

    fun openUrl(url: String?) {
        if (url.isNullOrBlank()) return
        browserError = false
        runCatching { uriHandler.openUri(url) }
            .onFailure { browserError = true }
    }

    Box(
        modifier = modifier
            .clip(tokens.shapes.card)
            .background(brand.cardBrush(), tokens.shapes.card)
            .border(
                width = tokens.borders.hairline,
                color = Color.White.copy(alpha = 0.2f),
                shape = tokens.shapes.card,
            ),
    ) {
        TrackingBrandGlyph(
            brand = brand,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(150.dp)
                .alpha(0.08f),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (mode == TrackingConnectionCardMode.CONNECTED) 20.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TrackingBrandWordmark(
                brand = brand,
                contentDescription = brand.displayName,
            )

            when (mode) {
                TrackingConnectionCardMode.CONNECTED -> {
                    TrackingConnectedIdentity(
                        label = connectedLabel,
                        description = connectedDescription,
                        avatarUrl = avatarUrl,
                    )
                }

                TrackingConnectionCardMode.AWAITING_APPROVAL -> {
                    Text(
                        text = finishSignInLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = approvalDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                    TrackingBrandPrimaryButton(
                        label = openLoginLabel,
                        loading = isLoading,
                        enabled = !isLoading,
                        onClick = { openUrl(onResumeAuthorization()) },
                    )
                    OutlinedButton(
                        onClick = onCancelAuthorization,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.44f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.45f),
                        ),
                    ) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                }

                TrackingConnectionCardMode.DISCONNECTED -> {
                    Text(
                        text = signInDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                    TrackingBrandPrimaryButton(
                        label = connectLabel,
                        loading = isLoading,
                        enabled = credentialsConfigured && !isLoading,
                        onClick = { openUrl(onConnectRequested()) },
                    )
                    if (!credentialsConfigured) {
                        TrackingBrandMessage(
                            text = missingCredentialsMessage,
                            isError = brand != TrackingBrand.MAL,
                        )
                    }
                }
            }

            statusMessage?.takeIf(String::isNotBlank)?.let { message ->
                TrackingBrandMessage(text = message, isError = false)
            }
            errorMessage?.takeIf(String::isNotBlank)?.let { message ->
                TrackingBrandMessage(text = message, isError = true)
            }
            if (browserError) {
                TrackingBrandMessage(text = failedOpenBrowserMessage, isError = true)
            }

            val hasWebsiteAction = !websiteLabel.isNullOrBlank() && !websiteUrl.isNullOrBlank()
            val hasDisconnectAction = mode == TrackingConnectionCardMode.CONNECTED
            val footerActionCount = listOf(hasWebsiteAction, hasDisconnectAction).count { it }
            if (footerActionCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasWebsiteAction) {
                        TextButton(
                            onClick = { openUrl(websiteUrl) },
                            modifier = if (footerActionCount > 1) Modifier.weight(0.95f) else Modifier,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        ) {
                            Text(
                                text = websiteLabel.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (hasDisconnectAction) {
                        TextButton(
                            onClick = { showDisconnectDialog = true },
                            modifier = if (footerActionCount > 1) Modifier.weight(1.05f) else Modifier,
                            enabled = !isLoading,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White.copy(alpha = 0.84f),
                                disabledContentColor = Color.White.copy(alpha = 0.38f),
                            ),
                        ) {
                            Text(
                                text = disconnectLabel,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDisconnectDialog) {
        TrackingDisconnectDialog(
            brand = brand,
            disconnectLabel = disconnectLabel,
            onConfirm = {
                showDisconnectDialog = false
                onDisconnect()
            },
            onDismiss = { showDisconnectDialog = false },
        )
    }
}

@Composable
private fun TrackingConnectedIdentity(
    label: String,
    description: String,
    avatarUrl: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The provider's own avatar, so a connected card looks like the account it is linked to.
        // Absent until the profile query lands, and null for an account with no picture set, so the
        // text block has to stand on its own.
        avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.76f),
            )
        }
    }
}

@Composable
private fun TrackingBrandPrimaryButton(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF171717),
            disabledContainerColor = Color.White.copy(alpha = 0.34f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
    ) {
        if (loading) {
            NuvioLoadingIndicator(
                color = Color(0xFF171717),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
private fun TrackingBrandMessage(
    text: String,
    isError: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isError) TrackingErrorColor.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(NuvioTokens.Radius.md),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) TrackingErrorColor else Color.White.copy(alpha = 0.82f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackingDisconnectDialog(
    brand: TrackingBrand,
    disconnectLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = tokens.components.dialogMaxWidth),
            shape = tokens.shapes.dialog,
            color = tokens.colors.surfaceDialog,
        ) {
            Column(
                modifier = Modifier.padding(tokens.spacing.dialogPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.settings_tracking_disconnect_title, brand.displayName),
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        Res.string.settings_tracking_disconnect_description,
                        brand.displayName,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(disconnectLabel)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TrackingBrandGlyph(
    brand: TrackingBrand,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    when (brand) {
        TrackingBrand.TMDB -> Image(
            painter = integrationLogoPainter(IntegrationLogo.Tmdb),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
        TrackingBrand.MAL -> Image(
            painter = painterResource(Res.drawable.rating_mal),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
        // No bundled AniList asset, so use a themed icon rather than hotlinking their mark.
        TrackingBrand.ANILIST -> Icon(
            imageVector = Icons.Rounded.Animation,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = AniListBrandAccent,
        )
        TrackingBrand.LOCAL -> Icon(
            imageVector = Icons.Rounded.Sync,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = MaterialTheme.nuvio.colors.accent,
        )
    }
}

@Composable
private fun TrackingBrandWordmark(
    brand: TrackingBrand,
    contentDescription: String,
) {
    when (brand) {
        // Neither AniList nor MAL ships a wordmark we bundle, so set the name in type instead of
        // leaving the card header empty.
        TrackingBrand.ANILIST,
        TrackingBrand.MAL,
        -> Text(
            text = brand.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.height(38.dp).wrapContentHeight(Alignment.CenterVertically),
        )
        TrackingBrand.LOCAL,
        TrackingBrand.TMDB,
        -> Unit
    }
}

private fun TrackingBrand.cardBrush(): Brush = when (this) {
    // AniList's own palette: navy ground rising into their signature blue.
    TrackingBrand.ANILIST -> Brush.linearGradient(
        colors = listOf(Color(0xFF11161D), Color(0xFF12405F), Color(0xFF02A9FF)),
    )
    // MyAnimeList's deep blue.
    TrackingBrand.MAL -> Brush.linearGradient(
        colors = listOf(Color(0xFF10141C), Color(0xFF1B2740), Color(0xFF2E51A2)),
    )
    TrackingBrand.LOCAL,
    TrackingBrand.TMDB,
    -> Brush.linearGradient(colors = listOf(Color(0xFF242424), Color(0xFF111111)))
}

@Composable
private fun aniListErrorMessage(error: AniListAuthError?): String? = when (error) {
    null, AniListAuthError.MISSING_CLIENT_ID -> null
    AniListAuthError.INVALID_CALLBACK,
    AniListAuthError.INVALID_CALLBACK_STATE,
    -> stringResource(Res.string.settings_anilist_invalid_callback)
    AniListAuthError.AUTHORIZATION_EXPIRED ->
        stringResource(Res.string.settings_anilist_authorization_expired)
    AniListAuthError.VIEWER_LOOKUP_FAILED ->
        stringResource(Res.string.settings_anilist_sign_in_failed)
    AniListAuthError.AUTHORIZATION_REVOKED ->
        stringResource(Res.string.settings_anilist_authorization_revoked)
}

@Composable
private fun malErrorMessage(error: MalAuthError?): String? = when (error) {
    null, MalAuthError.MISSING_CLIENT_ID -> null
    MalAuthError.INVALID_CALLBACK,
    MalAuthError.INVALID_CALLBACK_STATE,
    -> "The MyAnimeList sign-in callback was invalid. Please try again."
    MalAuthError.AUTHORIZATION_EXPIRED -> "The MyAnimeList sign-in request expired."
    MalAuthError.AUTHORIZATION_REVOKED -> "MyAnimeList authorization was cancelled or revoked."
    MalAuthError.TOKEN_EXCHANGE_FAILED -> "MyAnimeList sign-in failed while exchanging the authorization code."
    MalAuthError.TOKEN_REFRESH_FAILED -> "The MyAnimeList session expired. Please reconnect."
    MalAuthError.VIEWER_LOOKUP_FAILED -> "MyAnimeList connected, but the account profile could not be loaded."
}

private val TrackingErrorColor = Color(0xFFFFDAD6)
private val AniListBrandAccent = Color(0xFF02A9FF)
private const val ANILIST_WEBSITE_URL = "https://anilist.co"
private const val MAL_WEBSITE_URL = "https://myanimelist.net"
