package com.kakario.synogallery.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.kakario.synogallery.AllSource
import com.kakario.synogallery.Item
import com.kakario.synogallery.Palette
import com.kakario.synogallery.Syno
import com.kakario.synogallery.ViewerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.concurrent.ConcurrentHashMap

/** 위치가 있는 사진 목록 (앱이 켜져 있는 동안 한 번만 불러옴) */
private object MapData {
    var items: List<Item>? = null
}

private val pinCache = ConcurrentHashMap<String, Bitmap>()

/** 옛날 구글 포토 지도처럼: 대표 사진 + 장수가 적힌 팻말 */
private suspend fun pinBitmap(ctx: Context, item: Item, count: Int, accent: Int): Bitmap = withContext(Dispatchers.IO) {
    val cacheKey = "${item.id}_$count"
    pinCache[cacheKey]?.let { return@withContext it }
    val d = ctx.resources.displayMetrics.density
    val box = (64 * d).toInt()
    val border = 3 * d
    val tail = 10 * d
    val w = box + (14 * d).toInt()
    val h = box + tail.toInt() + (8 * d).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val left = 2f * d
    val top = 8f * d
    val card = RectF(left, top, left + box, top + box)

    // 그림자 + 흰 테두리 카드 + 아래 꼬리
    p.color = 0x55000000
    c.drawRoundRect(RectF(card.left + 1.5f * d, card.top + 2 * d, card.right + 1.5f * d, card.bottom + 2 * d), 10 * d, 10 * d, p)
    p.color = android.graphics.Color.WHITE
    c.drawRoundRect(card, 10 * d, 10 * d, p)
    val path = Path().apply {
        moveTo(card.centerX() - tail, card.bottom - 1)
        lineTo(card.centerX(), card.bottom + tail)
        lineTo(card.centerX() + tail, card.bottom - 1)
        close()
    }
    c.drawPath(path, p)

    // 사진
    val req = ImageRequest.Builder(ctx)
        .data(Syno.thumbUrl(item.thumbUnitId, item.cacheKey, "sm"))
        .diskCacheKey(Syno.thumbKey(item.thumbUnitId, item.cacheKey, "sm"))
        .memoryCacheKey(Syno.thumbKey(item.thumbUnitId, item.cacheKey, "sm"))
        .size(box)
        .allowHardware(false)
        .build()
    val src = ((ctx.imageLoader.execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
    if (src != null) {
        val inner = RectF(card.left + border, card.top + border, card.right - border, card.bottom - border)
        val layer = c.saveLayer(inner, null)
        p.color = android.graphics.Color.BLACK
        c.drawRoundRect(inner, 8 * d, 8 * d, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        // 가운데를 정사각형으로 잘라 그림
        val s = minOf(src.width, src.height)
        val sx = (src.width - s) / 2
        val sy = (src.height - s) / 2
        c.drawBitmap(src, android.graphics.Rect(sx, sy, sx + s, sy + s), inner, p)
        p.xfermode = null
        c.restoreToCount(layer)
    }

    // 장수
    if (count > 1) {
        val label = if (count > 999) "999+" else "$count"
        p.textSize = 11 * d
        p.typeface = Typeface.DEFAULT_BOLD
        val tw = p.measureText(label)
        val bw = maxOf(20 * d, tw + 12 * d)
        val br = RectF(card.right - bw + 8 * d, 0f, card.right + 8 * d, 20 * d)
        p.color = accent
        c.drawRoundRect(br, 10 * d, 10 * d, p)
        p.color = android.graphics.Color.WHITE
        c.drawText(label, br.centerX() - tw / 2, br.centerY() + 4 * d, p)
    }
    pinCache[cacheKey] = bmp
    bmp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(nav: NavController) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    var items by remember { mutableStateOf(MapData.items) }
    var loaded by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf<List<Item>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var fitted by remember { mutableStateOf(false) }

    // 위치 있는 사진 모두 불러오기
    LaunchedEffect(Unit) {
        if (items != null) return@LaunchedEffect
        try {
            val out = ArrayList<Item>()
            var off = 0
            while (true) {
                val page = Syno.listItems(AllSource, off, 500)
                off += page.size
                loaded = off
                page.filterTo(out) { it.lat != null && it.lon != null }
                if (page.size < 500) break
            }
            MapData.items = out
            items = out
        } catch (e: Exception) {
            error = e.message ?: "불러오지 못했어요"
        }
    }

    // 처음 한 번: 사진들이 다 보이게 맞춤
    LaunchedEffect(items, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        val list = items ?: return@LaunchedEffect
        if (fitted || list.isEmpty()) return@LaunchedEffect
        fitted = true
        mv.post {
            val box = BoundingBox.fromGeoPoints(list.map { GeoPoint(it.lat!!, it.lon!!) })
            if (box.latitudeSpan < 0.01 && box.longitudeSpanWithDateLine < 0.01) {
                mv.controller.setZoom(15.0)
                mv.controller.setCenter(box.centerWithDateLine)
            } else {
                mv.zoomToBoundingBox(box.increaseByScale(1.15f), false)
            }
            tick++
        }
    }

    // 지도를 움직일 때마다: 보이는 사진 + 묶어서 팻말
    LaunchedEffect(tick, items) {
        val mv = mapView ?: return@LaunchedEffect
        val list = items ?: return@LaunchedEffect
        val bbox = mv.boundingBox
        val proj = mv.projection
        val cell = 76 * ctx.resources.displayMetrics.density
        val groups = withContext(Dispatchers.Default) {
            val inView = list.filter { bbox.contains(it.lat!!, it.lon!!) }.sortedByDescending { it.time }
            val g = LinkedHashMap<Long, MutableList<Item>>()
            val pt = android.graphics.Point()
            for (it in inView) {
                proj.toPixels(GeoPoint(it.lat!!, it.lon!!), pt)
                val key = ((pt.x / cell).toLong() shl 32) or ((pt.y / cell).toLong() and 0xffffffffL)
                g.getOrPut(key) { mutableListOf() }.add(it)
            }
            inView to g.values.toList()
        }
        visible = groups.first
        val markers = groups.second.take(150).map { grp ->
            val rep = grp.first()
            val bmp = pinBitmap(ctx, rep, grp.size, accent)
            Marker(mv).apply {
                position = GeoPoint(grp.map { it.lat!! }.average(), grp.map { it.lon!! }.average())
                icon = BitmapDrawable(ctx.resources, bmp)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setInfoWindow(null)
                setOnMarkerClickListener { _, view ->
                    if (grp.size > 1 && view.zoomLevelDouble < 18.5) {
                        val b = BoundingBox.fromGeoPoints(grp.map { GeoPoint(it.lat!!, it.lon!!) })
                        if (b.latitudeSpan < 0.0005 && b.longitudeSpanWithDateLine < 0.0005) {
                            view.controller.animateTo(position, view.zoomLevelDouble + 2, 350L)
                        } else view.zoomToBoundingBox(b.increaseByScale(1.4f), true)
                    } else {
                        ViewerStore.stacks = grp.map { listOf(it) }
                        ViewerStore.sourceKey = ""
                        nav.navigate("viewer/0")
                    }
                    true
                }
            }
        }
        mv.overlays.removeAll { it is Marker }
        mv.overlays.addAll(markers)
        mv.invalidate()
    }

    DisposableEffect(lifecycle, mapView) {
        val mv = mapView
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mv?.onResume()
                Lifecycle.Event.ON_PAUSE -> mv?.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    DisposableEffect(Unit) { onDispose { mapView?.onDetach() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("지도") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(0.55f)) {
                AndroidView(
                    factory = { c ->
                        Configuration.getInstance().apply {
                            userAgentValue = c.packageName
                            osmdroidBasePath = c.cacheDir.resolve("osm")
                            osmdroidTileCache = c.cacheDir.resolve("osm/tiles")
                        }
                        MapView(c).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                            minZoomLevel = 2.5
                            isTilesScaledToDpi = true
                            controller.setZoom(7.0)
                            controller.setCenter(GeoPoint(36.3, 127.8))
                            addMapListener(DelayedMapListener(object : MapListener {
                                override fun onScroll(event: ScrollEvent?): Boolean {
                                    tick++; return false
                                }

                                override fun onZoom(event: ZoomEvent?): Boolean {
                                    tick++; return false
                                }
                            }, 220))
                            mapView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (items == null) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (error != null) {
                            Text(error!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            CircularProgressIndicator(Modifier.size(32.dp))
                            Text(
                                "위치 있는 사진 찾는 중… ($loaded)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.fg,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .background(Palette.card, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = Palette.divider)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("이 지역 사진", fontWeight = FontWeight.Bold, color = Palette.fg)
                Text(" ${visible.size}장", color = Palette.sub, modifier = Modifier.padding(start = 4.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().weight(0.45f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(visible, key = { it.id }) { item ->
                    Thumb(item = item, onClick = {
                        ViewerStore.stacks = visible.map { listOf(it) }
                        ViewerStore.sourceKey = ""
                        nav.navigate("viewer/${visible.indexOf(item).coerceAtLeast(0)}")
                    })
                }
            }
        }
    }
}

