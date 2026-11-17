package com.kakario.synogallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.os.Build
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * NAS가 썸네일을 만들어 주지 않은 사진·영상(갤럭시 HEIC 사진, HEVC 영상 등)은
 * 폰에서 원본을 읽어 직접 썸네일을 만들고 저장해 둔다. 한 번 만들면 다시 안 만듦.
 */
object LocalThumbs {
    private lateinit var dir: File
    private val gate = Semaphore(2)
    private val busy = ConcurrentHashMap<Long, Any>()
    private const val SIZE = 400

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "local_thumbs").apply { mkdirs() }
    }

    /** 남은 개수 (화면 표시용) */
    var pending by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    @Volatile private var job: kotlinx.coroutines.Job? = null
    private val queue = ConcurrentHashMap<Long, Item>()
    private val tries = ConcurrentHashMap<Long, Int>()

    /** 앱을 켜 두면 NAS 썸네일이 없는 사진을 뒤에서 하나씩 전부 만들어 둠 */
    fun ensureAll(items: List<Item>) {
        for (it in items) if (!it.thumbReady && cached(it) == null && (tries[it.id] ?: 0) < 2) queue[it.id] = it
        pending = queue.size
        if (queue.isEmpty() || job?.isActive == true || Syno.sid == null) return
        job = scope.launch {
            AppLog.add("썸네일", "NAS에 없는 썸네일 만들기 시작 (${queue.size}장)")
            var fails = 0
            while (Settings.autoLocalThumbs) {
                UiBusy.waitIdle()
                // 최신 사진부터
                val next = queue.values.maxByOrNull { it.time } ?: break
                val ok = get(next) != null
                queue.remove(next.id)
                if (ok) fails = 0 else {
                    tries[next.id] = (tries[next.id] ?: 0) + 1
                    if (tries[next.id]!! < 2) queue[next.id] = next
                    fails++
                    // 연달아 실패하면 연결 문제 → 잠깐 쉬었다가
                    if (fails >= 6) {
                        AppLog.add("썸네일", "연달아 실패 → 1분 쉬었다가 이어서 (남은 ${queue.size})")
                        kotlinx.coroutines.delay(60_000)
                        fails = 0
                    }
                }
                pending = queue.size
            }
            pending = queue.size
        }
    }

    private fun file(item: Item) = File(dir, "${item.id}_${item.cacheKey.hashCode()}.jpg")

    fun cached(item: Item): File? = file(item).takeIf { it.exists() && it.length() > 0 }

    suspend fun get(item: Item): File? = withContext(Dispatchers.IO) {
        cached(item)?.let { return@withContext it }
        gate.withPermit {
            val lock = busy.getOrPut(item.id) { Any() }
            synchronized(lock) {
                try {
                    cached(item) ?: make(item)
                } finally {
                    busy.remove(item.id)
                }
            }
        }
    }

    private fun make(item: Item): File? {
        val bmp = (if (item.isVideo) videoFrame(item) else photo(item)) ?: return null
        val out = file(item)
        val scaled = scale(bmp)
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (scaled != bmp) scaled.recycle()
        bmp.recycle()
        return out
    }

    private fun scale(b: Bitmap): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= SIZE) return b
        val r = SIZE.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * r).toInt().coerceAtLeast(1), (b.height * r).toInt().coerceAtLeast(1), true)
    }

    /** 원본 사진을 받아서 작게 줄여 읽기 (HEIC도 안드로이드 9 이상이면 읽힘, 회전도 자동) */
    private fun photo(item: Item): Bitmap? {
        val tmp = File(dir, "tmp_${item.id}")
        try {
            if (!Syno.downloadOriginal(item, tmp)) return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(tmp)) { d, info, _ ->
                    val m = maxOf(info.size.width, info.size.height)
                    var s = 1
                    while (m / (s * 2) >= SIZE) s *= 2
                    d.setTargetSampleSize(s)
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tmp.path, o)
                var s = 1
                while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= SIZE) s *= 2
                BitmapFactory.decodeFile(tmp.path, BitmapFactory.Options().apply { inSampleSize = s })
            }
        } catch (e: Exception) {
            AppLog.err("썸네일", "사진 썸네일 만들기 실패 (${item.filename})", e)
            return null
        } finally {
            tmp.delete()
        }
    }

    /** 영상은 통째로 받지 않고 앞부분만 읽어 1초 지점 화면을 뽑음 */
    private fun videoFrame(item: Item): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(Syno.originalUrl(item), HashMap())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                r.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, SIZE, SIZE)
            } else r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            AppLog.err("썸네일", "영상 썸네일 만들기 실패 (${item.filename})", e)
            null
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }
}

/** 화면에서 쓰는 썸네일 데이터: NAS 썸네일이 있으면 주소, 없으면 폰에서 만든 썸네일 */
data class LocalThumb(val item: Item)

fun thumbData(item: Item, size: String): Any =
    if (item.thumbReady) Syno.thumbUrl(item.thumbUnitId, item.cacheKey, size) else LocalThumb(item)

class LocalThumbFetcher(private val data: LocalThumb) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val f = LocalThumbs.get(data.item) ?: throw IOException("썸네일을 만들지 못했어요")
        return SourceResult(
            source = ImageSource(f.toOkioPath(), FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<LocalThumb> {
        override fun create(data: LocalThumb, options: Options, imageLoader: ImageLoader): Fetcher = LocalThumbFetcher(data)
    }
}
