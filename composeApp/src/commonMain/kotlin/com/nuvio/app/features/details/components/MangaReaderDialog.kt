package com.nuvio.app.features.details.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.anilist.recordAniListMangaChapterRead
import com.nuvio.app.features.details.MangaReaderSettingsRepository
import com.nuvio.app.features.details.MangaReadingMode
import com.nuvio.app.features.details.MangaReaderBookmark
import com.nuvio.app.features.details.MangaReaderProgressRepository
import com.nuvio.app.features.details.mangaReaderProgressKey
import com.nuvio.app.features.plugins.MangaPluginChapter
import com.nuvio.app.features.plugins.MangaPluginPage
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.PluginScraper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.nuvio.app.features.player.EnterImmersivePlayerMode

private const val CONTROLS_AUTO_HIDE_MS = 3000L
private const val READER_SHEET_SCRIM = 0xFF16181D
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Single reader implementation, rendered fullscreen at the details screen root
 * via [MangaReaderOverlay]. Owns page loading, completion reporting and
 * dismissal so every entry point shares one behavior.
 */
@Composable
internal fun MangaReaderHost(request: MangaReaderRequest) {
    var pages by remember(request.chapter.id, request.provider?.id, request.localPageUris) {
        mutableStateOf(emptyList<MangaPluginPage>())
    }
    var loadingPages by remember(request.chapter.id, request.provider?.id, request.localPageUris) {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(request.chapter.id, request.provider?.id, request.localPageUris) {
        val localPages = request.localPageUris
        if (localPages != null) {
            pages = localPages.map(::MangaPluginPage)
            loadingPages = false
            return@LaunchedEffect
        }
        val provider = request.provider ?: run {
            pages = emptyList()
            loadingPages = false
            return@LaunchedEffect
        }
        loadingPages = true
        pages = PluginRepository.getMangaChapterPages(provider, request.chapter.id)
            .getOrDefault(emptyList())
            .filter { it.url.isNotBlank() }
        loadingPages = false
    }
    PlatformBackHandler(enabled = true, onBack = MangaReaderOverlay::close)
    val progressKey = mangaReaderProgressKey(
        metaId = request.metaId,
        providerId = request.providerId,
        chapterId = request.chapter.id,
    )
    MangaReaderDialog(
        title = request.title,
        sourceName = request.provider?.name ?: "Offline",
        chapter = request.chapter,
        chapters = request.chapters,
        pages = pages,
        loading = loadingPages,
        progressKey = progressKey,
        onDismiss = MangaReaderOverlay::close,
        onSelectChapter = MangaReaderOverlay::selectChapter,
        onChapterCompleted = { completed ->
            scope.launch { recordAniListMangaChapterRead(request.metaId, completed.number) }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MangaReaderDialog(
    title: String,
    sourceName: String,
    chapter: MangaPluginChapter,
    chapters: List<MangaPluginChapter>,
    pages: List<MangaPluginPage>,
    loading: Boolean,
    progressKey: String,
    onDismiss: () -> Unit,
    onSelectChapter: (MangaPluginChapter) -> Unit,
    onChapterCompleted: (MangaPluginChapter) -> Unit,
) {
    MangaReaderSettingsRepository.ensureLoaded()
    // Reuse the existing platform immersive abstraction. Android hides system bars;
    // iOS keeps the reader active while this overlay is mounted.
    EnterImmersivePlayerMode(keepScreenAwake = false)
    val readerSettings by MangaReaderSettingsRepository.uiState.collectAsStateWithLifecycle()
    val mode = readerSettings.readingMode
    val isPager = mode != MangaReadingMode.WEBTOON

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    var controlsVisible by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    // Pages currently pinched past 1x. The pager locks swiping while any is zoomed so
    // pan gestures never fight page turns.
    val zoomedPages = remember { mutableStateListOf<Int>() }
    var positionRestored by remember(progressKey) { mutableStateOf(false) }
    var restoredMode by remember(progressKey) { mutableStateOf<MangaReadingMode?>(null) }
    var completionReported by remember(progressKey) { mutableStateOf(false) }
    var bookmarkRevision by remember(progressKey) { mutableStateOf(0) }
    val latestPositionRestored by androidx.compose.runtime.rememberUpdatedState(positionRestored)

    fun currentPosition(forMode: MangaReadingMode = mode): Pair<Int, Int> = if (forMode == MangaReadingMode.WEBTOON) {
        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
    } else {
        pagerState.currentPage to 0
    }

    fun saveCurrentPosition(forMode: MangaReadingMode = mode) {
        if (!latestPositionRestored || pages.isEmpty()) return
        val (pageIndex, offset) = currentPosition(forMode)
        MangaReaderProgressRepository.save(progressKey, pageIndex, offset)
    }

    LaunchedEffect(progressKey, pages.size, mode) {
        zoomedPages.clear()
        if (pages.isEmpty()) {
            positionRestored = false
            return@LaunchedEffect
        }

        if (positionRestored && restoredMode != null && restoredMode != mode) {
            saveCurrentPosition(restoredMode ?: mode)
        }

        val saved = MangaReaderProgressRepository.progressFor(progressKey)
        val pageIndex = saved?.pageIndex?.coerceIn(0, pages.lastIndex) ?: 0
        if (isPager) {
            pagerState.scrollToPage(pageIndex)
        } else {
            listState.scrollToItem(pageIndex, saved?.webtoonOffset?.coerceAtLeast(0) ?: 0)
        }
        restoredMode = mode
        positionRestored = true
        controlsVisible = true
    }

    LaunchedEffect(progressKey, pages.size, mode, positionRestored) {
        if (!positionRestored || pages.isEmpty()) return@LaunchedEffect
        snapshotFlow { currentPosition(mode) }
            .distinctUntilChanged()
            .collect { (pageIndex, offset) ->
                MangaReaderProgressRepository.save(progressKey, pageIndex, offset)
            }
    }

    DisposableEffect(progressKey, mode, pages.size) {
        onDispose {
            if (latestPositionRestored && pages.isNotEmpty()) {
                val (pageIndex, offset) = currentPosition(mode)
                MangaReaderProgressRepository.save(progressKey, pageIndex, offset)
            }
        }
    }

    // Focus mode: controls hide a few seconds after the last tap or page turn.
    val currentPage = if (isPager) {
        (pagerState.currentPage + 1).coerceAtMost(pages.size.coerceAtLeast(1))
    } else {
        (listState.firstVisibleItemIndex + 1).coerceAtMost(pages.size.coerceAtLeast(1))
    }
    val currentOffset = if (isPager) 0 else listState.firstVisibleItemScrollOffset
    val webtoonAtEnd = if (isPager || pages.isEmpty()) {
        false
    } else {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        last != null && last.size > 0 && last.index == pages.lastIndex &&
            last.offset + last.size <= listState.layoutInfo.viewportEndOffset
    }
    LaunchedEffect(controlsVisible, currentPage, chapter.id, pages.size) {
        if (!controlsVisible) return@LaunchedEffect
        delay(CONTROLS_AUTO_HIDE_MS)
        controlsVisible = false
    }
    fun reportChapterCompletedOnce() {
        if (completionReported || pages.isEmpty()) return
        completionReported = true
        val (pageIndex, offset) = currentPosition()
        MangaReaderProgressRepository.save(progressKey, pageIndex, offset, completed = true)
        onChapterCompleted(chapter)
    }

    LaunchedEffect(currentPage, webtoonAtEnd, pages.size, progressKey, positionRestored) {
        if (positionRestored && pages.isNotEmpty() && (isPager && currentPage == pages.size || !isPager && webtoonAtEnd)) {
            reportChapterCompletedOnce()
        }
    }

    fun toggleCurrentBookmark() {
        MangaReaderProgressRepository.toggleBookmark(progressKey, currentPage - 1, currentOffset)
        bookmarkRevision++
        controlsVisible = true
    }

    fun handlePageTap(fraction: Float) {
        if (isPager) {
            val advance = if (mode == MangaReadingMode.RIGHT_TO_LEFT) fraction < 0.30f else fraction > 0.70f
            when {
                fraction < 0.30f || fraction > 0.70f -> scope.launch {
                    val target = if (advance) pagerState.currentPage + 1 else pagerState.currentPage - 1
                    pagerState.animateScrollToPage(target.coerceIn(0, pages.lastIndex))
                    controlsVisible = true
                }
                else -> {
                    controlsVisible = !controlsVisible
                }
            }
        } else {
            val viewport = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset)
                .coerceAtLeast(1)
            when {
                fraction < 0.25f -> scope.launch {
                    listState.animateScrollBy(-viewport * 0.85f)
                    controlsVisible = true
                }
                fraction > 0.75f -> scope.launch {
                    listState.animateScrollBy(viewport * 0.85f)
                    controlsVisible = true
                }
                else -> controlsVisible = !controlsVisible
            }
        }
    }
    val bookmarkCount = remember(progressKey, bookmarkRevision, currentPage, currentOffset) {
        MangaReaderProgressRepository.bookmarksFor(progressKey).size
    }
    val currentBookmarked = remember(progressKey, bookmarkRevision, currentPage, currentOffset) {
        MangaReaderProgressRepository.isBookmarked(progressKey, currentPage - 1, currentOffset)
    }

    val chapterIndex = chapters.indexOfFirst { it.id == chapter.id }
    val previous = chapters.getOrNull(chapterIndex - 1)
    val next = chapters.getOrNull(chapterIndex + 1)

    // Advancing to a later chapter counts the current one as read even when the
    // last page was never reached (picker jump, next button). Backward moves
    // never report.
    fun goToChapter(target: MangaPluginChapter) {
        val currentNumber = chapterNumberOf(chapter)
        val targetNumber = chapterNumberOf(target)
        if (currentNumber != null && targetNumber != null && targetNumber > currentNumber) {
            reportChapterCompletedOnce()
        }
        saveCurrentPosition()
        onSelectChapter(target)
    }

    fun selectPreviousChapter(target: MangaPluginChapter) {
        saveCurrentPosition()
        onSelectChapter(target)
    }

    // In-window content (rendered by MangaReaderHost inside the details screen's
    // own root Box): no Dialog window, so no platform dialog width, no dim and no
    // separate-window touch handling.
    Box(modifier = Modifier.fillMaxSize()) {
                when {
                    loading -> ReaderStatus("Loading chapter pages…")
                    pages.isEmpty() -> ReaderStatus("This provider returned no pages for this chapter.")
                    isPager -> HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = mode == MangaReadingMode.RIGHT_TO_LEFT,
                        userScrollEnabled = zoomedPages.isEmpty(),
                        key = { index -> "${pages[index].url}-$index" },
                    ) { index ->
                        ZoomableMangaPage(
                            page = pages[index],
                            onTap = ::handlePageTap,
                            onZoomChanged = { zoomed ->
                                if (zoomed) {
                                    if (index !in zoomedPages) zoomedPages.add(index)
                                } else {
                                    zoomedPages.remove(index)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                    ) {
                        itemsIndexed(pages, key = { index, page -> "${page.url}-$index" }) { index, page ->
                            ZoomableMangaPage(
                                page = page,
                                onTap = ::handlePageTap,
                                onZoomChanged = { zoomed ->
                                    if (zoomed) {
                                        if (index !in zoomedPages) zoomedPages.add(index)
                                    } else {
                                        zoomedPages.remove(index)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    ReaderTopBar(
                        title = title,
                        chapter = chapter,
                        sourceName = sourceName,
                        chapters = chapters,
                        onDismiss = onDismiss,
                        onSelectChapter = ::goToChapter,
                        onSettings = { settingsOpen = true },
                        bookmarked = currentBookmarked,
                        bookmarkCount = bookmarkCount,
                        onToggleBookmark = ::toggleCurrentBookmark,
                    )
                }

                AnimatedVisibility(
                    visible = controlsVisible && pages.isNotEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    ReaderBottomBar(
                        currentPage = currentPage,
                        totalPages = pages.size,
                        previous = previous,
                        next = next,
                        rightToLeft = mode == MangaReadingMode.RIGHT_TO_LEFT,
                        onPrevious = { previous?.let(::selectPreviousChapter) },
                        onNext = { next?.let(::goToChapter) },
                    )
                }
            }

    if (settingsOpen) {
        ReaderSettingsSheet(
            bookmarks = MangaReaderProgressRepository.bookmarksFor(progressKey),
            onDismiss = { settingsOpen = false },
            onSelectBookmark = { bookmark ->
                if (pages.isNotEmpty()) {
                    scope.launch {
                        if (isPager) pagerState.animateScrollToPage(bookmark.pageIndex.coerceIn(0, pages.lastIndex))
                        else listState.animateScrollToItem(
                            bookmark.pageIndex.coerceIn(0, pages.lastIndex),
                            bookmark.webtoonOffset,
                        )
                        settingsOpen = false
                    }
                }
            },
        )
    }
}

/**
 * One manga page with pinch-to-zoom and double-tap zoom. Reports zoom state so the
 * pager can lock swiping while zoomed; zoom resets whenever the url changes.
 */
@Composable
private fun ZoomableMangaPage(
    page: MangaPluginPage,
    onTap: (Float) -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val url = page.url
    val platformContext = LocalPlatformContext.current
    var scale by remember(url) { mutableStateOf(MIN_ZOOM) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var size by remember(url) { mutableStateOf(IntSize.Zero) }
    var retryAttempt by remember(url) { mutableStateOf(0) }

    fun applyZoom(next: Float) {
        val clamped = next.coerceIn(MIN_ZOOM, MAX_ZOOM)
        scale = clamped
        if (clamped <= MIN_ZOOM) offset = Offset.Zero
        onZoomChanged(clamped > MIN_ZOOM)
    }

    val transformable = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        scale = next
        offset = if (next <= MIN_ZOOM) Offset.Zero else offset + panChange
        onZoomChanged(next > MIN_ZOOM)
    }
    val imageRequest = remember(url, page.headers, retryAttempt) {
        ImageRequest.Builder(platformContext)
            .data(url)
            .httpHeaders(
                NetworkHeaders.Builder().apply {
                    page.headers.forEach { (name, value) ->
                        if (name.isNotBlank() && value.isNotBlank()) set(name, value)
                    }
                }.build(),
            )
            // Keep provider headers in the cache identity so a cached anonymous response
            // cannot mask an authenticated image request.
            .memoryCacheKey("manga:$url:${page.headers.toSortedMap()}:$retryAttempt")
            .diskCacheKey("manga:$url:${page.headers.toSortedMap()}:$retryAttempt")
            .build()
    }
    AsyncImage(
        model = imageRequest,
        contentDescription = null,
        modifier = modifier
            .onSizeChanged { size = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            // Let the pager/list win ordinary one-finger drags at 1x. The
            // transform gesture still receives pinch gestures, and takes over
            // panning after the page has been zoomed.
            .transformable(
                state = transformable,
                canPan = { scale > MIN_ZOOM },
            )
            .pointerInput(url) {
                detectTapGestures(
                    onTap = {
                        if (size.width > 0) onTap((it.x / size.width).coerceIn(0f, 1f))
                    },
                    onDoubleTap = { if (scale > MIN_ZOOM) applyZoom(MIN_ZOOM) else applyZoom(DOUBLE_TAP_ZOOM) },
                )
            },
        contentScale = ContentScale.FillWidth,
        onError = { if (retryAttempt < 2) retryAttempt++ },
    )
}

@Composable
private fun ReaderStatus(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    chapter: MangaPluginChapter,
    sourceName: String,
    chapters: List<MangaPluginChapter>,
    onDismiss: () -> Unit,
    onSelectChapter: (MangaPluginChapter) -> Unit,
    onSettings: () -> Unit,
    bookmarked: Boolean,
    bookmarkCount: Int,
    onToggleBookmark: () -> Unit,
) {
    var chapterMenuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                ),
            )
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close reader", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Box {
                    Text(
                        text = "Chapter ${chapter.number}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.pointerInput(chapter.id) {
                            detectTapGestures(onTap = { chapterMenuOpen = !chapterMenuOpen })
                        },
                    )
                    ChapterPickerDropdown(
                        expanded = chapterMenuOpen,
                        chapter = chapter,
                        chapters = chapters,
                        onDismiss = { chapterMenuOpen = false },
                        onSelectChapter = { chapterMenuOpen = false; onSelectChapter(it) },
                    )
                }
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = sourceName,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 4.dp),
            )
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark page",
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.MoreVert, "Reader settings${if (bookmarkCount > 0) ", $bookmarkCount bookmarks" else ""}", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    currentPage: Int,
    totalPages: Int,
    previous: MangaPluginChapter?,
    next: MangaPluginChapter?,
    rightToLeft: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val progress = if (totalPages == 0) 0f else currentPage.toFloat() / totalPages
    val previousIcon = if (rightToLeft) Icons.Rounded.KeyboardArrowRight else Icons.Rounded.KeyboardArrowLeft
    val nextIcon = if (rightToLeft) Icons.Rounded.KeyboardArrowLeft else Icons.Rounded.KeyboardArrowRight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                ),
            )
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalIconButton(onClick = onPrevious, enabled = previous != null) {
                Icon(previousIcon, "Previous chapter", tint = Color.White)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Page $currentPage of $totalPages",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.18f),
                )
            }
            FilledTonalIconButton(onClick = onNext, enabled = next != null) {
                Icon(nextIcon, "Next chapter", tint = Color.White)
            }
        }
    }
}

/**
 * Range-first chapter picker: opens on the current chapter's 1–10 / 11–20 / …
 * range, with a back entry to the full range list. Pure (data in, callbacks
 * out) so it renders in Compose previews with fake chapters.
 */
@Composable
internal fun ChapterPickerDropdown(
    expanded: Boolean,
    chapter: MangaPluginChapter,
    chapters: List<MangaPluginChapter>,
    onDismiss: () -> Unit,
    onSelectChapter: (MangaPluginChapter) -> Unit,
) {
    val ranges = remember(chapters) { buildChapterRanges(chapters) }
    // The picker always opens on the range list so every 1–10 / 11–20 / … page is
    // visible first; picking a range drills into its chapters.
    var pickerRangeIndex by remember(expanded, chapter.id) {
        mutableStateOf(-1)
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Color(READER_SHEET_SCRIM),
    ) {
        if (ranges.isEmpty()) {
            chapters.forEach { option ->
                DropdownMenuItem(
                    text = { Text("Chapter ${option.number}", color = Color.White) },
                    onClick = { onSelectChapter(option) },
                )
            }
        } else if (pickerRangeIndex !in ranges.indices) {
            ranges.forEachIndexed { index, (range, rangeChapters) ->
                DropdownMenuItem(
                    text = { Text("Chapters ${range.label} (${rangeChapters.size})", color = Color.White) },
                    trailingIcon = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                        )
                    },
                    onClick = { pickerRangeIndex = index },
                )
            }
        } else {
            val (range, rangeChapters) = ranges[pickerRangeIndex]
            DropdownMenuItem(
                text = {
                    Text(
                        "← ${range.label}",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                },
                onClick = { pickerRangeIndex = -1 },
            )
            rangeChapters.forEach { option ->
                val isCurrent = option.id == chapter.id
                DropdownMenuItem(
                    text = {
                        Text(
                            "Chapter ${option.number}",
                            fontWeight = if (isCurrent) FontWeight.Bold else null,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White
                            },
                        )
                    },
                    onClick = { onSelectChapter(option) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    bookmarks: List<MangaReaderBookmark>,
    onDismiss: () -> Unit,
    onSelectBookmark: (MangaReaderBookmark) -> Unit,
) {
    val readerSettings by MangaReaderSettingsRepository.uiState.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(READER_SHEET_SCRIM),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Reading mode",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            MangaReadingMode.entries.forEach { mode ->
                val selected = readerSettings.readingMode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton) {
                            MangaReaderSettingsRepository.setReadingMode(mode)
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mode.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                        Text(
                            text = mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            Text(
                "Bookmarks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (bookmarks.isEmpty()) {
                Text(
                    "Bookmark a page with the ribbon button while reading.",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                bookmarks.forEach { bookmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectBookmark(bookmark) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Page ${bookmark.pageIndex + 1}${if (bookmark.webtoonOffset > 0) " · offset ${bookmark.webtoonOffset}" else ""}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            Text(
                "Pinch or double-tap a page to zoom. Tap a page to hide or reveal controls.",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

private val MangaReadingMode.title: String
    get() = when (this) {
        MangaReadingMode.WEBTOON -> "Webtoon"
        MangaReadingMode.LEFT_TO_RIGHT -> "Left to right"
        MangaReadingMode.RIGHT_TO_LEFT -> "Right to left"
    }

private val MangaReadingMode.description: String
    get() = when (this) {
        MangaReadingMode.WEBTOON -> "Continuous vertical scroll, one long strip."
        MangaReadingMode.LEFT_TO_RIGHT -> "Swipe pages left to right."
        MangaReadingMode.RIGHT_TO_LEFT -> "Classic manga direction, swipe right to left."
    }

@Preview
@Composable
private fun ChapterPickerDropdownPreview() {
    val chapters = remember {
        (1..65).map { n -> MangaPluginChapter(id = "ch-$n", number = n.toString()) }
    }
    val current = remember { chapters.first { it.number == "58" } }
    NuvioTheme(darkTheme = true) {
        Surface(color = Color(0xFF0D0E12)) {
            Box(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Chapter ${current.number}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                ChapterPickerDropdown(
                    expanded = true,
                    chapter = current,
                    chapters = chapters,
                    onDismiss = {},
                    onSelectChapter = {},
                )
            }
        }
    }
}
