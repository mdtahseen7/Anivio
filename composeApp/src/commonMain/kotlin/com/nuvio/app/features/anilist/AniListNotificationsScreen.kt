package com.nuvio.app.features.anilist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import kotlinx.coroutines.launch

/**
 * The signed-in AniList account's notification feed. Rows are grouped visually by "new" state
 * (newer than the last time the feed was cleared) rather than a server flag, because AniList's
 * notification union carries no per-item unread marker.
 */
@Composable
fun AniListNotificationsScreen(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    onBack: () -> Unit,
    onPosterClick: ((mediaId: Int, title: String?) -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val scope = rememberCoroutineScope()
    val uiState by AniListNotificationsRepository.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val selectedCategory = NotificationCategory.entries[selectedTabIndex]

    LaunchedEffect(Unit) {
        AniListAuthRepository.ensureLoaded()
        AniListNotificationsRepository.refresh()
    }

    val signInHint = "Connect AniList in Settings → Tracking to see your notifications here."

    NuvioScreen(
        modifier = modifier.fillMaxSize(),
        horizontalPadding = 0.dp,
        listState = listState,
    ) {
        item {
            NuvioScreenHeader(
                title = "Notifications",
                onBack = onBack,
                actions = {
                    if (uiState.isAuthenticated && uiState.unreadCount > 0) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    AniListNotificationsRepository.markAllRead(
                                        nowEpochSec = EpisodeReleaseDatePlatform.nowEpochMs() / 1000L,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = "Mark all as read",
                                tint = tokens.colors.accent,
                            )
                        }
                    }
                },
            )
        }

        if (uiState.isAuthenticated && uiState.items.isNotEmpty()) {
            item(key = "notif-tabs") {
                NotificationTabs(
                    selectedIndex = selectedTabIndex,
                    onSelect = { selectedTabIndex = it },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

        when {
            !uiState.isAuthenticated -> item {
                EmptyState(text = signInHint)
            }

            uiState.isLoading && uiState.items.isEmpty() -> item {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    NuvioLoadingIndicator()
                }
            }

            uiState.error != null && uiState.items.isEmpty() -> item {
                EmptyState(text = uiState.error ?: "Something went wrong.")
            }

            uiState.items.isEmpty() -> item {
                EmptyState(text = "You're all caught up — no notifications yet.")
            }

            else -> {
                val rows = uiState.items.filter { categoryOf(it.type) == selectedCategory }
                if (rows.isEmpty()) {
                    item(key = "empty-tab") {
                        EmptyState(text = "No ${selectedCategory.title.lowercase()} notifications yet.")
                    }
                } else {
                    items(rows, key = { it.id }) { notification ->
                        val isNew = uiState.isNew(notification)
                        NotificationRow(
                            notification = notification,
                            isNew = isNew,
                            onClick = notification.mediaId?.let { mediaId ->
                                {
                                    onPosterClick?.invoke(mediaId, notification.mediaTitle)
                                }
                            },
                        )
                    }
                }
                if (!uiState.hasReachedEnd) {
                    item(key = "load-more") {
                        LoadMoreButton(
                            isLoading = uiState.isLoadingMore,
                            onClick = { scope.launch { AniListNotificationsRepository.loadMore() } },
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
private fun NotificationRow(
    notification: AniListNotification,
    isNew: Boolean,
    onClick: (() -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = tokens.spacing.screenHorizontal, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left rail: avatar for social notifications, cover for media ones.
        val avatar = notification.userAvatar
        val cover = notification.mediaCover
        Box(modifier = Modifier.size(44.dp)) {
            when {
                avatar != null -> AsyncImage(
                    model = avatar,
                    contentDescription = notification.userName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
                cover != null -> AsyncImage(
                    model = cover,
                    contentDescription = notification.mediaTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                )
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(tokens.colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = typeGlyph(notification.type),
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.colors.accent,
                    )
                }
            }
            if (isNew) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E88E5)),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.userName ?: notification.mediaTitle ?: "AniList",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (notification.episode != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EP ${notification.episode}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.accent,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Text(
                text = describe(notification),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        notification.createdAtEpochSec?.let { created ->
            Text(
                text = relativeTime(created),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textSecondary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alpha(0.8f),
            )
        }
    }
}

@Composable
private fun NotificationTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NotificationCategory.entries.forEachIndexed { index, category ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) tokens.colors.accent.copy(alpha = 0.18f) else Color.Transparent,
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) tokens.colors.accent else tokens.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LoadMoreButton(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            NuvioLoadingIndicator()
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tokens.colors.surface)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Load more",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.screenHorizontal, vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textSecondary,
        )
    }
}

private enum class NotificationCategory(val title: String) {
    EPISODES("New Episodes"),
    ACTIVITY("Activity"),
    FORUM("Forum"),
    UPDATES("Updates"),
}

private fun categoryOf(type: String): NotificationCategory = when (type) {
    "AIRING" -> NotificationCategory.EPISODES
    "THREAD_COMMENT_MENTION", "THREAD_COMMENT_REPLY", "THREAD_COMMENT_SUBSCRIBED",
    "THREAD_COMMENT_LIKE", "THREAD_LIKE" -> NotificationCategory.FORUM
    "MEDIA_DELETION", "MEDIA_DATA_CHANGE", "MEDIA_MERGE" -> NotificationCategory.UPDATES
    // FOLLOWING and every ACTIVITY_* (plus anything unrecognised) is social activity.
    else -> NotificationCategory.ACTIVITY
}

private fun typeGlyph(type: String): String = when (type) {
    "FOLLOWING" -> "+"
    "ACTIVITY_MESSAGE" -> "✉"
    "ACTIVITY_MENTION", "ACTIVITY_REPLY", "ACTIVITY_REPLY_SUBSCRIBED" -> "↩"
    "ACTIVITY_LIKE", "ACTIVITY_REPLY_LIKE" -> "♥"
    "THREAD_COMMENT_MENTION", "THREAD_COMMENT_REPLY", "THREAD_COMMENT_SUBSCRIBED" -> "💬"
    "THREAD_COMMENT_LIKE", "THREAD_LIKE" -> "♥"
    "AIRING" -> "▶"
    "MEDIA_DELETION", "MEDIA_DATA_CHANGE", "MEDIA_MERGE" -> "!"
    else -> "•"
}

private fun describe(notification: AniListNotification): String {
    val show = notification.mediaTitle
    return when (notification.type) {
        "AIRING" -> "Episode ${notification.episode} of $show just aired"
        "FOLLOWING" -> "started following you"
        "ACTIVITY_MESSAGE" -> notification.message ?: "sent you a message"
        "ACTIVITY_MENTION" -> "mentioned you in an activity"
        "ACTIVITY_REPLY" -> "replied to your activity"
        "ACTIVITY_REPLY_SUBSCRIBED" -> "replied to an activity you follow"
        "ACTIVITY_LIKE" -> "liked your activity"
        "ACTIVITY_REPLY_LIKE" -> "liked your reply"
        "THREAD_COMMENT_MENTION" -> "mentioned you in ${notification.context ?: "a thread"}"
        "THREAD_COMMENT_REPLY" -> "replied to you in a thread"
        "THREAD_COMMENT_SUBSCRIBED" -> "commented in a thread you follow"
        "THREAD_COMMENT_LIKE" -> "liked your comment"
        "THREAD_LIKE" -> "liked your thread"
        "MEDIA_DELETION" -> notification.message?.let { "$it was removed from AniList" } ?: "A media entry was removed"
        "MEDIA_DATA_CHANGE" -> notification.message?.let { "Entry changed: $it" } ?: "A media entry changed"
        "MEDIA_MERGE" -> notification.message?.let { "Merged with $it" } ?: "A media entry was merged"
        else -> notification.context ?: notification.message ?: "New activity"
    }
}

private fun relativeTime(createdAtEpochSec: Long): String {
    val nowSec = EpisodeReleaseDatePlatform.nowEpochMs() / 1000L
    val deltaSec = (nowSec - createdAtEpochSec).coerceAtLeast(0)
    return when {
        deltaSec < 60 -> "now"
        deltaSec < 3600 -> "${deltaSec / 60}m"
        deltaSec < 86_400 -> "${deltaSec / 3600}h"
        deltaSec < 604_800 -> "${deltaSec / 86_400}d"
        deltaSec < 2_592_000 -> "${deltaSec / 604_800}w"
        else -> "${deltaSec / 2_592_000}mo"
    }
}
