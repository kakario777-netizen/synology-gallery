package com.kakario.synogallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kakario.synogallery.Item
import com.kakario.synogallery.Settings
import com.kakario.synogallery.Syno
import com.kakario.synogallery.ViewerStore
import kotlinx.coroutines.delay
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage

@Composable
fun ViewerScreen(start: Int, nav: NavController) {
    val stacks = ViewerStore.stacks
    val items = stacks
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, items.lastIndex)) { items.size }
    var ui by remember { mutableStateOf(true) }
    var slideshow by remember { mutableStateOf(false) }
    var tagEdit by remember { mutableStateOf<Item?>(null) }
    var albumAdd by remember { mutableStateOf<Item?>(null) }
    var tagsVersion by remember { mutableIntStateOf(0) }
    val pos = remember { Animatable(0f) }   // 0 = 기본, 음수 = 위로(상세), 양수 = 아래로(닫기)
    val scope = rememberCoroutineScope()
    // 묶음마다 지금 보고 있는 사진 번호
    val chosen = remember { mutableStateMapOf<Int, Int>() }
    var strip by remember { mutableStateOf(true) }
    var motionItem by remember { mutableStateOf<Long?>(null) }   // 모션 포토가 있는 사진
    var motionTick by remember { mutableIntStateOf(0) }          // 누를 때마다 다시 재생
    fun itemAt(page: Int): Item {
        val st = stacks[page]
        return st[(chosen[page] ?: 0).coerceIn(0, st.lastIndex)]
    }
    fun play(item: Item) {
        ViewerStore.playItem = item
        nav.navigate("player/0")
    }

    KeepScreenOn(Settings.keepScreenOn || slideshow)
    SystemBars(ui, darkScreen = Settings.viewerBlack)

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { ViewerStore.lastIndex = it }
    }

    LaunchedEffect(slideshow) {
        if (slideshow) ui = false
        while (slideshow) {
            delay(Settings.slideshowSec * 1000L)
            if (pager.currentPage < items.lastIndex) pager.animateScrollToPage(pager.currentPage + 1)
            else slideshow = false
        }
    }

    val bg = if (Settings.viewerBlack) Color.Black else Color.White

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenH = with(density) { maxHeight.toPx() }
        val panelH = screenH * 0.48f
        val dismissAt = with(density) { 120.dp.toPx() }
        val up = (-pos.value / panelH).coerceIn(0f, 1f)
        val down = (pos.value / screenH).coerceIn(0f, 1f)
        val detailsOpen = pos.value <= -panelH * 0.99f

        BackHandler(enabled = up > 0f) { scope.launch { pos.animateTo(0f) } }

        val flingSpeed = with(density) { 900.dp.toPx() }
        val tracker = remember { VelocityTracker() }

        // 튕기듯 빠르게 밀면 거리가 짧아도 닫히거나(아래) 정보가 열림(위)
        fun settle(velocity: Float) {
            scope.launch {
                val v = pos.value
                when {
                    v > dismissAt || (v > 0f && velocity > flingSpeed) -> {
                        pos.animateTo(screenH, tween(160))
                        nav.popBackStack()
                    }
                    v > 0f -> pos.animateTo(0f, spring(dampingRatio = 0.8f))
                    v < -panelH * 0.25f || velocity < -flingSpeed -> pos.animateTo(-panelH, spring(dampingRatio = 0.85f))
                    else -> pos.animateTo(0f, spring(dampingRatio = 0.85f))
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(bg.copy(alpha = 1f - down * 0.85f))
                .pointerInput(panelH) {
                    detectVerticalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = { settle(tracker.calculateVelocity().y) },
                        onDragCancel = { settle(0f) },
                    ) { change, dy ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { pos.snapTo((pos.value + dy).coerceAtLeast(-panelH)) }
                    }
                },
        ) {
            HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val sc = 1f - 0.32f * up - 0.3f * down
                        scaleX = sc
                        scaleY = sc
                        translationY = if (pos.value > 0f) pos.value else -panelH * 0.55f * up
                    },
            ) { page ->
                val item = itemAt(page)
                if (item.isVideo) {
                    if (Settings.inlineVideo && page == pager.currentPage && down == 0f) {
                        // 구글 포토처럼 그 자리에서 바로 재생 (넘기면 멈춤)
                        InlineVideo(
                            item, uiVisible = ui,
                            onTap = { if (up > 0f) scope.launch { pos.animateTo(0f) } else ui = !ui },
                            onFullscreen = { play(item) },
                            poster = { PosterImage(item) },
                        )
                    } else {
                        VideoPoster(item, onTap = { ui = !ui }, onPlay = {
                            if (Settings.inlineVideo) scope.launch { pager.animateScrollToPage(page) } else play(item)
                        })
                    }
                } else {
                    PhotoPage(
                        item,
                        current = page == pager.currentPage && down == 0f,
                        motionTick = motionTick,
                        onMotion = { has -> motionItem = if (has) item.id else if (motionItem == item.id) null else motionItem },
                        onTap = {
                        if (up > 0f) scope.launch { pos.animateTo(0f) } else ui = !ui
                        if (slideshow) slideshow = false
                    })
                }
            }

            val current = itemAt(pager.currentPage)
            val curStack = stacks[pager.currentPage]

            // 묶음 사진: 아래 띠에서 안쪽 사진 고르기 (구글 포토 방식)
            if (curStack.size > 1 && up == 0f && down == 0f) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "비슷한 사진 ${(chosen[pager.currentPage] ?: 0) + 1} / ${curStack.size}" + if (strip) "  ▾" else "  ▴",
                        color = Color.White, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .background(Color(0x99000000), RoundedCornerShape(14.dp))
                            .clickable { strip = !strip }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                    AnimatedVisibility(strip && ui) {
                        val pg = pager.currentPage
                        key(pg) {
                            StackStrip(
                                stack = curStack,
                                selected = chosen[pg] ?: 0,
                                onSelect = { chosen[pg] = it },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                ui && down == 0f, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                // 위: 뒤로 + 이름/날짜 두 줄, 아래: 아이콘 줄 (자리 고정)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xDD000000), Color(0x66000000), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color.White)
                        }
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                current.filename, color = Color.White, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "${fullDate(current.time)}  ·  ${pager.currentPage + 1} / ${items.size}",
                                color = Color(0xFFD0D0D0), style = MaterialTheme.typography.labelMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val fav = com.kakario.synogallery.Favorites.version.let { com.kakario.synogallery.Favorites.isFav(current.id) }
                        IconButton(onClick = { com.kakario.synogallery.Favorites.toggle(current) }) {
                            Icon(
                                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "즐겨찾기",
                                tint = if (fav) MaterialTheme.colorScheme.primary else Color.White,
                            )
                        }
                        // 모션 버튼 자리는 항상 비워 둠 → 다른 아이콘이 움직이지 않음
                        val hasMotion = Settings.motionPhoto != "off" && motionItem == current.id
                        IconButton(onClick = { if (hasMotion) motionTick++ }, enabled = hasMotion) {
                            Icon(
                                Icons.Filled.MotionPhotosOn, "모션 재생",
                                tint = if (hasMotion) Color.White else Color(0x40FFFFFF),
                            )
                        }
                        IconButton(onClick = { slideshow = !slideshow }) {
                            Icon(if (slideshow) Icons.Filled.Pause else Icons.Filled.Slideshow, "슬라이드쇼", tint = Color.White)
                        }
                        IconButton(onClick = { tagEdit = current }) {
                            Icon(Icons.AutoMirrored.Filled.Label, "태그", tint = Color.White)
                        }
                        IconButton(onClick = { albumAdd = current }) {
                            Icon(Icons.Filled.LibraryAdd, "앨범에 추가", tint = Color.White)
                        }
                        IconButton(onClick = {
                            scope.launch { pos.animateTo(if (detailsOpen) 0f else -panelH) }
                        }) {
                            Icon(Icons.Filled.Info, "정보", tint = Color.White)
                        }
                    }
                }
            }

            // 위로 밀면 올라오는 상세 정보
            if (up > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(with(density) { panelH.toDp() })
                        .graphicsLayer { translationY = panelH * (1f - up) }
                        .background(com.kakario.synogallery.Palette.card, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                ) {
                    DetailsPanel(
                        item = current,
                        tagsVersion = tagsVersion,
                        onTag = { tagEdit = current },
                        onAlbum = { albumAdd = current },
                    )
                }
            }
        }
    }

    ViewerDialogs(
        tagEdit = tagEdit, albumAdd = albumAdd,
        onTagDone = { tagEdit = null; tagsVersion++ },
        onTagClose = { tagEdit = null },
        onAlbumClose = { albumAdd = null },
    )
}

@Composable
private fun StackStrip(stack: List<Item>, selected: Int, onSelect: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val fling = rememberSnapFlingBehavior(listState, SnapPosition.Center)
    val itemW = 54.dp
    val gap = 4.dp

    // 가운데에 온 사진이 지금 보는 사진
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }?.index
        }.collect { idx -> if (idx != null) onSelect(idx) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(Color(0x99000000))
            .padding(vertical = 8.dp)
    ) {
        val side = (maxWidth - itemW) / 2
        LazyRow(
            state = listState,
            flingBehavior = fling,
            horizontalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(horizontal = side),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(stack, key = { _, it -> it.id }) { i, it ->
                val key = Syno.thumbKey(it.thumbUnitId, it.cacheKey, "sm")
                val on = i == selected
                Box(
                    Modifier
                        .size(itemW, 62.dp)
                        .graphicsLayer {
                            val sc = if (on) 1f else 0.86f
                            scaleX = sc
                            scaleY = sc
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .border(if (on) 2.5.dp else 0.dp, if (on) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { scope.launch { listState.animateScrollToItem(i) } }
                ) {
                    AsyncImage(
                        model = remember(it.id) {
                            ImageRequest.Builder(ctx)
                                .data(Syno.thumbUrl(it.thumbUnitId, it.cacheKey, "sm"))
                                .memoryCacheKey(key).diskCacheKey(key).build()
                        },
                        contentDescription = it.filename,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerDialogs(
    tagEdit: Item?, albumAdd: Item?,
    onTagDone: () -> Unit, onTagClose: () -> Unit, onAlbumClose: () -> Unit,
) {
    tagEdit?.let { item ->
        var current by remember(item.id) { mutableStateOf<Set<Long>?>(null) }
        var loaded by remember(item.id) { mutableStateOf(false) }
        LaunchedEffect(item.id) {
            current = try {
                Syno.itemTags(item.id).map { it.id }.toSet()
            } catch (_: Exception) {
                null
            }
            loaded = true
        }
        if (loaded) TagEditorDialog(listOf(item.id), current ?: emptySet(), onDone = onTagDone, onDismiss = onTagClose)
    }
    albumAdd?.let { item ->
        AlbumPickerDialog(listOf(item.id), onDone = onAlbumClose, onDismiss = onAlbumClose)
    }
}

@Composable
private fun DetailsPanel(item: Item, tagsVersion: Int, onTag: () -> Unit, onAlbum: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 16.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .background(Color(0xFFD5D5DA), RoundedCornerShape(2.dp))
        )
        Text(
            fullDate(item.time), style = MaterialTheme.typography.titleMedium,
            color = com.kakario.synogallery.Palette.fg, modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
        )
        InfoLine("파일명", item.filename)
        if (item.width > 0) InfoLine("해상도", "${item.width} × ${item.height}")
        if (item.size > 0) InfoLine("크기", formatSize(item.size))
        if (item.isVideo && item.duration > 0) InfoLine("길이", formatDuration(item.duration))
        var tags by remember(item.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(item.id, tagsVersion) {
            tags = try {
                Syno.itemTags(item.id).joinToString(", ") { "#" + it.name }.ifEmpty { "없음" }
            } catch (_: Exception) {
                null
            }
        }
        InfoLine("태그", tags ?: "불러오는 중…")
        Row(Modifier.padding(top = 12.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = onTag) {
                Icon(Icons.AutoMirrored.Filled.Label, null, modifier = Modifier.size(18.dp))
                Text("태그 편집", modifier = Modifier.padding(start = 6.dp))
            }
            androidx.compose.material3.OutlinedButton(onClick = onAlbum, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Filled.LibraryAdd, null, modifier = Modifier.size(18.dp))
                Text("앨범에 추가", modifier = Modifier.padding(start = 6.dp))
            }
        }
        Text(
            "아래로 밀면 닫혀요", color = Color.Gray, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun InfoLine(k: String, v: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(k, color = Color.Gray, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
        Text(v, color = com.kakario.synogallery.Palette.fg, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PosterImage(item: Item) {
    val ctx = LocalContext.current
    val s = if (item.hasXl) "xl" else "m"
    val key = Syno.thumbKey(item.thumbUnitId, item.cacheKey, s)
    AsyncImage(
        model = remember(item.id) {
            ImageRequest.Builder(ctx)
                .data(Syno.thumbUrl(item.thumbUnitId, item.cacheKey, s))
                .memoryCacheKey(key).diskCacheKey(key)
                .placeholderMemoryCacheKey(Syno.thumbKey(item.thumbUnitId, item.cacheKey, Settings.thumbSize))
                .crossfade(false)
                .build()
        },
        contentDescription = item.filename,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun PhotoPage(
    item: Item,
    current: Boolean,
    motionTick: Int,
    onMotion: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    // 갤럭시 모션 포토: 자동 재생 / 버튼으로 재생 / 표시 안 함
    val mode = Settings.motionPhoto
    var motionFile by remember(item.id) { mutableStateOf(com.kakario.synogallery.MotionPhoto.cached(item)) }
    var playing by remember(item.id) { mutableStateOf(false) }
    val startTick = remember(item.id) { motionTick }
    if (mode != "off" && current) {
        LaunchedEffect(item.id, mode) {
            val f = motionFile ?: com.kakario.synogallery.MotionPhoto.video(item)
            motionFile = f
            onMotion(f != null)
            if (f != null && mode == "auto") playing = true
        }
    }
    LaunchedEffect(motionTick) { if (motionTick != startTick && current && motionFile != null) playing = true }
    LaunchedEffect(current) { if (!current) playing = false }

    Box(Modifier.fillMaxSize()) {
        PhotoImage(item, onTap)
        val f = motionFile
        if (playing && f != null) MotionOverlay(f) { playing = false }
    }
}

@Composable
private fun PhotoImage(item: Item, onTap: () -> Unit) {
    val ctx = LocalContext.current
    val quality = Settings.viewerQuality
    val thumbSize = Settings.thumbSize
    val req = remember(item.id, quality) {
        val (url, key) = if (quality == "original") {
            Syno.originalUrl(item) to "o_${item.id}_${item.cacheKey}"
        } else {
            val s = if (item.hasXl) "xl" else "m"
            Syno.thumbUrl(item.thumbUnitId, item.cacheKey, s) to Syno.thumbKey(item.thumbUnitId, item.cacheKey, s)
        }
        ImageRequest.Builder(ctx)
            .data(url)
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .placeholderMemoryCacheKey(Syno.thumbKey(item.thumbUnitId, item.cacheKey, thumbSize))
            .build()
    }
    ZoomableAsyncImage(
        model = req,
        contentDescription = item.filename,
        modifier = Modifier.fillMaxSize(),
        onClick = { onTap() },
    )
}

@Composable
private fun VideoPoster(item: Item, onTap: () -> Unit, onPlay: () -> Unit) {
    val ctx = LocalContext.current
    val s = if (item.hasXl) "xl" else "m"
    val key = Syno.thumbKey(item.thumbUnitId, item.cacheKey, s)
    Box(Modifier.fillMaxSize().clickable(onClick = onTap), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = remember(item.id) {
                ImageRequest.Builder(ctx)
                    .data(Syno.thumbUrl(item.thumbUnitId, item.cacheKey, s))
                    .memoryCacheKey(key).diskCacheKey(key)
                    .placeholderMemoryCacheKey(Syno.thumbKey(item.thumbUnitId, item.cacheKey, Settings.thumbSize))
                    .build()
            },
            contentDescription = item.filename,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, "재생", tint = Color.White, modifier = Modifier.size(48.dp))
        }
        if (item.duration > 0) {
            Badge(formatDuration(item.duration), Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp))
        }
    }
}
