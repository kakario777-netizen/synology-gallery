package com.kakario.synogallery.ui

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
    val items = ViewerStore.items
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, items.lastIndex)) { items.size }
    var ui by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }

    KeepScreenOn(Settings.keepScreenOn || slideshow)
    SystemBars(ui)

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

    val bg = if (Settings.viewerBlack) Color.Black else Color(0xFF121214)

    Box(Modifier.fillMaxSize().background(bg)) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                VideoPoster(item, onTap = { ui = !ui }, onPlay = { nav.navigate("player/$page") })
            } else {
                PhotoPage(item, onTap = { ui = !ui; if (slideshow) slideshow = false })
            }
        }

        val current = items[pager.currentPage]

        AnimatedVisibility(ui, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        current.filename, color = Color.White, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "${fullDate(current.time)} · ${pager.currentPage + 1}/${items.size}",
                        color = Color(0xFFCCCCCC), style = MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(onClick = { slideshow = !slideshow }) {
                    Icon(if (slideshow) Icons.Filled.Pause else Icons.Filled.Slideshow, "슬라이드쇼", tint = Color.White)
                }
                IconButton(onClick = { info = !info }) {
                    Icon(Icons.Filled.Info, "정보", tint = Color.White)
                }
            }
        }

        AnimatedVisibility(info, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            InfoPanel(current)
        }
    }
}

@Composable
private fun InfoPanel(item: Item) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 20.dp)
    ) {
        InfoLine("파일명", item.filename)
        InfoLine("촬영일", fullDate(item.time))
        if (item.width > 0) InfoLine("해상도", "${item.width} × ${item.height}")
        if (item.size > 0) InfoLine("크기", formatSize(item.size))
        if (item.isVideo && item.duration > 0) InfoLine("길이", formatDuration(item.duration))
    }
}

@Composable
private fun InfoLine(k: String, v: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(k, color = Color.Gray, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
        Text(v, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PhotoPage(item: Item, onTap: () -> Unit) {
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
