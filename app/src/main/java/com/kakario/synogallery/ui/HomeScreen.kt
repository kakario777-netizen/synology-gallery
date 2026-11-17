package com.kakario.synogallery.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kakario.synogallery.AlbumSource
import com.kakario.synogallery.AllSource
import com.kakario.synogallery.Folder
import com.kakario.synogallery.FolderSource
import com.kakario.synogallery.Settings
import com.kakario.synogallery.Syno
import com.kakario.synogallery.TagSource
import kotlinx.coroutines.launch

val HOME_TABS = listOf("사진", "앨범", "태그", "폴더", "즐겨찾기", "장소")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val tabs = Settings.visibleTabs()
    val startPage = remember { tabs.indexOf(Settings.startTab).coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startPage) { tabs.size }
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? android.app.Activity
    var askExit by remember { mutableStateOf(false) }

    // 뒤로가기: 앱 종료 전에 한 번 더 물어봄
    BackHandler { askExit = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("시놀 갤러리") },
                actions = {
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, "설정")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (tabs.size > 1) {
                androidx.compose.material3.TabRow(selectedTabIndex = pager.currentPage.coerceIn(0, tabs.lastIndex), containerColor = com.kakario.synogallery.Palette.bg) {
                    tabs.forEachIndexed { i, t ->
                        Tab(
                            selected = pager.currentPage == i,
                            onClick = { if (!SelectionMode.active) scope.launch { pager.animateScrollToPage(i) } },
                            text = { Text(HOME_TABS[t]) },
                        )
                    }
                }
            }
            BackgroundStatusBar()
            // 좌우로 밀어서 탭 이동
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { tabs[it] },
                // 여러 장 고르는 중에는 좌우로 넘어가지 않게
                userScrollEnabled = !SelectionMode.active,
            ) { page ->
                when (tabs[page]) {
                    0 -> GalleryGrid(AllSource, nav)
                    1 -> AlbumsTab(nav)
                    2 -> TagsTab(nav)
                    4 -> GalleryGrid(com.kakario.synogallery.FavSource, nav)
                    5 -> PlacesTab(nav)
                    else -> FolderContent(folderId = null, nav = nav)
                }
            }
        }
    }

    if (askExit) {
        ConfirmDialog(
            title = "앱 종료", text = "시놀 갤러리를 종료할까요?", confirm = "종료",
            onConfirm = { askExit = false; activity?.finish() },
            onDismiss = { askExit = false },
        )
    }
}

/** 앨범·태그 공용 카드 */
data class CoverCard(val id: Long, val name: String, val count: Int, val cacheKey: String, val unitId: Long)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverCardView(
    c: CoverCard,
    team: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    albumCounts: Boolean = false,
    editable: Boolean = true,
    placeName: Boolean = false,
) {
    val ctx = LocalContext.current
    // 장소 이름은 한글로
    var shownName by remember(c.id, c.name) {
        mutableStateOf(if (placeName) com.kakario.synogallery.PlaceNames.cached(c.id, c.name) ?: c.name else c.name)
    }
    if (placeName) LaunchedEffect(c.id, c.name) {
        shownName = com.kakario.synogallery.PlaceNames.korean(c.id, c.name)
    }
    var menu by remember { mutableStateOf(false) }
    // 앨범: 사진 몇 장 · 동영상 몇 개 (갤럭시 갤러리처럼)
    var counts by remember(c.id) { mutableStateOf(AlbumCounts.cache[c.id]) }
    if (albumCounts) LaunchedEffect(c.id, c.count) {
        if (counts == null || counts!!.first + counts!!.second != c.count) {
            try {
                val v = Syno.countInAlbum(c.id, "video")
                val p = (c.count - v).coerceAtLeast(0)
                counts = p to v
                AlbumCounts.cache[c.id] = p to v
            } catch (_: Exception) {
            }
        }
    }
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = { if (editable) menu = true })) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(com.kakario.synogallery.Palette.placeholder)
        ) {
            if (c.unitId != 0L) {
                val key = Syno.thumbKey(c.unitId, c.cacheKey, "m")
                AsyncImage(
                    model = remember(c.id, c.unitId, Syno.sessionTick) {
                        ImageRequest.Builder(ctx)
                            .data(Syno.thumbUrl(c.unitId, c.cacheKey, "m", team = team))
                            .memoryCacheKey(key).diskCacheKey(key).build()
                    },
                    contentDescription = c.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("이름 바꾸기") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("삭제", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shownName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            if (editable) IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.MoreVert, "메뉴", tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }
        val cnt = counts
        val label = when {
            !albumCounts || cnt == null -> "${c.count}개"
            cnt.second == 0 -> "사진 ${cnt.first}"
            cnt.first == 0 -> "동영상 ${cnt.second}"
            else -> "사진 ${cnt.first} · 동영상 ${cnt.second}"
        }
        Text(label, color = com.kakario.synogallery.Palette.sub, style = MaterialTheme.typography.labelMedium)
    }
}

object AlbumCounts {
    val cache = java.util.concurrent.ConcurrentHashMap<Long, Pair<Int, Int>>()
}

/** 이름 바꾸기·삭제 대화상자 상태 */
private sealed interface EditTarget {
    val card: CoverCard
}
private data class RenameTarget(override val card: CoverCard) : EditTarget
private data class DeleteTarget(override val card: CoverCard) : EditTarget

@Composable
private fun CoverGrid(
    load: suspend () -> List<CoverCard>,
    emptyText: String,
    team: Boolean,
    reloadKey: Any,
    onOpen: (CoverCard) -> Unit,
    rename: suspend (CoverCard, String) -> Unit,
    delete: suspend (CoverCard) -> Unit,
    deleteText: (CoverCard) -> String,
    topAction: (@Composable (reload: () -> Unit) -> Unit)? = null,
    albumCounts: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    editable: Boolean = true,
    placeNames: Boolean = false,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember(reloadKey) { mutableStateOf<List<CoverCard>?>(null) }
    var error by remember(reloadKey) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<EditTarget?>(null) }

    LaunchedEffect(reload, reloadKey) {
        error = null
        try {
            list = load()
        } catch (e: Exception) {
            error = e.message
        }
    }

    val l = list
    when {
        error != null -> MessageBox(error!!, "다시 시도") { reload++ }
        l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(12.dp),
        ) {
            if (topAction != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { topAction { reload++ } }
            }
            if (leading != null) {
                item(key = "leading") { leading() }
            }
            if (l.isEmpty() && leading == null) {
                item(span = { GridItemSpan(maxLineSpan) }) { MessageBox(emptyText) }
            }
            items(l, key = { it.id }) { c ->
                CoverCardView(
                    c, team,
                    albumCounts = albumCounts,
                    editable = editable,
                    placeName = placeNames,
                    onClick = { onOpen(c) },
                    onRename = { edit = RenameTarget(c) },
                    onDelete = { edit = DeleteTarget(c) },
                )
            }
        }
    }

    when (val e = edit) {
        is RenameTarget -> TextInputDialog(
            title = "이름 바꾸기", initial = e.card.name, confirm = "저장",
            onConfirm = { name ->
                edit = null
                scope.launch {
                    try {
                        rename(e.card, name); reload++
                    } catch (ex: Exception) {
                        toast(ctx, ex.message ?: "실패했어요")
                    }
                }
            },
            onDismiss = { edit = null },
        )
        is DeleteTarget -> ConfirmDialog(
            title = "'${e.card.name}' 삭제", text = deleteText(e.card),
            onConfirm = {
                edit = null
                scope.launch {
                    try {
                        delete(e.card); reload++
                    } catch (ex: Exception) {
                        toast(ctx, ex.message ?: "실패했어요")
                    }
                }
            },
            onDismiss = { edit = null },
        )
        null -> {}
    }
}

// ───────────────── 앨범 ─────────────────

@Composable
fun AlbumsTab(nav: NavController) {
    if (Settings.sharedSpace) {
        MessageBox("공유 공간에는 앨범이 없어요.\n개인 공간으로 바꾸면 앨범을 볼 수 있어요.", "개인 공간으로") {
            Settings.sharedSpace = false
        }
        return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf<(() -> Unit)?>(null) }
    CoverGrid(
        load = { Syno.listAlbums().map { CoverCard(it.id, it.name, it.count, it.cacheKey, it.thumbUnitId) } },
        emptyText = "앨범이 없어요.\n사진을 길게 눌러 선택한 뒤 '앨범'으로 새 앨범을 만들 수 있어요.",
        team = false,
        reloadKey = "albums",
        onOpen = { nav.navigate("album/${it.id}/${Uri.encode(it.name)}") },
        rename = { c, name -> Syno.renameAlbum(c.id, name) },
        delete = { c -> Syno.deleteAlbum(c.id) },
        deleteText = { "앨범만 지워지고 사진은 그대로 남아요." },
        albumCounts = true,
        leading = if (Settings.allVideosAlbum) ({ AllVideosCard { nav.navigate("videos") } }) else null,
        topAction = { reload ->
            Row(
                Modifier.fillMaxWidth().clickable { creating = reload }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                Text("새 앨범", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            }
        },
    )
    creating?.let { reload ->
        TextInputDialog(
            title = "새 앨범", label = "앨범 이름", confirm = "만들기",
            onConfirm = { n ->
                creating = null
                scope.launch {
                    try {
                        Syno.createAlbum(n, emptyList()); reload()
                        toast(ctx, "'$n' 앨범을 만들었어요. 사진을 길게 눌러 넣을 수 있어요")
                    } catch (e: Exception) {
                        toast(ctx, e.message ?: "실패했어요")
                    }
                }
            },
            onDismiss = { creating = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(id: Long, title: String, nav: NavController) {
    var name by remember { mutableStateOf(title) }
    DetailScaffold(
        title = name, nav = nav,
        onRename = { n -> Syno.renameAlbum(id, n); name = n },
        onDelete = { Syno.deleteAlbum(id); nav.popBackStack() },
        deleteText = "앨범만 지워지고 사진은 그대로 남아요.",
    ) { m -> GalleryGrid(AlbumSource(id), nav, m) }
}

// ───────────────── 태그 ─────────────────

@Composable
fun TagsTab(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf<(() -> Unit)?>(null) }
    val team = Settings.sharedSpace
    CoverGrid(
        load = { Syno.listTags().map { CoverCard(it.id, it.name, it.count, it.cacheKey, it.thumbUnitId) } },
        emptyText = "태그가 없어요.\n사진을 길게 눌러 선택한 뒤 '태그'로 붙일 수 있어요.",
        team = team,
        reloadKey = "tags_$team",
        onOpen = { nav.navigate("tag/${it.id}/${Uri.encode(it.name)}") },
        rename = { c, name -> Syno.renameTag(c.id, name) },
        delete = { c -> Syno.deleteTag(c.id) },
        deleteText = { "태그만 지워지고 사진은 그대로 남아요." },
        topAction = { reload ->
            Row(
                Modifier.fillMaxWidth().clickable { creating = reload }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                Text("새 태그", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            }
        },
    )
    creating?.let { reload ->
        TextInputDialog(
            title = "새 태그", label = "태그 이름", confirm = "만들기",
            onConfirm = { n ->
                creating = null
                scope.launch {
                    try {
                        Syno.createTag(n); reload()
                    } catch (e: Exception) {
                        toast(ctx, e.message ?: "실패했어요")
                    }
                }
            },
            onDismiss = { creating = null },
        )
    }
}

@Composable
fun TagDetailScreen(id: Long, title: String, nav: NavController) {
    var name by remember { mutableStateOf(title) }
    DetailScaffold(
        title = name, nav = nav,
        onRename = { n -> Syno.renameTag(id, n); name = n },
        onDelete = { Syno.deleteTag(id); nav.popBackStack() },
        deleteText = "태그만 지워지고 사진은 그대로 남아요.",
        icon = true,
    ) { m -> GalleryGrid(TagSource(id), nav, m) }
}

/** 앨범·태그 상세 화면 공용 (상단 메뉴: 이름 바꾸기 / 삭제) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(
    title: String,
    nav: NavController,
    onRename: suspend (String) -> Unit,
    onDelete: suspend () -> Unit,
    deleteText: String,
    icon: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon) Icon(Icons.AutoMirrored.Filled.Label, null, modifier = Modifier.padding(end = 8.dp).size(20.dp))
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "메뉴") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("이름 바꾸기") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(
                                text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; deleting = true },
                            )
                        }
                    }
                },
            )
        },
    ) { pad -> content(Modifier.padding(pad)) }

    if (renaming) TextInputDialog(
        title = "이름 바꾸기", initial = title, confirm = "저장",
        onConfirm = { n ->
            renaming = false
            scope.launch {
                try {
                    onRename(n)
                } catch (e: Exception) {
                    toast(ctx, e.message ?: "실패했어요")
                }
            }
        },
        onDismiss = { renaming = false },
    )
    if (deleting) ConfirmDialog(
        title = "'$title' 삭제", text = deleteText,
        onConfirm = {
            deleting = false
            scope.launch {
                try {
                    onDelete()
                } catch (e: Exception) {
                    toast(ctx, e.message ?: "실패했어요")
                }
            }
        },
        onDismiss = { deleting = false },
    )
}

// ───────────────── 폴더 ─────────────────

@Composable
fun FolderContent(folderId: Long?, nav: NavController, modifier: Modifier = Modifier) {
    val space = Settings.sharedSpace
    var resolved by remember(folderId, space) { mutableStateOf(folderId) }
    var folders by remember(folderId, space) { mutableStateOf<List<Folder>>(emptyList()) }
    var error by remember(folderId, space) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(folderId, space, reload) {
        error = null
        try {
            val id = folderId ?: Syno.rootFolderId()
            resolved = id
            folders = Syno.listFolders(id)
        } catch (e: Exception) {
            error = e.message
        }
    }

    val id = resolved
    if (error != null && id == null) {
        MessageBox(error!!, "다시 시도") { reload++ }
        return
    }
    if (id == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    GalleryGrid(FolderSource(id), nav, modifier) {
        items(folders, key = { "f_${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { f ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { nav.navigate("folder/${f.id}/${Uri.encode(f.shortName)}") }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Text(
                    f.shortName, modifier = Modifier.padding(start = 14.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(id: Long, title: String, nav: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
    ) { pad ->
        FolderContent(folderId = id, nav = nav, modifier = Modifier.padding(pad))
    }
}


// ───────────────── 모든 동영상 ─────────────────

private object AllVideosInfo {
    var cover: com.kakario.synogallery.Item? = null
    var count: Int = -1
}

/** 앨범 탭 맨 앞 '모든 동영상' 카드 */
@Composable
private fun AllVideosCard(onClick: () -> Unit) {
    val ctx = LocalContext.current
    var cover by remember { mutableStateOf(AllVideosInfo.cover) }
    var count by remember { mutableIntStateOf(AllVideosInfo.count) }
    LaunchedEffect(Unit) {
        try {
            val first = Syno.listItems(com.kakario.synogallery.VideosSource, 0, 1).firstOrNull()
            cover = first
            AllVideosInfo.cover = first
            val n = try {
                Syno.countVideos()
            } catch (_: Exception) {
                -1
            }
            count = n
            AllVideosInfo.count = n
        } catch (_: Exception) {
        }
    }
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(com.kakario.synogallery.Palette.placeholder)
        ) {
            cover?.let { c ->
                val key = Syno.thumbKey(c.thumbUnitId, c.cacheKey, "m")
                AsyncImage(
                    model = remember(c.id) {
                        ImageRequest.Builder(ctx)
                            .data(Syno.thumbUrl(c.thumbUnitId, c.cacheKey, "m"))
                            .memoryCacheKey(key).diskCacheKey(key).build()
                    },
                    contentDescription = "모든 동영상",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Icon(
                Icons.Filled.PlayCircle, null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            )
        }
        Text(
            "모든 동영상", style = MaterialTheme.typography.bodyLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            if (count >= 0) "동영상 $count" else "동영상",
            color = com.kakario.synogallery.Palette.sub, style = MaterialTheme.typography.labelMedium,
        )
    }
}

private val VIDEO_SORTS = listOf(
    "date_desc" to "최신순", "date_asc" to "오래된순", "name" to "이름순", "size" to "큰 파일순", "length" to "긴 영상순",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(nav: NavController) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("모든 동영상") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    Box {
                        TextButton(onClick = { menu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, null, modifier = Modifier.size(18.dp))
                            Text(
                                VIDEO_SORTS.firstOrNull { it.first == Settings.videoSort }?.second ?: "정렬",
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            VIDEO_SORTS.forEach { (k, label) ->
                                DropdownMenuItem(
                                    text = { Text(label + if (k == Settings.videoSort) "  ✓" else "") },
                                    onClick = { Settings.videoSort = k; menu = false },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        GalleryGrid(com.kakario.synogallery.VideosSource, nav, Modifier.padding(pad))
    }
}


// ───────────────── 장소 ─────────────────

@Composable
fun PlacesTab(nav: NavController) {
    val team = Settings.sharedSpace
    CoverGrid(
        load = { Syno.listPlaces().map { CoverCard(it.id, it.name, it.count, it.cacheKey, it.thumbUnitId) } },
        emptyText = "장소가 없어요.\n위치 정보(GPS)가 있는 사진이 있어야 시놀로지 포토가 장소를 만들어요.",
        team = team,
        reloadKey = "places_$team",
        onOpen = {
            val ko = com.kakario.synogallery.PlaceNames.cached(it.id, it.name) ?: it.name
            nav.navigate("place/${it.id}/${Uri.encode(ko)}")
        },
        rename = { _, _ -> },
        delete = { },
        deleteText = { "" },
        editable = false,
        placeNames = true,
        topAction = {
            Row(
                Modifier.fillMaxWidth().clickable { nav.navigate("map") }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Map, null, tint = MaterialTheme.colorScheme.primary)
                Text("지도로 보기", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(id: Long, title: String, nav: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
    ) { pad ->
        GalleryGrid(com.kakario.synogallery.PlaceSource(id), nav, Modifier.padding(pad))
    }
}


/** 탭 바로 아래 고정: 지금 뒤에서 하는 일 (스크롤해도 사라지지 않음) */
@Composable
fun BackgroundStatusBar() {
    val parts = buildList {
        if (com.kakario.synogallery.BgStatus.connecting) add("NAS 연결 중")
        if (com.kakario.synogallery.BgStatus.checking > 0) add("새 사진 확인 중")
        val pre = com.kakario.synogallery.ThumbPrefetch.pending
        if (pre > 0) add("썸네일 받는 중 ${"%,d".format(pre)}")
        val loc = com.kakario.synogallery.LocalThumbs.pending
        if (loc > 0) add("썸네일 만드는 중 ${"%,d".format(loc)}")
        val sim = com.kakario.synogallery.Similar.pending
        if (sim > 0 && Settings.groupSimilar) {
            add(if (com.kakario.synogallery.Similar.waiting) "비슷한 사진 분석 대기 (연결)" else "비슷한 사진 분석 ${"%,d".format(sim)}")
        }
    }
    androidx.compose.animation.AnimatedVisibility(parts.isNotEmpty()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(com.kakario.synogallery.Palette.placeholder)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            Text(
                parts.joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = com.kakario.synogallery.Palette.sub,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
