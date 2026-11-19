package com.nuvio.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.profiles.ActiveProfileMiniAvatar
import com.nuvio.app.features.profiles.AvatarRepository
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.profiles.ProfileSwitcherTab
import com.nuvio.app.features.settings.NavBarStyle
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_profile
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal val DesktopSidebarCollapsedWidth = 68.dp
internal val DesktopSidebarExpandedWidth = 192.dp

@Composable
internal fun DesktopSidebar(
    items: List<FloatingNavigationItem>,
    style: NavBarStyle,
    onProfileClick: () -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val expanded = style == NavBarStyle.EXPANDED || (style == NavBarStyle.ADAPTIVE && hovered)
    val width by animateDpAsState(
        targetValue = if (expanded) DesktopSidebarExpandedWidth else DesktopSidebarCollapsedWidth,
        animationSpec = tween(200),
        label = "desktop_sidebar_width",
    )
    val tokens = MaterialTheme.nuvio
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()
    val profile = profileState.activeProfile

    Surface(
        modifier = modifier.width(width).fillMaxHeight().hoverable(hoverSource).zIndex(1f),
        color = tokens.colors.background,
        contentColor = tokens.colors.textPrimary,
    ) {
        Box(Modifier.fillMaxSize()) {
            ProfileSwitcherTab(
                selected = false,
                onClick = onProfileClick,
                onProfileSelected = onProfileSelected,
                onAddProfileRequested = onAddProfileRequested,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp).fillMaxWidth(),
                triggerContent = {
                    DesktopSidebarLabel(
                        label = profile?.name ?: stringResource(Res.string.compose_nav_profile),
                        expanded = expanded,
                        selected = false,
                    ) {
                        ActiveProfileMiniAvatar(profile = profile, avatars = avatars, selected = false, size = 32)
                    }
                },
            )
            Column(Modifier.align(Alignment.Center).fillMaxWidth()) {
                items.forEach { item ->
                    Surface(
                        onClick = item.onClick,
                        color = Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        val iconColor = if (item.selected) tokens.colors.onAccent else tokens.colors.textMuted
                        DesktopSidebarLabel(
                            label = item.label,
                            expanded = expanded,
                            selected = item.selected,
                            startPadding = 9,
                        ) {
                            item.icon?.let {
                                Icon(it, contentDescription = item.label, tint = iconColor, modifier = Modifier.size(22.dp))
                            }
                            item.drawable?.let {
                                Icon(painterResource(it), contentDescription = item.label, tint = iconColor, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSidebarLabel(
    label: String,
    expanded: Boolean,
    selected: Boolean,
    startPadding: Int = 15,
    icon: @Composable () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(start = startPadding.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            color = if (selected) tokens.colors.accent else Color.Transparent,
            shape = RoundedCornerShape(14.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(80)),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(start = 10.dp, end = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) tokens.colors.textPrimary else tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
