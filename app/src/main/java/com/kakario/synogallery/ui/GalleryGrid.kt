package com.kakario.synogallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kakario.synogallery.AlbumSource
import com.kakario.synogallery.FavSource
import com.kakario.synogallery.Favorites
import com.kakario.synogallery.Item
import com.kakario.synogallery.Palette
import com.kakario.synogallery.Settings
import com.kakario.synogallery.Similar
import com.kakario.synogallery.Source
import com.kakario.synogallery.Syno
import com.kakario.synogallery.TagSource
import com.kakario.synogallery.ViewerStore
import com.kakario.synogallery.VideosSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PAGE = 500

/** 사진을 여러 장 고르는 중인지 (메인 화면 좌우 넘김을 막을 때 씀) */
object SelectionMode {
    var active by mutableStateOf(false)
}

class GalleryViewModel(private val source: Source, private val cacheKey: String) : ViewModel() {
    val items = mutableStateListOf<Item>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var end by mutableStateOf(false)
        private set

    /** 저장해 둔 목록을 보여주는 중, 뒤에서 최신인지 확인 중 */
    var refreshing by mutableStateOf(false)
        private set
    private var rawOffset = 0

    var sortVersion by mutableStateOf(0)
        private set

    /** 선택된 사진 id */
    val selected = mutableStateMapOf<Long, Unit>()

    init {
        if (source is FavSource) {
            items.addAll(Favorites.list())
            end = true
            ensureSimilar()
        } else {
            viewModelScope.launch {
                // 1) 폰에 저장해 둔 목록을 먼저 바로 보여줌
                val cached = com.kakario.synogallery.ItemCache.load(cacheKey)
                if (cached != null && cached.items.isNotEmpty()) {
                    items.addAll(cached.items)
                    end = true
                    ensureSimilar()
                    // 2) 뒤에서 바뀐 게 있는지만 가볍게 확인
                    refresh(cached)
                } else loadAll()
            }
        }
    }

    private fun filterPage(page: List<Item>) =
        when (if (source is VideosSource) "video" else Settings.mediaFilter) {
            "photo" -> page.filter { !it.isVideo }
            "video" -> page.filter { it.isVideo }
            else -> page
        }

    /** 처음: 끝까지 이어서 불러오고 폰에 저장 */
    private fun loadAll() {
        if (loading || end) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                while (!end) {
                    val page = Syno.listItems(source, rawOffset, PAGE)
                    rawOffset += page.size
                    items.addAll(filterPage(page))
                    if (page.size < PAGE) end = true
                    ensureSimilar()
                }
                com.kakario.synogallery.ItemCache.save(cacheKey, rawOffset, items.toList())
            } catch (e: Exception) {
                error = e.message ?: "불러오지 못했어요."
            } finally {
                loading = false
            }
        }
    }

    /** 개수와 맨 앞 사진들이 같으면 그대로, 다르면 새로 받아서 한 번에 바꿈 */
    private suspend fun refresh(c: com.kakario.synogallery.ItemCache.Cached) {
        refreshing = true
        try {
            val count = try {
                Syno.countItems(source)
            } catch (_: Exception) {
                -1
            }
            val first = filterPage(Syno.listItems(source, 0, 60))
            val head = c.items.take(first.size)
            val same = count >= 0 && count == c.serverCount && first.size == head.size &&
                first.indices.all { first[it].id == head[it].id && first[it].cacheKey == head[it].cacheKey }
            if (same) return
            val fresh = ArrayList<Item>(maxOf(count, 0))
            var off = 0
            while (true) {
                val page = Syno.listItems(source, off, PAGE)
                off += page.size
                fresh += filterPage(page)
                if (page.size < PAGE) break
            }
            items.clear()
            items.addAll(fresh)
            sortVersion++
            com.kakario.synogallery.ItemCache.save(cacheKey, off, fresh)
            ensureSimilar()
        } catch (_: Exception) {
            // 연결이 안 되면 저장해 둔 목록 그대로 보여줌
        } finally {
            refreshing = false
        }
    }

    /** 직접 새로 고침 */
    fun forceRefresh() {
        if (refreshing || loading) return
        viewModelScope.launch { refresh(com.kakario.synogallery.ItemCache.Cached(-2, emptyList())) }
    }

    fun retry() {
        error = null
        loadAll()
    }

    fun ensureSimilar() {
        if (!Settings.groupSimilar || Settings.sortBy != "takentime" || items.isEmpty()) return
        Similar.ensure(cacheKey, items.toList())
    }


    /** 모든 동영상 안에서 정렬 (이름·크기·길이·날짜) */
    fun sortLocal(mode: String) {
        val sorted = when (mode) {
            "date_asc" -> items.sortedBy { it.time }
            "name" -> items.sortedBy { it.filename.lowercase() }
            "size" -> items.sortedByDescending { it.size }
            "length" -> items.sortedByDescending { it.duration }
            else -> items.sortedByDescending { it.time }
        }
        items.clear()
        items.addAll(sorted)
        sortVersion++
    }

    fun removeIds(ids: Collection<Long>) {
        val set = ids.toSet()
        items.removeAll { it.id in set }
        selected.clear()
    }
}

private sealed interface GridRow {
    val key: String
    val month: String

    /** 이 줄이 속한 날짜 구역 제목 */
    val section: String
}

private data class HeaderRow(val label: String, override val month: String, val count: Int) : GridRow {
    override val key get() = "h_$label"
    override val section get() = label
}

/** index: 대표 사진 위치, members: 묶인 사진 위치들 (묶음이 아니면 1개) */
private data class CellRow(
    val index: Int,
    val id: Long,
    val members: List<Int>,
    override val month: String,
    override val section: String,
) : GridRow {
    override val key get() = "c_${index}_$id"
}

private fun buildRows(items: List<Item>, grouping: String, groups: List<List<Int>>?): List<GridRow> {
    val out = ArrayList<GridRow>(items.size + 64)
    var last: String? = null
    var headerAt = -1
    var count = 0
    val units = groups ?: items.indices.map { listOf(it) }
    fun closeHeader() {
        if (headerAt >= 0) {
            val h = out[headerAt] as HeaderRow
            out[headerAt] = h.copy(count = count)
        }
    }
    for (g in units) {
        val first = items.getOrNull(g.first()) ?: continue
        val month = monthLabel(first.time)
        var label = ""
        if (grouping != "none") {
            label = when (grouping) {
                "day" -> dayLabel(first.time)
                "year" -> yearLabel(first.time)
                else -> month
            }
            if (label != last) {
                closeHeader()
                out += HeaderRow(label, month, 0)
                headerAt = out.lastIndex
                count = 0
                last = label
            }
        }
        count += g.size
        out += CellRow(g.first(), first.id, g, month, label)
    }
    closeHeader()
    return out
}

fun galleryKey(source: Source) =
    "${source.key}_${Settings.sharedSpace}_${Settings.sortBy}_${Settings.sortDesc}_${Settings.mediaFilter}" +
        if (source is FavSource) "_${Favorites.version}" else ""

/** 두 손가락 오므리기/벌리기로 열 개수 조절 */
private fun Modifier.pinchColumns(onChange: (Int) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                if (zoom > 1.35f) {
                    onChange(-1); zoom = 1f
                } else if (zoom < 0.74f) {
                    onChange(+1); zoom = 1f
                }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * 길게 누른 뒤 끌어서 여러 장 고르기 (구글 포토 방식).
 * 길게 누르기 전까지는 아무것도 가로채지 않아서 일반 스크롤·탭은 그대로 동작한다.
 */
private fun Modifier.longPressDrag(
    key: Any,
    onStart: (Offset) -> Boolean,
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit,
) = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var stop = false
            while (!stop) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == down.id }
                if (e.changes.count { it.pressed } > 1 || c == null || !c.pressed ||
                    (c.position - down.position).getDistance() > slop || c.isConsumed
                ) stop = true
            }
            true
        }
        if (early != null) return@awaitEachGesture
        if (!onStart(down.position)) return@awaitEachGesture
        try {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                e.changes.forEach { it.consume() }
                if (!c.pressed) break
                onMove(c.position)
            }
        } finally {
            onEnd()
        }
    }
}

@Composable
fun GalleryGrid(
    source: Source,
    nav: NavController,
    modifier: Modifier = Modifier,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val gKey = galleryKey(source)
    val vm: GalleryViewModel = viewModel(key = gKey) { GalleryViewModel(source, gKey) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val stack = Settings.groupSimilar && Settings.sortBy == "takentime" && source !is VideosSource
    val simVersion = Similar.version
    val simLevel = Settings.similarLevel
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val cols = Settings.columns + if (wide) 2 else 0
    val showTags = Settings.showTags && source !is TagSource
    val videoSort = Settings.videoSort
    val isVideos = source is VideosSource
    val grouping = if (isVideos && !videoSort.startsWith("date")) "none" else Settings.grouping
    LaunchedEffect(videoSort, vm.end, isVideos) { if (isVideos && vm.end) vm.sortLocal(videoSort) }

    LaunchedEffect(stack, Syno.sessionTick) { if (stack) vm.ensureSimilar() }

    val rows = remember(vm.items.size, vm.sortVersion, grouping, stack, if (stack) simVersion else 0, simLevel) {
        val list = vm.items.toList()
        buildRows(list, grouping, if (stack) Similar.groups(list) else null)
    }
    val keyToRow = remember(rows) { HashMap<Any, Int>(rows.size * 2).apply { rows.forEachIndexed { i, r -> put(r.key, i) } } }
    val state = rememberLazyGridState()
    val currentRows by rememberUpdatedState(rows)
    val currentKeyToRow by rememberUpdatedState(keyToRow)

    // 뷰어에서 돌아오면 마지막으로 본 사진 근처로
    LaunchedEffect(Unit) {
        val idx = ViewerStore.lastIndex
        if (idx >= 0 && ViewerStore.sourceKey == gKey) {
            var ord = -1
            val row = currentRows.indexOfFirst { if (it is CellRow) ord++; it is CellRow && ord == idx }
            if (row >= 0 && state.layoutInfo.visibleItemsInfo.none { it.index == row }) state.scrollToItem(row)
            ViewerStore.lastIndex = -1
        }
    }

    val selecting = vm.selected.isNotEmpty()
    BackHandler(enabled = selecting) { vm.selected.clear() }
    DisposableEffect(selecting) {
        SelectionMode.active = selecting
        onDispose { SelectionMode.active = false }
    }

    var albumDialog by remember { mutableStateOf<List<Long>?>(null) }
    var tagDialog by remember { mutableStateOf<List<Long>?>(null) }
    var removeAsk by remember { mutableStateOf<List<Long>?>(null) }

    fun membersOf(r: CellRow): List<Item> = r.members.mapNotNull { vm.items.getOrNull(it) }
    fun idsOf(r: CellRow): List<Long> = r.members.mapNotNull { vm.items.getOrNull(it)?.id }

    // ─── 끌어서 고르기 상태 ───
    var dragAnchor by remember { mutableStateOf(-1) }
    var dragLast by remember { mutableStateOf(-1) }
    var dragSelect by remember { mutableStateOf(true) }
    var dragBase by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pointer by remember { mutableStateOf<Offset?>(null) }

    fun rowAt(p: Offset): Int? {
        val hit = state.layoutInfo.visibleItemsInfo.firstOrNull {
            p.x >= it.offset.x && p.x < it.offset.x + it.size.width &&
                p.y >= it.offset.y && p.y < it.offset.y + it.size.height
        } ?: return null
        val r = currentKeyToRow[hit.key] ?: return null
        return if (currentRows.getOrNull(r) is CellRow) r else null
    }

    fun applyRange(to: Int) {
        if (dragAnchor < 0 || to == dragLast) return
        dragLast = to
        val (a, b) = if (dragAnchor <= to) dragAnchor to to else to to dragAnchor
        val range = HashSet<Long>()
        for (i in a..b) (currentRows.getOrNull(i) as? CellRow)?.let { range += idsOf(it) }
        val next = if (dragSelect) dragBase + range else dragBase - range
        vm.selected.keys.filter { it !in next }.forEach { vm.selected.remove(it) }
        next.forEach { vm.selected[it] = Unit }
    }

    // 손가락이 위·아래 가장자리에 가까울수록 빠르게 자동 스크롤
    LaunchedEffect(dragAnchor >= 0) {
        if (dragAnchor < 0) return@LaunchedEffect
        val edge = with(density) { 110.dp.toPx() }
        val maxSpeed = with(density) { 46.dp.toPx() }
        while (dragAnchor >= 0) {
            withFrameNanos { }
            val p = pointer ?: continue
            val h = state.layoutInfo.viewportSize.height.toFloat()
            val depth = when {
                p.y < edge -> -((edge - p.y) / edge)
                p.y > h - edge -> (p.y - (h - edge)) / edge
                else -> 0f
            }.coerceIn(-1.6f, 1.6f)
            if (depth != 0f) {
                val speed = maxSpeed * depth * abs(depth)
                state.scrollBy(speed)
                rowAt(Offset(p.x, p.y.coerceIn(1f, h - 1f)))?.let { applyRange(it) }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols.coerceAtLeast(1)),
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .longPressDrag(
                    key = vm,
                    onStart = { p ->
                        val r = rowAt(p)
                        val cell = r?.let { currentRows[it] as? CellRow }
                        if (r == null || cell == null) {
                            false
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val ids = idsOf(cell)
                            dragSelect = !(ids.isNotEmpty() && ids.all { it in vm.selected })
                            dragBase = vm.selected.keys.toSet()
                            dragAnchor = r
                            dragLast = -1
                            pointer = p
                            applyRange(r)
                            true
                        }
                    },
                    onMove = { p ->
                        pointer = p
                        rowAt(p)?.let { applyRange(it) }
                    },
                    onEnd = { dragAnchor = -1; pointer = null },
                )
                .pinchColumns { d -> Settings.columns = (Settings.columns + d).coerceIn(1, 8) },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            header?.invoke(this)

            if (stack && Similar.pending > 0) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "similar_progress") {
                    Text(
                        "비슷한 사진 찾는 중… 남은 ${Similar.pending}장 (처음 한 번만)",
                        color = Palette.sub, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            items(
                count = rows.size,
                key = { rows[it].key },
                span = { if (rows[it] is HeaderRow) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                contentType = { if (rows[it] is HeaderRow) 0 else 1 },
            ) { i ->
                when (val r = rows[i]) {
                    is HeaderRow -> SectionHeader(r, state)
                    is CellRow -> {
                        val item = vm.items.getOrNull(r.index) ?: return@items
                        val memberIds = idsOf(r)
                        val isSel = memberIds.isNotEmpty() && memberIds.all { it in vm.selected }
                        Thumb(
                            item = item,
                            stackCount = r.members.size,
                            selecting = selecting,
                            selected = isSel,
                            showTags = showTags,
                            onClick = {
                                if (selecting) {
                                    if (isSel) memberIds.forEach { vm.selected.remove(it) }
                                    else memberIds.forEach { vm.selected[it] = Unit }
                                } else {
                                    // 구글 포토처럼: 묶음은 한 장처럼 넘어가고, 안쪽 사진은 아래 띠에서 고름
                                    val cells = rows.filterIsInstance<CellRow>()
                                    ViewerStore.stacks = cells.map { membersOf(it) }.filter { it.isNotEmpty() }
                                    ViewerStore.sourceKey = gKey
                                    nav.navigate("viewer/${cells.indexOf(r).coerceAtLeast(0)}")
                                }
                            },
                        )
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                when {
                    vm.error != null -> MessageBox(vm.error!!, "다시 시도") { vm.retry() }
                    vm.loading && vm.items.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                    vm.end && vm.items.isEmpty() -> MessageBox(
                        if (source is FavSource) "즐겨찾기가 없어요.\n사진을 길게 눌러 고른 뒤 ♡를 누르면 여기에 모여요." else "사진이 없어요."
                    )
                    source is FavSource -> Box(Modifier.padding(bottom = 90.dp))
                    else -> Text(
                        when {
                            vm.loading -> "${vm.items.size}개 불러오는 중…"
                            vm.refreshing -> "${vm.items.size}개 · 새 사진 확인 중…"
                            else -> "${vm.items.size}개 · 눌러서 새로 고침"
                        },
                        color = Palette.sub,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.forceRefresh() }
                            .padding(top = 20.dp, bottom = 90.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (grouping != "none") StickyDate(state, rows, keyToRow)

        MonthScroller(state, rows, Modifier.align(Alignment.TopEnd))

        // 선택 모드 하단 바
        AnimatedVisibility(
            selecting, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                color = Palette.card,
                modifier = Modifier.navigationBarsPadding().padding(10.dp),
            ) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.selected.clear() }) { Icon(Icons.Filled.Close, "선택 취소") }
                    Text("${vm.selected.size}", modifier = Modifier.padding(end = 6.dp), fontWeight = FontWeight.Bold)
                    val ids = vm.selected.keys.toList()
                    val selItems = vm.items.filter { it.id in vm.selected }
                    val allFav = Favorites.version.let { Favorites.allFav(selItems) }
                    ActionButton(if (allFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (allFav) "해제" else "즐겨찾기") {
                        if (allFav) {
                            Favorites.remove(ids)
                            toast(ctx, "즐겨찾기에서 ${ids.size}장을 뺐어요")
                        } else {
                            Favorites.add(selItems)
                            toast(ctx, "즐겨찾기에 ${ids.size}장을 넣었어요")
                        }
                        vm.selected.clear()
                    }
                    ActionButton(Icons.Filled.LibraryAdd, "앨범") { albumDialog = ids }
                    ActionButton(Icons.AutoMirrored.Filled.Label, "태그") { tagDialog = ids }
                    when (source) {
                        is AlbumSource -> ActionButton(Icons.Filled.RemoveCircleOutline, "빼기") { removeAsk = ids }
                        is TagSource -> ActionButton(Icons.Filled.RemoveCircleOutline, "태그 빼기") { removeAsk = ids }
                        else -> {}
                    }
                }
            }
        }
    }

    removeAsk?.let { ids ->
        ConfirmDialog(
            title = if (source is AlbumSource) "앨범에서 빼기" else "태그 빼기",
            text = "${ids.size}장을 ${if (source is AlbumSource) "이 앨범에서" else "이 태그에서"} 뺄까요? 사진 자체는 지워지지 않아요.",
            confirm = "빼기",
            onDismiss = { removeAsk = null },
            onConfirm = {
                removeAsk = null
                when (source) {
                    is AlbumSource -> scope.launch {
                        try {
                            Syno.removeFromAlbum(source.id, ids)
                            vm.removeIds(ids)
                            toast(ctx, "앨범에서 ${ids.size}장을 뺐어요")
                        } catch (e: Exception) {
                            toast(ctx, e.message ?: "실패했어요")
                        }
                    }
                    is TagSource -> scope.launch {
                        try {
                            Syno.removeTags(ids, listOf(source.id))
                            vm.removeIds(ids)
                            toast(ctx, "${ids.size}장에서 태그를 뺐어요")
                        } catch (e: Exception) {
                            toast(ctx, e.message ?: "실패했어요")
                        }
                    }
                    else -> {}
                }
            },
        )
    }

    albumDialog?.let { ids ->
        AlbumPickerDialog(ids, onDone = { albumDialog = null; vm.selected.clear() }, onDismiss = { albumDialog = null })
    }
    tagDialog?.let { ids ->
        TagEditorDialog(ids, current = null, onDone = { tagDialog = null; vm.selected.clear() }, onDismiss = { tagDialog = null })
    }
}

/** 목록 속 큰 날짜 제목 — 화면 위쪽에 가까워질수록 조금씩 작아짐 */
@Composable
private fun SectionHeader(r: HeaderRow, state: LazyGridState) {
    val density = LocalDensity.current
    val zone = with(density) { 140.dp.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 22.dp, bottom = 8.dp)
            .graphicsLayer {
                val y = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == r.key }?.offset?.y?.toFloat() ?: zone
                val t = (y / zone).coerceIn(0f, 1f)
                val sc = 0.72f + 0.28f * t
                scaleX = sc
                scaleY = sc
                alpha = 0.55f + 0.45f * t
                transformOrigin = TransformOrigin(0f, 1f)
            },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(r.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Palette.fg)
        Text(
            "${r.count}장", color = Palette.sub, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp, bottom = 3.dp),
        )
    }
}

/** 화면 위에 작게 붙어 있는 현재 날짜 — 다음 날짜 제목이 올라오면 밀려 올라감 */
@Composable
private fun StickyDate(state: LazyGridState, rows: List<GridRow>, keyToRow: Map<Any, Int>) {
    val density = LocalDensity.current
    val boxH = with(density) { 40.dp.toPx() }
    val info by remember(rows, keyToRow) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo
            val firstIdx = visible.indexOfFirst { keyToRow.containsKey(it.key) }
            if (firstIdx < 0) return@derivedStateOf null
            val first = visible[firstIdx]
            val row = rows.getOrNull(keyToRow[first.key] ?: -1) ?: return@derivedStateOf null
            // 맨 위 큰 제목이 아직 보이면 작은 표시는 숨김
            if (row is HeaderRow && first.offset.y > -first.size.height / 2) return@derivedStateOf null
            val next = visible.drop(firstIdx + 1).firstOrNull { v ->
                v.offset.y > 0 && rows.getOrNull(keyToRow[v.key] ?: -1) is HeaderRow
            }
            row.section to (next?.offset?.y ?: Int.MAX_VALUE)
        }
    }
    val v = info ?: return
    if (v.first.isEmpty()) return
    Box(
        Modifier
            .padding(start = 10.dp, top = 6.dp)
            .graphicsLayer {
                val push = v.second.toFloat() - boxH
                translationY = if (push < 0f) push else 0f
            }
            .background(Palette.card.copy(alpha = 0.94f), RoundedCornerShape(14.dp))
            .border(1.dp, Palette.divider, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(v.first, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Palette.fg)
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.fg)
    }
}

/** 오른쪽 가장자리 손잡이를 끌어 월 단위로 빠르게 이동 */
@Composable
private fun MonthScroller(state: LazyGridState, rows: List<GridRow>, modifier: Modifier) {
    if (rows.size < 60) return
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    var show by remember { mutableStateOf(false) }
    val first by remember { derivedStateOf { state.firstVisibleItemIndex } }
    val currentRows by rememberUpdatedState(rows)

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { moving -> if (moving) show = true }
    }
    LaunchedEffect(show, dragging, state.isScrollInProgress) {
        if (show && !dragging && !state.isScrollInProgress) {
            delay(1800)
            show = false
        }
    }

    val frac = if (dragging) dragFrac else first.toFloat() / (rows.size - 1).coerceAtLeast(1)
    val density = LocalDensity.current
    val thumbH = 52.dp
    val visible = show || dragging

    BoxWithConstraints(modifier.fillMaxHeight().width(150.dp)) {
        val trackPx = with(density) { (maxHeight - thumbH).toPx() }.coerceAtLeast(1f)
        val y = (frac.coerceIn(0f, 1f) * trackPx).roundToInt()
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)) {
            Box(
                Modifier
                    .offset { IntOffset(0, y) }
                    .padding(end = 4.dp)
                    .size(width = 36.dp, height = thumbH)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                    .pointerInput(trackPx) {
                        detectVerticalDragGestures(
                            onDragStart = { dragging = true; dragFrac = first.toFloat() / (currentRows.size - 1).coerceAtLeast(1) },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, dy ->
                            change.consume()
                            dragFrac = (dragFrac + dy / trackPx).coerceIn(0f, 1f)
                            val target = (dragFrac * (currentRows.size - 1)).roundToInt()
                            scope.launch { state.scrollToItem(target) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.UnfoldMore, "월별 이동", tint = Color.White)
            }
        }
        if (dragging) {
            val idx = (frac * (rows.size - 1)).roundToInt().coerceIn(0, rows.lastIndex)
            Surface(
                color = Color(0xEE1B1B1F),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(-with(density) { 50.dp.roundToPx() }, y + with(density) { 10.dp.roundToPx() }) },
            ) {
                Text(
                    rows[idx].month, color = Color.White, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun Thumb(
    item: Item,
    stackCount: Int = 1,
    selecting: Boolean = false,
    selected: Boolean = false,
    showTags: Boolean = false,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val size = Settings.thumbSize
    val square = Settings.squareThumbs
    val req = remember(item.thumbUnitId, item.cacheKey, size, Syno.sessionTick) {
        val key = Syno.thumbKey(item.thumbUnitId, item.cacheKey, size)
        ImageRequest.Builder(ctx)
            .data(Syno.thumbUrl(item.thumbUnitId, item.cacheKey, size))
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .build()
    }
    val fav = Favorites.version.let { Favorites.isFav(item.id) }
    Box(
        Modifier
            .aspectRatio(1f)
            .background(Palette.placeholder)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = req,
            contentDescription = item.filename,
            contentScale = if (square) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(if (selected) 10.dp else 0.dp),
        )
        if (stackCount > 1) {
            // 비슷한 사진 묶음 표시 (구글 포토처럼 숫자)
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.FilterNone, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(
                    "$stackCount", color = Color.White, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 3.dp),
                )
            }
        }
        if (fav && !selecting) {
            Icon(
                Icons.Filled.Favorite, "즐겨찾기", tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp).size(15.dp),
            )
        }
        if (item.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color(0x99000000), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                if (item.duration > 0) {
                    Text(formatDuration(item.duration), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (showTags && item.tags.isNotEmpty()) {
            Text(
                "#" + item.tags.first() + if (item.tags.size > 1) " +${item.tags.size - 1}" else "",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 3.dp, end = if (item.isVideo) 48.dp else 3.dp, bottom = if (Settings.showFilename) 20.dp else 3.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (Settings.showFilename) {
            Text(
                item.filename,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0x88000000))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (selecting) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(if (selected) Color.White else Color(0x55000000), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
    }
}
