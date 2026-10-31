package com.kakario.synogallery.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.kakario.synogallery.Album
import com.kakario.synogallery.AlbumSource
import com.kakario.synogallery.AllSource
import com.kakario.synogallery.Folder
import com.kakario.synogallery.FolderSource
import com.kakario.synogallery.Settings
import com.kakario.synogallery.Syno

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("사진", "앨범", "폴더")
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
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> GalleryGrid(AllSource, nav)
                1 -> AlbumsTab(nav)
                else -> FolderContent(folderId = null, nav = nav)
            }
        }
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
    var albums by remember { mutableStateOf<List<Album>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        error = null
        try {
            albums = Syno.listAlbums()
        } catch (e: Exception) {
            error = e.message
        }
    }
    val list = albums
    when {
        error != null -> MessageBox(error!!, "다시 시도") { reload++ }
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        list.isEmpty() -> MessageBox("앨범이 없어요.")
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        ) {
            items(list, key = { it.id }) { a ->
                AlbumCard(a) { nav.navigate("album/${a.id}/${Uri.encode(a.name)}") }
            }
        }
    }
}

@Composable
private fun AlbumCard(a: Album, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1C1C1F))
        ) {
            if (a.thumbUnitId != 0L) {
                val key = Syno.thumbKey(a.thumbUnitId, a.cacheKey, "m")
                AsyncImage(
                    model = remember(a.id) {
                        ImageRequest.Builder(ctx)
                            .data(Syno.thumbUrl(a.thumbUnitId, a.cacheKey, "m", team = false))
                            .memoryCacheKey(key).diskCacheKey(key).build()
                    },
                    contentDescription = a.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            a.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text("${a.count}개", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(id: Long, title: String, nav: NavController) {
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
        GalleryGrid(AlbumSource(id), nav, Modifier.padding(pad))
    }
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
