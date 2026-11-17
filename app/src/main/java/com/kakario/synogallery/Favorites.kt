package com.kakario.synogallery

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 즐겨찾기 — 시놀로지에는 없고 이 앱(폰)에만 저장됨.
 * 사진 정보를 같이 저장해서 즐겨찾기 탭을 바로 띄울 수 있게 한다.
 */
object Favorites {
    private lateinit var file: File

    /** key = "공간_사진id" */
    private val map = mutableStateMapOf<String, Item>()

    var version by mutableIntStateOf(0)
        private set

    private fun key(id: Long, team: Boolean = Settings.sharedSpace) = "${if (team) "t" else "p"}_$id"

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "favorites.json")
        try {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val item = fromJson(o.getJSONObject("item"))
                map[o.getString("key")] = item
            }
        } catch (_: Exception) {
        }
    }

    fun isFav(id: Long) = map.containsKey(key(id))

    fun allFav(items: Collection<Item>) = items.isNotEmpty() && items.all { isFav(it.id) }

    fun add(items: Collection<Item>) {
        items.forEach { map[key(it.id)] = it }
        save()
    }

    fun remove(ids: Collection<Long>) {
        ids.forEach { map.remove(key(it)) }
        save()
    }

    fun toggle(item: Item) {
        if (isFav(item.id)) remove(listOf(item.id)) else add(listOf(item))
    }

    /** 지금 공간의 즐겨찾기 (정렬 설정 반영) */
    fun list(): List<Item> {
        val prefix = if (Settings.sharedSpace) "t_" else "p_"
        val items = map.filterKeys { it.startsWith(prefix) }.values.toList()
        val sorted = if (Settings.sortBy == "filename") items.sortedBy { it.filename.lowercase() } else items.sortedBy { it.time }
        val ordered = if (Settings.sortDesc) sorted.reversed() else sorted
        return when (Settings.mediaFilter) {
            "photo" -> ordered.filter { !it.isVideo }
            "video" -> ordered.filter { it.isVideo }
            else -> ordered
        }
    }

    private fun save() {
        version++
        try {
            val arr = JSONArray()
            map.forEach { (k, v) -> arr.put(JSONObject().put("key", k).put("item", toJson(v))) }
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun toJson(i: Item) = JSONObject()
        .put("id", i.id).put("filename", i.filename).put("time", i.time).put("type", i.type)
        .put("size", i.size).put("width", i.width).put("height", i.height).put("cacheKey", i.cacheKey)
        .put("thumbUnitId", i.thumbUnitId).put("hasXl", i.hasXl).put("duration", i.duration)
        .put("tags", JSONArray(i.tags)).put("thumbReady", i.thumbReady)

    private fun fromJson(o: JSONObject) = Item(
        id = o.getLong("id"),
        filename = o.optString("filename"),
        time = o.optLong("time"),
        type = o.optString("type", "photo"),
        size = o.optLong("size"),
        width = o.optInt("width"),
        height = o.optInt("height"),
        cacheKey = o.optString("cacheKey"),
        thumbUnitId = o.optLong("thumbUnitId", o.getLong("id")),
        hasXl = o.optBoolean("hasXl"),
        duration = o.optLong("duration"),
        thumbReady = o.optBoolean("thumbReady", true),
        tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
    )
}
