package com.kakario.synogallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** 뒤에서 하는 일 상태 (탭 아래 고정 줄에 표시) */
object BgStatus {
    var connecting by androidx.compose.runtime.mutableStateOf(false)
    var checking by mutableIntStateOf(0)
}

/** 화면이 바쁜지 (스크롤 중이면 뒤 작업은 잠깐 쉼) */
object UiBusy {
    @Volatile private var lastActive = 0L

    @Volatile var scrolling = false
        set(v) {
            field = v
            lastActive = android.os.SystemClock.elapsedRealtime()
        }

    /** 스크롤 중이거나 막 멈춘 직후(화면 썸네일을 받는 시간)에는 뒤 작업을 기다림 */
    suspend fun waitIdle() {
        while (scrolling || android.os.SystemClock.elapsedRealtime() - lastActive < 1500) delay(250)
    }
}

/** 뒤 작업 전용 이미지 로더: 같은 폰 캐시를 쓰지만 통로는 따로 */
object BgImages {
    @Volatile private var loader: coil.ImageLoader? = null

    fun get(ctx: Context): coil.ImageLoader = loader ?: synchronized(this) {
        loader ?: coil.ImageLoader.Builder(ctx.applicationContext)
            .callFactory {
                object : okhttp3.Call.Factory {
                    override fun newCall(request: okhttp3.Request) = Syno.bgClient.newCall(request)
                }
            }
            .diskCache { ctx.imageLoader.diskCache!! }
            .memoryCache(null as coil.memory.MemoryCache?)
            .components { add(LocalThumbFetcher.Factory()) }
            .respectCacheHeaders(false)
            .build()
            .also { loader = it }
    }
}

/**
 * 2단계: 전체 사진의 목록용 썸네일을 미리 폰에 받아 둠 (최신 사진부터).
 * 받으면서 사진의 대표 색도 기억해서, 썸네일이 뜨기 전 회색 대신 그 색을 깔아 둠.
 */
object ThumbPrefetch {
    private lateinit var ctx: Context
    private lateinit var colorFile: File
    private val colors = ConcurrentHashMap<Long, Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var job: Job? = null
    @Volatile private var latest: List<Item> = emptyList()

    var pending by mutableIntStateOf(0)
        private set

    /** 몇 장 받을 때마다 올라감 → 아직 비어 있는 칸이 폰에 받아진 썸네일로 바로 다시 그려짐 */
    var version by mutableIntStateOf(0)
        private set

    fun init(c: Context) {
        ctx = c.applicationContext
        colorFile = File(ctx.filesDir, "thumb_colors.txt")
        try {
            if (colorFile.exists()) colorFile.forEachLine { l ->
                val i = l.indexOf(',')
                if (i > 0) {
                    val id = l.substring(0, i).toLongOrNull()
                    val col = l.substring(i + 1).toIntOrNull()
                    if (id != null && col != null) colors[id] = col
                }
            }
        } catch (_: Exception) {
        }
    }

    fun color(id: Long): Int? = colors[id]

    /** 전체 목록을 넘겨주면 아직 안 받은 것만 골라 뒤에서 받음 */
    fun ensure(items: List<Item>) {
        if (items.size > latest.size) latest = items
        if (!Settings.prefetchThumbs || job?.isActive == true || Syno.sid == null) return
        job = scope.launch {
            val todo = latest.filter { !colors.containsKey(it.id) }.sortedByDescending { it.time }
            pending = todo.size
            if (todo.isEmpty()) return@launch
            AppLog.add("썸네일", "전체 썸네일 미리 받기 시작 (${todo.size}장)")
            val size = Settings.thumbSize
            val lines = StringBuilder()
            var done = 0
            var wait = 15_000L
            for (chunk in todo.chunked(6)) {
                if (!Settings.prefetchThumbs) break
                UiBusy.waitIdle()
                val ok = coroutineScope {
                    chunk.map { item -> async { fetch(item, size)?.let { item.id to it } } }.awaitAll()
                }
                val got = ok.filterNotNull()
                got.forEach { (id, col) ->
                    colors[id] = col
                    lines.append(id).append(',').append(col).append('\n')
                }
                done += chunk.size
                pending = (todo.size - done).coerceAtLeast(0)
                if (got.isNotEmpty()) version++
                if (lines.length > 4000) flush(lines)
                if (got.size * 2 < chunk.size) {
                    // 절반 넘게 실패 → 연결 문제, 잠깐 쉬었다가
                    AppLog.add("썸네일", "미리 받기 연결 문제 → ${wait / 1000}초 쉬었다가 이어서 (남은 $pending)")
                    runCatching { Syno.countItems(AllSource) }
                    delay(wait)
                    wait = (wait * 2).coerceAtMost(300_000L)
                } else wait = 15_000L
            }
            flush(lines)
            pending = 0
            AppLog.add("썸네일", "전체 썸네일 미리 받기 끝 (${colors.size}장)")
        }
    }

    /** 썸네일 하나를 폰 캐시에 받고 대표 색을 돌려줌 */
    private suspend fun fetch(item: Item, size: String): Int? {
        val key = Syno.thumbKey(item.thumbUnitId, item.cacheKey, size)
        val req = ImageRequest.Builder(ctx)
            .data(thumbData(item, size))
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .size(32)
            .allowHardware(false)
            .build()
        val res = BgImages.get(ctx).execute(req)
        if (res is ErrorResult) return null
        val bmp = ((res as SuccessResult).drawable as? BitmapDrawable)?.bitmap ?: return null
        val one = Bitmap.createScaledBitmap(bmp, 1, 1, true)
        val c = one.getPixel(0, 0)
        if (one != bmp) one.recycle()
        return c
    }

    private fun flush(sb: StringBuilder) {
        if (sb.isEmpty()) return
        try {
            colorFile.appendText(sb.toString())
        } catch (_: Exception) {
        }
        sb.setLength(0)
    }

    fun clear() {
        colors.clear()
        colorFile.delete()
    }
}
