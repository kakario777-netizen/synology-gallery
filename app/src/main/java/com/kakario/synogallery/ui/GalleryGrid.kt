package com.kakario.synogallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kakario.synogallery.Item
import com.kakario.synogallery.Settings
import com.kakario.synogallery.Source
import com.kakario.synogallery.Syno
import com.kakario.synogallery.ViewerStore
import kotlinx.coroutines.launch

private const val PAGE = 200

class GalleryViewModel(private val source: Source) : ViewModel() {
    val items = mutableStateListOf<Item>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var end by mutableStateOf(false)
        private set
    private var rawOffset = 0

    init {
        loadMore()
    }

    fun loadMore() {
        if (loading || end) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val page = Syno.listItems(source, rawOffset, PAGE)
                rawOffset += page.size
                val filter = Settings.mediaFilter
                items.addAll(
                    when (filter) {
                        "photo" -> page.filter { !it.isVideo }
                        "video" -> page.filter { it.isVideo }
                        else -> page
                    }
                )
                if (page.size < PAGE) end = true
            } catch (e: Exception) {
                error = e.message ?: "불러오지 못했어요."
            } finally {
                loading = false
            }
        }
    }

    fun retry() {
        error = null
        loadMore()
    }
}

private sealed interface GridRow {
    val key: String
}

private data class HeaderRow(val label: String) : GridRow {
    override val key get() = "h_$label"
}

private data class CellRow(val index: Int, val id: Long) : GridRow {
    override val key get() = "c_${index}_$id"
}

private fun buildRows(items: List<Item>, grouping: String): List<GridRow> {
    val out = ArrayList<GridRow>(items.size + 32)
    var last: String? = null
    items.forEachIndexed { i, it ->
        if (grouping != "none") {
            val label = if (grouping == "day") dayLabel(it.time) else monthLabel(it.time)
            if (label != last) {
                out += HeaderRow(label)
                last = label
            }
        }
        out += CellRow(i, it.id)
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

@Composable
fun GalleryGrid(
    source: Source,
    nav: NavController,
    modifier: Modifier = Modifier,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val vm: GalleryViewModel = viewModel(key = galleryKey(source)) { GalleryViewModel(source) }
    val grouping = Settings.grouping
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val cols = Settings.columns + if (wide) 2 else 0
    val rows = remember(vm.items.size, grouping) { buildRows(vm.items, grouping) }
    val state = rememberLazyGridState()
    val currentRows by rememberUpdatedState(rows)

    // 끝에 가까워지면 다음 페이지
    LaunchedEffect(state, vm) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= currentRows.size - 60) vm.loadMore() }
    }

    // 뷰어에서 돌아오면 마지막으로 본 사진 근처로
    val gKey = galleryKey(source)
    LaunchedEffect(Unit) {
        val idx = ViewerStore.lastIndex
        if (idx >= 0 && ViewerStore.sourceKey == gKey) {
            val row = currentRows.indexOfFirst { it is CellRow && it.index == idx }
            if (row >= 0) {
                val visible = state.layoutInfo.visibleItemsInfo
                if (visible.none { it.index == row }) state.scrollToItem(row)
            }
            ViewerStore.lastIndex = -1
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(cols.coerceAtLeast(1)),
        state = state,
        modifier = modifier
            .fillMaxSize()
            .pinchColumns { d -> Settings.columns = (Settings.columns + d).coerceIn(1, 8) },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        header?.invoke(this)

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
                    Thumb(item) {
                        ViewerStore.items = vm.items.toList()
                        ViewerStore.sourceKey = gKey
                        nav.navigate("viewer/${r.index}")
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
            when {
                vm.error != null -> MessageBox(vm.error!!, "다시 시도") { vm.retry() }
                vm.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
                vm.end && vm.items.isEmpty() -> MessageBox("사진이 없어요.")
                vm.end -> Text(
                    "${vm.items.size}개",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun Thumb(item: Item, onClick: () -> Unit) {
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
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = req,
            contentDescription = item.filename,
            contentScale = if (square) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
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
    }
}
