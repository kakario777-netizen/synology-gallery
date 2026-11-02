package com.kakario.synogallery

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * 갤럭시 모션 포토(사진 안에 짧은 영상이 들어 있는 JPG) 꺼내기.
 * 1) 원본 앞부분만 받아 '모션 포토' 표시가 있는지 확인
 * 2) 있으면 원본 전체를 받아 뒤쪽에 붙은 MP4를 잘라 폰에 저장
 * 결과는 기억해 두어서 같은 사진은 다시 받지 않는다.
 */
object MotionPhoto {
    private lateinit var dir: File
    private lateinit var noneFile: File
    private val none = ConcurrentHashMap.newKeySet<Long>()
    private val lock = Mutex()

    private val MARKERS = listOf("MotionPhoto", "MicroVideo", "motionphoto", "MotionPhoto_Data").map { it.toByteArray() }

    fun init(ctx: Context) {
        dir = File(ctx.cacheDir, "motion").apply { mkdirs() }
        noneFile = File(ctx.filesDir, "motion_none.txt")
        try {
            if (noneFile.exists()) noneFile.forEachLine { it.trim().toLongOrNull()?.let { id -> none += id } }
        } catch (_: Exception) {
        }
    }

    private fun videoFile(item: Item) = File(dir, "${item.id}.mp4")

    /** 확인할 필요가 있는 사진인지 (JPG/HEIC 사진만) */
    fun candidate(item: Item): Boolean {
        if (item.isVideo || item.id in none) return false
        val n = item.filename.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".heic") || item.type == "live"
    }

    /** 이미 꺼내 둔 영상이 있으면 바로 반환 */
    fun cached(item: Item): File? = videoFile(item).takeIf { it.exists() && it.length() > 0 }

    /** 모션 포토면 영상 파일, 아니면 null */
    suspend fun video(item: Item): File? = withContext(Dispatchers.IO) {
        cached(item)?.let { return@withContext it }
        if (!candidate(item)) return@withContext null
        lock.withLock {
            cached(item)?.let { return@withLock it }
            val head = Syno.originalHead(item, 160 * 1024) ?: return@withLock null
            if (MARKERS.none { indexOf(head, it, 0) >= 0 }) {
                markNone(item)
                return@withLock null
            }
            val tmp = File(dir, "${item.id}.part")
            if (!Syno.downloadOriginal(item, tmp)) return@withLock null
            val out = extract(tmp, videoFile(item))
            tmp.delete()
            if (out == null) markNone(item)
            out
        }
    }

    private fun markNone(item: Item) {
        none += item.id
        try {
            noneFile.appendText("${item.id}\n")
        } catch (_: Exception) {
        }
    }

    /** 파일 안에서 MP4 시작('....ftyp')을 찾아 그 뒤를 잘라 저장 */
    private fun extract(src: File, dst: File): File? {
        val bytes = src.readBytes()
        val ftyp = "ftyp".toByteArray()
        // 앞쪽 JPG 머리 부분은 건너뛰고, 'MotionPhoto_Data' 표시가 있으면 그 뒤부터 찾음
        val marker = indexOf(bytes, "MotionPhoto_Data".toByteArray(), 0)
        var from = if (marker >= 0) marker else 1024
        while (true) {
            val i = indexOf(bytes, ftyp, from)
            if (i < 4) return null
            val start = i - 4
            val size = ((bytes[start].toInt() and 0xff) shl 24) or ((bytes[start + 1].toInt() and 0xff) shl 16) or
                ((bytes[start + 2].toInt() and 0xff) shl 8) or (bytes[start + 3].toInt() and 0xff)
            if (size in 8..512) {
                RandomAccessFile(dst, "rw").use { f ->
                    f.setLength(0)
                    f.write(bytes, start, bytes.size - start)
                }
                return dst
            }
            from = i + 4
        }
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int): Int {
        if (needle.isEmpty()) return -1
        var i = from.coerceAtLeast(0)
        val last = hay.size - needle.size
        while (i <= last) {
            var j = 0
            while (j < needle.size && hay[i + j] == needle[j]) j++
            if (j == needle.size) return i
            i++
        }
        return -1
    }
}
