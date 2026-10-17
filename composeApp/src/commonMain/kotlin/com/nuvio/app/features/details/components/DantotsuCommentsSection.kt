package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.features.details.DantotsuComment
import com.nuvio.app.features.details.DantotsuCommentsRepository
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.anilist.parseAniListMediaId
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.nuvio
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class DantotsuCommentSort(val apiValue: Int, val label: String) {
    NEWEST(0, "Newest"),
    OLDEST(1, "Oldest"),
    HIGHEST_RATED(2, "Highest rated"),
}

@Composable
fun DantotsuCommentsSection(
    meta: MetaDetails,
    modifier: Modifier = Modifier,
) {
    val mediaId = parseAniListMediaId(meta.id)
    if (mediaId == null) {
        NuvioSurfaceCard(modifier = modifier) {
            Text("Comments are unavailable for this title because it has no AniList media id.")
        }
        return
    }

    val state by DantotsuCommentsRepository.uiState.collectAsStateWithLifecycle()
    var editingComment by remember(mediaId) { mutableStateOf<DantotsuComment?>(null) }
    var showRules by remember(mediaId) { mutableStateOf(false) }

    LaunchedEffect(mediaId) {
        DantotsuCommentsRepository.start(mediaId)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Community",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${state.comments.size} comments · AniList community",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showRules = true }) {
                Icon(Icons.Rounded.Info, contentDescription = "Open rules")
            }
            IconButton(onClick = DantotsuCommentsRepository::refresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh comments")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Sort, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            DantotsuCommentSort.entries.forEach { sort ->
                FilterChip(
                    selected = state.sort == sort,
                    onClick = { DantotsuCommentsRepository.setSort(sort) },
                    label = { Text(sort.label) },
                )
            }
        }

        state.errorMessage?.let { error ->
            NuvioSurfaceCard {
                Text(error, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    DantotsuCommentsRepository.clearError()
                    DantotsuCommentsRepository.refresh()
                }) { Text("Retry") }
            }
        }

        if (state.isLoading && state.comments.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (!state.isLoading && state.comments.isEmpty() && state.errorMessage == null) {
            NuvioSurfaceCard {
                Text("No comments yet. Start the conversation.")
            }
        } else {
            state.comments.forEach { comment ->
                DantotsuCommentCard(
                    comment = comment,
                    currentUser = state.signedInUser,
                    onVote = DantotsuCommentsRepository::vote,
                    onReply = DantotsuCommentsRepository::setReplyingTo,
                    onEdit = { editingComment = it },
                    onDelete = DantotsuCommentsRepository::delete,
                    onReport = { DantotsuCommentsRepository.report(it, "Reported from Anivio") },
                    onBan = DantotsuCommentsRepository::ban,
                    onLoadReplies = DantotsuCommentsRepository::loadReplies,
                )
            }
            if (state.totalPages > state.page + 1) {
                TextButton(
                    onClick = DantotsuCommentsRepository::loadMore,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoadingMore,
                ) {
                    if (state.isLoadingMore) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text("Load more comments")
                }
            }
        }
    }

    editingComment?.let { comment ->
        var editText by remember(comment.id) { mutableStateOf(comment.content) }
        AlertDialog(
            onDismissRequest = { editingComment = null },
            title = { Text("Edit comment") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it.take(300) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("${editText.length}/300") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    DantotsuCommentsRepository.edit(comment.id, editText)
                    editingComment = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingComment = null }) { Text("Cancel") } },
        )
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
            title = { Text("Community rules") },
            text = {
                Text("Be respectful, avoid harassment and spam, and use spoiler labels when discussing plot details. Comments are limited to 300 characters.")
            },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text("Got it") } },
        )
    }
}

@Composable
fun DantotsuCommentsComposer(
    modifier: Modifier = Modifier,
) {
    val state by DantotsuCommentsRepository.uiState.collectAsStateWithLifecycle()
    var composerText by remember(state.mediaId) { mutableStateOf("") }
    var composerSpoiler by remember(state.mediaId) { mutableStateOf(false) }

    DantotsuComposer(
        modifier = modifier,
        text = composerText,
        onTextChange = { composerText = it.take(300) },
        replyingTo = state.replyingTo,
        enabled = state.signedInUser != null && !state.isPosting,
        spoiler = composerSpoiler,
        onSpoilerChange = { composerSpoiler = it },
        onCancelReply = { DantotsuCommentsRepository.setReplyingTo(null) },
        onSend = {
            DantotsuCommentsRepository.post(
                composerText,
                state.replyingTo?.id,
                if (composerSpoiler) 1 else null,
            )
            composerText = ""
            composerSpoiler = false
            DantotsuCommentsRepository.setReplyingTo(null)
        },
    )
}

@Composable
private fun DantotsuComposer(
    modifier: Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    replyingTo: DantotsuComment?,
    spoiler: Boolean,
    onSpoilerChange: (Boolean) -> Unit,
    enabled: Boolean,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
) {
    var optionsExpanded by remember { mutableStateOf(false) }
    NuvioSurfaceCard(
        modifier = modifier,
        contentPadding = 10.dp,
    ) {
        if (!enabled && replyingTo == null) {
            Text("Connect AniList to join the discussion.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            replyingTo?.let { reply ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Replying to ${reply.username}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onCancelReply) { Icon(Icons.Rounded.Close, contentDescription = "Cancel reply") }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                placeholder = { Text("Write a response…") },
                singleLine = true,
                supportingText = { Text("${text.length}/300") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { optionsExpanded = true }) {
                                Icon(Icons.Rounded.MoreHoriz, contentDescription = "More comment options")
                            }
                            DropdownMenu(
                                expanded = optionsExpanded,
                                onDismissRequest = { optionsExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (spoiler) "Remove spoiler" else "Mark as spoiler") },
                                    onClick = {
                                        onSpoilerChange(!spoiler)
                                        optionsExpanded = false
                                    },
                                )
                            }
                        }
                        IconButton(
                            onClick = onSend,
                            enabled = enabled && text.trim().isNotEmpty(),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = "Post comment")
                        }
                    }
                },
            )
        }
    }
}

private fun formatCommentTimestamp(raw: String): String {
    val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return raw
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(local.date)
        append(' ')
        append(local.time.hour.toString().padStart(2, '0'))
        append(':')
        append(local.time.minute.toString().padStart(2, '0'))
    }
}

@Composable
private fun DantotsuCommentCard(
    comment: DantotsuComment,
    currentUser: com.nuvio.app.features.details.DantotsuUser?,
    onVote: (Int, Int) -> Unit,
    onReply: (DantotsuComment) -> Unit,
    onEdit: (DantotsuComment) -> Unit,
    onDelete: (Int) -> Unit,
    onReport: (Int) -> Unit,
    onBan: (String) -> Unit,
    onLoadReplies: (Int) -> Unit,
) {
    var menuExpanded by remember(comment.id) { mutableStateOf(false) }
    var repliesExpanded by remember(comment.id) { mutableStateOf(false) }
    var spoilerRevealed by remember(comment.id) { mutableStateOf(comment.tag == null) }
    val canModerate = currentUser?.isAdmin == true || currentUser?.isMod == true
    val isOwner = currentUser?.id == comment.userId

    NuvioSurfaceCard {
        Row(verticalAlignment = Alignment.Top) {
            CommentAvatar(comment)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.username.ifBlank { "Anonymous" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (comment.isAdmin) Badge("Admin", MaterialTheme.colorScheme.primary)
                    if (comment.isMod) Badge("Mod", MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Comment actions") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Reply") }, onClick = { menuExpanded = false; onReply(comment) })
                            if (isOwner) {
                                DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onEdit(comment) })
                                DropdownMenuItem(text = { Text("Delete comment") }, onClick = { menuExpanded = false; onDelete(comment.id) })
                            } else {
                                DropdownMenuItem(text = { Text("Report comment") }, onClick = { menuExpanded = false; onReport(comment.id) })
                            }
                            if (canModerate) {
                                DropdownMenuItem(text = { Text("Ban user") }, onClick = { menuExpanded = false; onBan(comment.username) })
                            }
                        }
                    }
                }
                Text(formatCommentTimestamp(comment.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (comment.tag != null) {
                    Text("Spoiler", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                if (comment.tag != null && !spoilerRevealed) {
                    Text(
                        "Spoiler · tap to reveal",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { spoilerRevealed = true }
                            .padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    CommentContent(comment.content)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VoteButton(Icons.Rounded.ThumbUp, comment.userVoteType == 1) { onVote(comment.id, 1) }
                    Text("${comment.totalVotes}", style = MaterialTheme.typography.labelMedium)
                    VoteButton(Icons.Rounded.ThumbDown, comment.userVoteType == -1) { onVote(comment.id, -1) }
                    if (comment.replyCount > 0) {
                        TextButton(onClick = {
                            repliesExpanded = !repliesExpanded
                            if (repliesExpanded && comment.replies.isEmpty()) onLoadReplies(comment.id)
                        }) {
                            Icon(Icons.Rounded.AddComment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${comment.replyCount} replies")
                        }
                    }
                }
            }
        }
        if (repliesExpanded) {
            comment.replies.forEach { reply ->
                Spacer(Modifier.height(10.dp))
                DantotsuCommentCard(
                    comment = reply,
                    currentUser = currentUser,
                    onVote = onVote,
                    onReply = onReply,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onReport = onReport,
                    onBan = onBan,
                    onLoadReplies = onLoadReplies,
                )
            }
        }
    }
}

@Composable
private fun CommentContent(content: String) {
    val uriHandler = LocalUriHandler.current
    val url = Regex("https?://\\S+").find(content)?.value?.trimEnd('.', ',', ')', ']', '}')
    val isKlipyMedia = url?.contains("static.klipy.co/", ignoreCase = true) == true &&
        url.substringBefore('?').lowercase().let { it.endsWith(".gif") || it.endsWith(".webp") || it.endsWith(".png") }
    val isKlipyPage = url?.contains("klipy.com/", ignoreCase = true) == true

    when {
        isKlipyMedia -> AsyncImage(
            model = url,
            contentDescription = "Comment GIF",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
        isKlipyPage -> Text(
            text = "Open GIF on Klipy",
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { uriHandler.openUri(url) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        else -> Text(content, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CommentAvatar(comment: DantotsuComment) {
    Box(
        modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!comment.profilePictureUrl.isNullOrBlank()) {
            AsyncImage(
                model = comment.profilePictureUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(comment.username.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Badge(label: String, color: Color) {
    Text(
        label,
        modifier = Modifier.padding(start = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun VoteButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
