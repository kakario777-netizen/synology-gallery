package com.kakario.synogallery

data class Item(
    val id: Long,
    val filename: String,
    val time: Long,          // 촬영 시각 (초)
    val type: String,        // photo / video / live
    val size: Long,
    val width: Int,
    val height: Int,
    val cacheKey: String,
    val thumbUnitId: Long,
    val hasXl: Boolean,
    val duration: Long,      // 동영상 길이 (ms), 모르면 0
    val tags: List<String> = emptyList(),
) {
    val isVideo get() = type == "video"
}

data class Album(
    val id: Long,
    val name: String,
    val count: Int,
    val cacheKey: String,
    val thumbUnitId: Long,
)

data class Tag(
    val id: Long,
    val name: String,
    val count: Int,
    val cacheKey: String,
    val thumbUnitId: Long,
)

data class Folder(val id: Long, val name: String) {
    val shortName get() = name.trimEnd('/').substringAfterLast('/').ifEmpty { name }
}

sealed interface Source {
    val key: String
}

data object AllSource : Source {
    override val key = "all"
}

data class AlbumSource(val id: Long) : Source {
    override val key get() = "album_$id"
}

data class FolderSource(val id: Long) : Source {
    override val key get() = "folder_$id"
}

/** 모든 동영상 */
data object VideosSource : Source {
    override val key = "videos"
}

data object FavSource : Source {
    override val key = "fav"
}

data class TagSource(val id: Long) : Source {
    override val key get() = "tag_$id"
}

/** 뷰어로 넘겨줄 현재 목록 */
object ViewerStore {
    /** 뷰어 한 장 = 묶음 하나 (묶이지 않은 사진은 1장짜리 묶음) */
    var stacks: List<List<Item>> = emptyList()
    val items: List<Item> get() = stacks.map { it.first() }

    /** 전체 화면 플레이어로 넘길 영상 */
    var playItem: Item? = null
    var lastIndex: Int = -1
    var sourceKey: String = ""
}
