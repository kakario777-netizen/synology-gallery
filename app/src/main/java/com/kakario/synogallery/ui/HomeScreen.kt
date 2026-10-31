package com.kakario.synogallery.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
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

val HOME_TABS = listOf("사진", "앨범", "태그", "폴더")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    var tab by rememberSaveable { mutableIntStateOf(Settings.startTab.coerceIn(0, HOME_TABS.lastIndex)) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("시놀 갤러리") },
                actions = {
                    TextButton(onClick = { Settings.sharedSpace = !Settings.sharedSpace }) {
                        Text(if (Settings.sharedSpace) "공유 공간" else "개인 공간")
                    }
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, "설정")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            androidx.compose.material3.TabRow(selectedTabIndex = tab, containerColor = Color.White) {
                HOME_TABS.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> GalleryGrid(AllSource, nav)
                1 -> AlbumsTab(nav)
                2 -> TagsTab(nav)
                else -> FolderContent(folderId = null, nav = nav)
            }
        }
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
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = { menu = true })) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEFEFF2))
        ) {
            if (c.unitId != 0L) {
                val key = Syno.thumbKey(c.unitId, c.cacheKey, "m")
                AsyncImage(
                    model = remember(c.id, c.unitId) {
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
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.MoreVert, "메뉴", tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }
        Text("${c.count}개", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
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
            if (l.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { MessageBox(emptyText) }
            }
            items(l, key = { it.id }) { c ->
                CoverCardView(
                    c, team,
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
    CoverGrid(
        load = { Syno.listAlbums().map { CoverCard(it.id, it.name, it.count, it.cacheKey, it.thumbUnitId) } },
        emptyText = "앨범이 없어요.\n사진을 길게 눌러 선택한 뒤 '앨범'으로 새 앨범을 만들 수 있어요.",
        team = false,
        reloadKey = "albums",
        onOpen = { nav.navigate("album/${it.id}/${Uri.encode(it.name)}") },
        rename = { c, name -> Syno.renameAlbum(c.id, name) },
        delete = { c -> Syno.deleteAlbum(c.id) },
        deleteText = { "앨범만 지워지고 사진은 그대로 남아요." },
    )
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
