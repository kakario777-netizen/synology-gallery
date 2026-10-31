package com.kakario.synogallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * 비슷한 사진 묶기 (구글 포토의 '비슷한 사진' 쌓기와 비슷한 기준).
 * - 촬영 시각이 가까운(단계에 따라 45초~30분) 사진들 중에서
 * - 작은 썸네일의 모양(dHash)이 거의 같으면 한 묶음.
 * 계산은 전부 폰에서 하고, 결과는 폰에 저장해 다음부터 바로 쓴다.
 */
object Similar {
    /** 단계별 기준: (앞뒤 사진 시간 간격 초, 허용하는 모양 차이 / 64) */
    private fun level() = when (Settings.similarLevel) {
        1 -> 45L to 9
        2 -> 180L to 14
        4 -> 1800L to 24
        else -> 600L to 19
    }
    private val TIME_GAP_SEC get() = level().first
    private val MAX_DISTANCE get() = level().second

    private lateinit var ctx: Context
    private lateinit var file: File
    private val hashes = ConcurrentHashMap<String, Long>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    /** 새 결과가 생길 때마다 올라감 → 화면이 다시 묶음 */
    var version by mutableIntStateOf(0)
        private set

    /** 아직 분석할 사진 수 (화면 표시용) */
    var pending by mutableIntStateOf(0)
        private set

    fun init(c: Context) {
        ctx = c.applicationContext
        file = File(ctx.filesDir, "similar_hashes.txt")
        try {
            if (file.exists()) file.forEachLine { line ->
                val i = line.indexOf(',')
                if (i > 0) line.substring(i + 1).toLongOrNull()?.let { hashes[line.substring(0, i)] = it }
            }
        } catch (_: Exception) {
        }
    }

    fun clear() {
        hashes.clear()
        failed.clear()
        try {
            file.delete()
        } catch (_: Exception) {
        }
        version++
    }

    val analyzedCount get() = hashes.size

    private fun key(it: Item) = "${it.id}_${it.cacheKey}"

    private fun near(a: Item, b: Item) =
        !a.isVideo && !b.isVideo && abs(a.time - b.time) <= TIME_GAP_SEC && sameShape(a, b)

    private fun sameShape(a: Item, b: Item): Boolean {
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return true
        return (a.width >= a.height) == (b.width >= b.height)
    }

    /** 분석이 필요한 사진: 시간이 가까운 이웃이 있는 사진만 (나머지는 묶일 일이 없음) */
    fun candidates(items: List<Item>): List<Item> {
        val out = ArrayList<Item>()
        for (i in items.indices) {
            val it = items[i]
            val k = key(it)
            if (hashes.containsKey(k) || failed.contains(k)) continue
            val hasNeighbor = (i > 0 && near(items[i - 1], it)) || (i < items.lastIndex && near(it, items[i + 1]))
            if (hasNeighbor) out += it
        }
        return out
    }

    suspend fun compute(items: List<Item>) = withContext(Dispatchers.IO) {
        pending = items.size
        val gate = Semaphore(4)
        val lines = StringBuilder()
        var done = 0
        coroutineScope {
            items.chunked(24).forEach { chunk ->
                chunk.map { item ->
                    async {
                        gate.withPermit {
                            val h = try {
                                hashOf(item)
                            } catch (_: Exception) {
                                null
                            }
                            val k = key(item)
                            if (h != null) {
                                hashes[k] = h
                                synchronized(lines) { lines.append(k).append(',').append(h).append('\n') }
                            } else failed += k
                        }
                    }
                }.awaitAll()
                done += chunk.size
                pending = (items.size - done).coerceAtLeast(0)
                flush(lines)
                version++
            }
        }
        pending = 0
    }

    private fun flush(sb: StringBuilder) {
        val text = synchronized(sb) { sb.toString().also { sb.setLength(0) } }
        if (text.isEmpty()) return
        try {
            file.appendText(text)
        } catch (_: Exception) {
        }
    }

    private suspend fun hashOf(item: Item): Long? {
        val k = Syno.thumbKey(item.thumbUnitId, item.cacheKey, "sm")
        val req = ImageRequest.Builder(ctx)
            .data(Syno.thumbUrl(item.thumbUnitId, item.cacheKey, "sm"))
            .diskCacheKey(k)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .size(72, 72)
            .allowHardware(false)
            .build()
        val res = ctx.imageLoader.execute(req) as? SuccessResult ?: return null
        val bmp = (res.drawable as? BitmapDrawable)?.bitmap ?: return null
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        var h = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (lum(small.getPixel(x, y)) > lum(small.getPixel(x + 1, y))) h = h or (1L shl bit)
                bit++
            }
        }
        if (small != bmp) small.recycle()
        return h
    }

    private fun lum(c: Int) = Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114

    private fun similar(a: Item, b: Item): Boolean {
        if (!near(a, b)) return false
        val ha = hashes[key(a)] ?: return false
        val hb = hashes[key(b)] ?: return false
        return java.lang.Long.bitCount(ha xor hb) <= MAX_DISTANCE
    }

    /** 목록을 묶음으로 나눔 (각 묶음은 목록 순서대로의 위치들) */
    fun groups(items: List<Item>): List<List<Int>> {
        val out = ArrayList<List<Int>>(items.size)
        var cur = ArrayList<Int>()
        for (i in items.indices) {
            if (cur.isNotEmpty() && !similar(items[cur.last()], items[i])) {
                out += cur
                cur = ArrayList()
            }
            cur += i
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }
}
