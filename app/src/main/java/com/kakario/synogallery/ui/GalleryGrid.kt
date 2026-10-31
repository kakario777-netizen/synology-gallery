package com.kakario.synogallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.kakario.synogallery.Item
import com.kakario.synogallery.Settings
import com.kakario.synogallery.Similar
import com.kakario.synogallery.Source
import com.kakario.synogallery.Syno
import com.kakario.synogallery.TagSource
import com.kakario.synogallery.ViewerStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PAGE = 500

class GalleryViewModel(private val source: Source) : ViewModel() {
    val items = mutableStateListOf<Item>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var end by mutableStateOf(false)
        private set
    private var rawOffset = 0
    private var similarJob: Job? = null

    /** 선택된 사진 id */
    val selected = mutableStateMapOf<Long, Unit>()

    init {
        loadAll()
    }

    /** 월별 이동 스크롤이 전체 기간을 다룰 수 있게 끝까지 이어서 불러옴 */
    private fun loadAll() {
        if (loading || end) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                while (!end) {
                    val page = Syno.listItems(source, rawOffset, PAGE)
                    rawOffset += page.size
                    items.addAll(
                        when (Settings.mediaFilter) {
                            "photo" -> page.filter { !it.isVideo }
                            "video" -> page.filter { it.isVideo }
                            else -> page
                        }
                    )
                    if (page.size < PAGE) end = true
                    ensureSimilar()
                }
            } catch (e: Exception) {
                error = e.message ?: "불러오지 못했어요."
            } finally {
                loading = false
            }
        }
    }

    fun retry() {
        error = null
        loadAll()
    }

    fun ensureSimilar() {
        if (!Settings.groupSimilar || Settings.sortBy != "takentime") return
        if (similarJob?.isActive == true) return
        similarJob = viewModelScope.launch {
            while (true) {
                val c = Similar.candidates(items.toList())
                if (c.isEmpty()) break
                Similar.compute(c)
            }
        }
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
}

private data class HeaderRow(val label: String, override val month: String) : GridRow {
    override val key get() = "h_$label"
}

/** index: 대표 사진 위치, members: 묶인 사진 위치들 (묶음이 아니면 1개) */
private data class CellRow(val index: Int, val id: Long, val members: List<Int>, override val month: String) : GridRow {
    override val key get() = "c_${index}_$id"
}

private fun buildRows(items: List<Item>, grouping: String, groups: List<List<Int>>?): List<GridRow> {
    val out = ArrayList<GridRow>(items.size + 64)
    var last: String? = null
    val units = groups ?: items.indices.map { listOf(it) }
    for (g in units) {
        val first = items.getOrNull(g.first()) ?: continue
        val month = monthLabel(first.time)
        if (grouping != "none") {
            val label = if (grouping == "day") dayLabel(first.time) else month
            if (label != last) {
                out += HeaderRow(label, month)
                last = label
            }
        }
        out += CellRow(g.first(), first.id, g, month)
    }
    return out
}

fun galleryKey(source: Source) =
    "${source.key}_${Settings.sharedSpace}_${Settings.sortBy}_${Settings.sortDesc}_${Settings.mediaFilter}"

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryGrid(
    source: Source,
    nav: NavController,
    modifier: Modifier = Modifier,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val gKey = galleryKey(source)
    val vm: GalleryViewModel = viewModel(key = gKey) { GalleryViewModel(source) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val grouping = Settings.grouping
    val stack = Settings.groupSimilar && Settings.sortBy == "takentime"
    val simVersion = Similar.version
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val cols = Settings.columns + if (wide) 2 else 0

    LaunchedEffect(stack) { if (stack) vm.ensureSimilar() }

    val rows = remember(vm.items.size, grouping, stack, if (stack) simVersion else 0) {
        val list = vm.items.toList()
        buildRows(list, grouping, if (stack) Similar.groups(list) else null)
    }
    val state = rememberLazyGridState()
    val currentRows by rememberUpdatedState(rows)

    // 뷰어에서 돌아오면 마지막으로 본 사진 근처로
    LaunchedEffect(Unit) {
        val idx = ViewerStore.lastIndex
        if (idx >= 0 && ViewerStore.sourceKey == gKey) {
            val row = currentRows.indexOfFirst { it is CellRow && idx in it.members }
            if (row >= 0 && state.layoutInfo.visibleItemsInfo.none { it.index == row }) state.scrollToItem(row)
            ViewerStore.lastIndex = -1
        }
    }

    val selecting = vm.selected.isNotEmpty()
    BackHandler(enabled = selecting) { vm.selected.clear() }

    var albumDialog by remember { mutableStateOf<List<Long>?>(null) }
    var tagDialog by remember { mutableStateOf<List<Long>?>(null) }

    fun membersOf(r: CellRow): List<Item> = r.members.mapNotNull { vm.items.getOrNull(it) }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols.coerceAtLeast(1)),
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .pinchColumns { d -> Settings.columns = (Settings.columns + d).coerceIn(1, 8) },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            header?.invoke(this)

            if (stack && Similar.pending > 0) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "similar_progress") {
                    Text(
                        "비슷한 사진 찾는 중… 남은 ${Similar.pending}장",
                        color = Color.Gray, style = MaterialTheme.typography.labelMedium,
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
                    is HeaderRow -> Text(
                        r.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
                    )
                    is CellRow -> {
                        val item = vm.items.getOrNull(r.index) ?: return@items
                        val memberIds = r.members.mapNotNull { vm.items.getOrNull(it)?.id }
                        val isSel = memberIds.isNotEmpty() && memberIds.all { it in vm.selected }
                        Thumb(
                            item = item,
                            stackCount = r.members.size,
                            selecting = selecting,
                            selected = isSel,
                            onClick = {
                                if (selecting) {
                                    if (isSel) memberIds.forEach { vm.selected.remove(it) }
                                    else memberIds.forEach { vm.selected[it] = Unit }
                                } else if (r.members.size > 1) {
                                    // 묶음: 묶인 사진만 넘겨보기
                                    ViewerStore.items = membersOf(r)
                                    ViewerStore.sourceKey = ""
                                    nav.navigate("viewer/0")
                                } else {
                                    ViewerStore.items = vm.items.toList()
                                    ViewerStore.sourceKey = gKey
                                    nav.navigate("viewer/${r.index}")
                                }
                            },
                            onLongClick = { memberIds.forEach { vm.selected[it] = Unit } },
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
                    vm.end && vm.items.isEmpty() -> MessageBox("사진이 없어요.")
                    else -> Text(
                        if (vm.loading) "${vm.items.size}개 불러오는 중…" else "${vm.items.size}개",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 90.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        MonthScroller(state, rows, Modifier.align(Alignment.TopEnd))

        // 선택 모드 하단 바
        AnimatedVisibility(
            selecting, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier.navigationBarsPadding().padding(12.dp),
            ) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.selected.clear() }) { Icon(Icons.Filled.Close, "선택 취소") }
                    Text("${vm.selected.size}개", modifier = Modifier.padding(end = 4.dp), fontWeight = FontWeight.SemiBold)
                    val ids = vm.selected.keys.toList()
                    ActionButton(Icons.Filled.LibraryAdd, "앨범") { albumDialog = ids }
                    ActionButton(Icons.AutoMirrored.Filled.Label, "태그") { tagDialog = ids }
                    when (source) {
                        is AlbumSource -> ActionButton(Icons.Filled.RemoveCircleOutline, "빼기") {
                            scope.launch {
                                try {
                                    Syno.removeFromAlbum(source.id, ids)
                                    vm.removeIds(ids)
                                    toast(ctx, "앨범에서 ${ids.size}장을 뺐어요")
                                } catch (e: Exception) {
                                    toast(ctx, e.message ?: "실패했어요")
                                }
                            }
                        }
                        is TagSource -> ActionButton(Icons.Filled.RemoveCircleOutline, "태그 빼기") {
                            scope.launch {
                                try {
                                    Syno.removeTags(ids, listOf(source.id))
                                    vm.removeIds(ids)
                                    toast(ctx, "${ids.size}장에서 태그를 뺐어요")
                                } catch (e: Exception) {
                                    toast(ctx, e.message ?: "실패했어요")
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    albumDialog?.let { ids ->
        AlbumPickerDialog(ids, onDone = { albumDialog = null; vm.selected.clear() }, onDismiss = { albumDialog = null })
    }
    tagDialog?.let { ids ->
        TagEditorDialog(ids, current = null, onDone = { tagDialog = null; vm.selected.clear() }, onDismiss = { tagDialog = null })
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 4.dp))
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

    // 스크롤할 때 나타났다가 멈추면 잠시 뒤 사라짐
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { moving ->
            if (moving) show = true
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumb(
    item: Item,
    stackCount: Int = 1,
    selecting: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val size = Settings.thumbSize
    val square = Settings.squareThumbs
    val req = remember(item.thumbUnitId, item.cacheKey, size) {
        val key = Syno.thumbKey(item.thumbUnitId, item.cacheKey, size)
        ImageRequest.Builder(ctx)
            .data(Syno.thumbUrl(item.thumbUnitId, item.cacheKey, size))
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .build()
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .background(Color(0xFFEFEFF2))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
