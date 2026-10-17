package com.nuvio.app.features.details

import com.nuvio.app.features.details.components.DantotsuCommentSort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DantotsuCommentsUiState(
    val mediaId: Int? = null,
    val comments: List<DantotsuComment> = emptyList(),
    val totalPages: Int = 0,
    val page: Int = 0,
    val sort: DantotsuCommentSort = DantotsuCommentSort.NEWEST,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isPosting: Boolean = false,
    val errorMessage: String? = null,
    val signedInUser: DantotsuUser? = null,
)

object DantotsuCommentsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(DantotsuCommentsUiState())
    val uiState: StateFlow<DantotsuCommentsUiState> = _uiState.asStateFlow()

    fun start(mediaId: Int) {
        if (_uiState.value.mediaId == mediaId && _uiState.value.comments.isNotEmpty()) return
        _uiState.value = DantotsuCommentsUiState(mediaId = mediaId)
        refresh()
    }

    fun setSort(sort: DantotsuCommentSort) {
        if (_uiState.value.sort == sort) return
        _uiState.update { it.copy(sort = sort, comments = emptyList(), page = 0, totalPages = 0) }
        refresh()
    }

    fun refresh() {
        val mediaId = _uiState.value.mediaId ?: return
        if (_uiState.value.isLoading) return
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                DantotsuCommentsApi.comments(mediaId, page = 0, sort = _uiState.value.sort)
            }.onSuccess { page ->
                _uiState.update {
                    it.copy(
                        comments = page.comments,
                        totalPages = page.totalPages,
                        page = 0,
                        isLoading = false,
                        signedInUser = DantotsuCommentsApi.cachedUser(),
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "Unable to load comments.")
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val mediaId = state.mediaId ?: return
        if (state.isLoadingMore || state.isLoading || state.totalPages <= state.page + 1) return
        scope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            runCatching {
                DantotsuCommentsApi.comments(mediaId, state.page + 1, state.sort)
            }.onSuccess { page ->
                _uiState.update {
                    it.copy(
                        comments = it.comments + page.comments,
                        page = state.page + 1,
                        totalPages = page.totalPages,
                        isLoadingMore = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoadingMore = false, errorMessage = error.message) }
            }
        }
    }

    fun post(content: String, parentCommentId: Int? = null, tag: Int? = null) {
        val state = _uiState.value
        val mediaId = state.mediaId ?: return
        val clean = content.trim()
        if (clean.isBlank() || clean.length > 300 || state.isPosting) return
        val user = state.signedInUser ?: DantotsuCommentsApi.cachedUser() ?: return
        scope.launch {
            _uiState.update { it.copy(isPosting = true, errorMessage = null) }
            runCatching {
                DantotsuCommentsApi.create(
                    mediaId = mediaId,
                    userId = user.id,
                    content = clean,
                    parentCommentId = parentCommentId,
                    tag = tag,
                )
            }.onSuccess { comment ->
                _uiState.update { current ->
                    if (parentCommentId == null) {
                        current.copy(comments = listOf(comment) + current.comments, isPosting = false)
                    } else {
                        current.copy(
                            comments = current.comments.map { parent ->
                                if (parent.id == parentCommentId) {
                                    parent.copy(
                                        replyCount = parent.replyCount + 1,
                                        replies = parent.replies + comment,
                                    )
                                } else parent
                            },
                            isPosting = false,
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isPosting = false, errorMessage = error.message ?: "Unable to post comment.") }
            }
        }
    }

    fun edit(commentId: Int, content: String) {
        val clean = content.trim()
        if (clean.isBlank() || clean.length > 300) return
        scope.launch {
            runCatching { DantotsuCommentsApi.edit(commentId, clean) }
                .onSuccess { success -> if (success) updateComment(commentId) { it.copy(content = clean) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun delete(commentId: Int) {
        scope.launch {
            runCatching { DantotsuCommentsApi.delete(commentId) }
                .onSuccess { success -> if (success) updateComment(commentId) { it.copy(deleted = true, content = "Comment deleted") } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun vote(commentId: Int, score: Int) {
        if (score !in -1..1 || score == 0) return
        scope.launch {
            runCatching { DantotsuCommentsApi.vote(commentId, score) }
                .onSuccess { success ->
                    if (success) updateComment(commentId) { comment ->
                        val previous = comment.userVoteType ?: 0
                        val nextTotal = comment.totalVotes + when {
                            previous == score -> -score
                            previous == 0 -> score
                            else -> score * 2
                        }
                        comment.copy(
                            userVoteType = if (previous == score) null else score,
                            totalVotes = nextTotal,
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun loadReplies(commentId: Int) {
        scope.launch {
            runCatching { DantotsuCommentsApi.replies(commentId) }
                .onSuccess { replies -> updateComment(commentId) { it.copy(replies = replies, replyCount = maxOf(it.replyCount, replies.size)) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun report(commentId: Int, reason: String) {
        scope.launch {
            runCatching { DantotsuCommentsApi.report(commentId, reason) }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun ban(username: String) {
        scope.launch {
            runCatching { DantotsuCommentsApi.ban(username) }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    private fun updateComment(id: Int, transform: (DantotsuComment) -> DantotsuComment) {
        _uiState.update { state ->
            state.copy(comments = state.comments.map { parent ->
                when {
                    parent.id == id -> transform(parent)
                    parent.replies.any { it.id == id } -> parent.copy(
                        replies = parent.replies.map { reply -> if (reply.id == id) transform(reply) else reply },
                    )
                    else -> parent
                }
            })
        }
    }
}
