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

data class TagSource(val id: Long) : Source {
    override val key get() = "tag_$id"
}

/** 뷰어로 넘겨줄 현재 목록 */
object ViewerStore {
    var items: List<Item> = emptyList()
    var lastIndex: Int = -1
    var sourceKey: String = ""
}
