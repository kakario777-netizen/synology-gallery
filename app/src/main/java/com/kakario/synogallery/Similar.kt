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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
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
    private lateinit var failFile: File
    private val hashes = ConcurrentHashMap<String, Long>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    /** 새 결과가 생길 때마다 올라감 → 화면이 다시 묶음 */
    var version by mutableIntStateOf(0)
        private set

    @Volatile private var lastBump = 0L

    /** 연결 문제로 잠깐 쉬는 중 (화면 표시용) */
    var waiting by mutableStateOf(false)
        private set

    /** 같은 사진이 계속 실패한 횟수 — 3번 넘으면 이번 실행에서는 건너뜀 (멈춤 방지) */
    private val tries = ConcurrentHashMap<String, Int>()

    /** 아직 분석할 사진 수 (화면 표시용) */
    var pending by mutableIntStateOf(0)
        private set

    fun init(c: Context) {
        ctx = c.applicationContext
        file = File(ctx.filesDir, "similar_hashes.txt")
        failFile = File(ctx.filesDir, "similar_failed.txt")
        try {
            if (file.exists()) file.forEachLine { line ->
                val i = line.indexOf(',')
                // 예전 형식(id_cacheKey)도 사진 id만 떼어서 그대로 씀
                if (i > 0) line.substring(i + 1).toLongOrNull()?.let { hashes[line.substring(0, i).substringBefore('_')] = it }
            }
            // 예전 버전은 통신 오류로 못 받은 사진도 '실패'로 영구 기록해서 묶음이 빠졌음 → 한 번 초기화
            val v2 = File(ctx.filesDir, "similar_v2")
            if (!v2.exists()) {
                failFile.delete()
                v2.createNewFile()
            }
            if (failFile.exists()) failFile.forEachLine { if (it.isNotBlank()) failed += it.trim() }
        } catch (_: Exception) {
        }
    }

    fun clear() {
        hashes.clear()
        failed.clear()
        try {
            file.delete()
            failFile.delete()
        } catch (_: Exception) {
        }
        version++
    }

    val analyzedCount get() = hashes.size

    private fun key(it: Item) = "${it.id}"

    private fun near(a: Item, b: Item, gap: Long = TIME_GAP_SEC) =
        !a.isVideo && !b.isVideo && abs(a.time - b.time) <= gap && sameShape(a, b)

    /** 가장 넓은 단계 기준으로 한 번만 분석 → 단계를 바꿔도 다시 분석하지 않음 */
    private const val ANALYZE_GAP = 1800L

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
            val hasNeighbor = (i > 0 && near(items[i - 1], it, ANALYZE_GAP)) ||
                (i < items.lastIndex && near(it, items[i + 1], ANALYZE_GAP))
            if (hasNeighbor) out += it
        }
        return out
    }

    // ─── 앱 전체에서 한 번만 도는 분석 작업 (화면을 나가도 계속) ───
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lists = ConcurrentHashMap<String, List<Item>>()
    @Volatile private var job: Job? = null

    /** 이 목록도 분석 대상에 넣고, 안 돌고 있으면 시작 */
    fun ensure(key: String, items: List<Item>) {
        lists[key] = items
        if (job?.isActive == true || Syno.sid == null) return
        job = scope.launch {
            AppLog.add("분석", "비슷한 사진 분석 시작 (분석된 사진 ${hashes.size}장)")
            var wait = 15_000L
            while (true) {
                val c = lists.values.flatMap { candidates(it) }.distinctBy { it.id }
                if (c.isEmpty()) break
                if (compute(c)) {
                    wait = 15_000L
                    continue
                }
                // 통신이 끊겼거나 로그인이 만료됨 → 멈추지 않고 잠깐 쉬었다가 이어서
                AppLog.add("분석", "비슷한 사진: 연결 문제로 ${wait / 1000}초 쉬었다가 이어서 (남은 $pending)")
                waiting = true
                // 가벼운 요청 한 번: 로그인이 만료됐으면 여기서 자동으로 다시 로그인됨
                runCatching { Syno.countItems(AllSource) }
                delay(wait)
                wait = (wait * 2).coerceAtMost(300_000L)
                waiting = false
            }
            pending = 0
            version++
            AppLog.add("분석", "비슷한 사진 분석 끝 (분석된 사진 ${hashes.size}장)")
        }
    }

    private sealed interface Res
    private data class Ok(val h: Long) : Res
    private data object Broken : Res     // 그림이 깨짐 → 다시 시도해도 소용없음
    private data object NoNet : Res      // 통신 문제 → 나중에 다시

    /** @return false면 통신 문제로 중단 */
    private suspend fun compute(items: List<Item>): Boolean = withContext(Dispatchers.IO) {
        pending = items.size
        val gate = Semaphore(4)
        val lines = StringBuilder()
        var done = 0
        for (chunk in items.chunked(24)) {
            val results = coroutineScope {
                chunk.map { item ->
                    async {
                        gate.withPermit {
                            val r = try {
                                hashOf(item)
                            } catch (_: Exception) {
                                NoNet
                            }
                            val k = key(item)
                            when (r) {
                                is Ok -> {
                                    hashes[k] = r.h
                                    synchronized(lines) { lines.append(k).append(',').append(r.h).append('\n') }
                                }
                                else -> {
                                    val n = (tries[k] ?: 0) + 1
                                    tries[k] = n
                                    if (n >= 3) failed += k   // 저장은 안 함: 다음 실행 때 다시 시도
                                }
                            }
                            k to r
                        }
                    }
                }.awaitAll()
            }
            done += chunk.size
            pending = (items.size - done).coerceAtLeast(0)
            flush(lines)
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastBump > 3000 || pending == 0) {
                lastBump = now
                version++
            }
            val bad = results.count { it.second !is Ok }
            // 한 묶음에서 절반 넘게 실패하면 그림 문제가 아니라 연결(로그인 만료 등) 문제 → 기록하지 않고 멈춤
            if (bad * 2 > results.size) return@withContext false
            results.filter { it.second == Broken }.forEach { (k, _) ->
                failed += k
                try {
                    synchronized(failFile) { failFile.appendText(k + "\n") }
                } catch (_: Exception) {
                }
            }
        }
        true
    }

    private fun flush(sb: StringBuilder) {
        val text = synchronized(sb) { sb.toString().also { sb.setLength(0) } }
        if (text.isEmpty()) return
        try {
            file.appendText(text)
        } catch (_: Exception) {
        }
    }

    private suspend fun hashOf(item: Item): Res {
        val k = Syno.thumbKey(item.thumbUnitId, item.cacheKey, "sm")
        val req = ImageRequest.Builder(ctx)
            .data(thumbData(item, "sm"))
            .diskCacheKey(k)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .size(72, 72)
            .allowHardware(false)
            .build()
        val res = ctx.imageLoader.execute(req)
        if (res is coil.request.ErrorResult) {
            val t = res.throwable
            return if (t is java.io.IOException || t is coil.network.HttpException) NoNet else Broken
        }
        val bmp = ((res as SuccessResult).drawable as? BitmapDrawable)?.bitmap ?: return Broken
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
        return Ok(h)
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
