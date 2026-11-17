package com.kakario.synogallery

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 사진 목록을 폰에 저장해 두고 다음에 열 때 바로 보여줌.
 * 한 줄에 사진 하나 (탭으로 구분) — 수만 장도 빠르게 읽힘.
 */
object ItemCache {
    private lateinit var dir: File
    private const val VERSION = "v2"

    data class Cached(val serverCount: Int, val items: List<Item>)

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "item_cache").apply { mkdirs() }
    }

    private fun file(key: String) = File(dir, key.replace("[^A-Za-z0-9_.-]".toRegex(), "_") + ".tsv")

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    suspend fun load(key: String): Cached? = withContext(Dispatchers.IO) {
        val f = file(key)
        if (!f.exists()) return@withContext null
        try {
            f.bufferedReader().use { r ->
                val head = r.readLine()?.split('\t') ?: return@use null
                if (head.getOrNull(0) != VERSION) return@use null
                val count = head.getOrNull(1)?.toIntOrNull() ?: return@use null
                val out = ArrayList<Item>(count)
                while (true) {
                    val line = r.readLine() ?: break
                    parse(line)?.let { out += it }
                }
                Cached(count, out)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun save(key: String, serverCount: Int, items: List<Item>) = withContext(Dispatchers.IO) {
        try {
            val tmp = File(dir, "tmp_${System.nanoTime()}")
            tmp.bufferedWriter().use { w ->
                w.write("$VERSION\t$serverCount\n")
                for (i in items) {
                    w.write(line(i))
                    w.write("\n")
                }
            }
            tmp.renameTo(file(key))
        } catch (_: Exception) {
        }
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\u001f', ' ')

    private fun line(i: Item) = buildString {
        append(i.id).append('\t')
        append(clean(i.filename)).append('\t')
        append(i.time).append('\t')
        append(i.type).append('\t')
        append(i.size).append('\t')
        append(i.width).append('\t')
        append(i.height).append('\t')
        append(clean(i.cacheKey)).append('\t')
        append(i.thumbUnitId).append('\t')
        append(if (i.hasXl) 1 else 0).append('\t')
        append(i.duration).append('\t')
        append(i.tags.joinToString("\u001f") { clean(it) }).append('\t')
        append(i.lat ?: "").append('\t')
        append(i.lon ?: "").append('\t')
        append(if (i.thumbReady) 1 else 0)
    }

    private fun parse(line: String): Item? {
        val p = line.split('\t')
        if (p.size < 14) return null
        return try {
            Item(
                id = p[0].toLong(),
                filename = p[1],
                time = p[2].toLong(),
                type = p[3],
                size = p[4].toLong(),
                width = p[5].toInt(),
                height = p[6].toInt(),
                cacheKey = p[7],
                thumbUnitId = p[8].toLong(),
                hasXl = p[9] == "1",
                duration = p[10].toLong(),
                tags = if (p[11].isEmpty()) emptyList() else p[11].split('\u001f'),
                lat = p[12].toDoubleOrNull(),
                lon = p[13].toDoubleOrNull(),
                thumbReady = p.getOrNull(14) != "0",
            )
        } catch (_: Exception) {
            null
        }
    }
}
