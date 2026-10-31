package com.kakario.synogallery

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** 앱 설정. 값이 바뀌면 화면이 자동으로 다시 그려진다. */
object Settings {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    // 계정
    var host by P("host", "")
    var account by P("account", "")
    var password by P("password", "")
    var autoLogin by P("autoLogin", true)
    var trustAll by P("trustAll", false)

    // 공간: false = 개인 공간, true = 공유 공간
    var sharedSpace by P("sharedSpace", false)

    // 보기
    var columns by P("columns", 4)
    var grouping by P("grouping", "month")        // month / day / none
    var thumbSize by P("thumbSize", "m")          // sm / m / xl
    var showFilename by P("showFilename", false)
    var squareThumbs by P("squareThumbs", true)
    var groupSimilar by P("groupSimilar", false)
    var startTab by P("startTab", 0)               // 0 사진 / 1 앨범 / 2 태그 / 3 폴더

    // 정렬·필터
    var sortBy by P("sortBy", "takentime")        // takentime / filename
    var sortDesc by P("sortDesc", true)
    var mediaFilter by P("mediaFilter", "all")    // all / photo / video

    // 뷰어
    var viewerQuality by P("viewerQuality", "xl") // xl / original
    var slideshowSec by P("slideshowSec", 3)
    var keepScreenOn by P("keepScreenOn", true)
    var viewerBlack by P("viewerBlack", true)

    // 동영상
    var loopVideo by P("loopVideo", false)
    var startMuted by P("startMuted", false)
    var autoPlayVideo by P("autoPlayVideo", true)

    private class P<T>(private val key: String, private val def: T) : ReadWriteProperty<Any?, T> {
        private var st: MutableState<T>? = null

        @Suppress("UNCHECKED_CAST")
        private fun read(): T = when (def) {
            is String -> Settings.sp.getString(key, def) as T
            is Int -> Settings.sp.getInt(key, def) as T
            is Boolean -> Settings.sp.getBoolean(key, def) as T
            else -> def
        }

        private fun state(): MutableState<T> = st ?: mutableStateOf(read()).also { st = it }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state().value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state().value = value
            val e = Settings.sp.edit()
            when (value) {
                is String -> e.putString(key, value)
                is Int -> e.putInt(key, value)
                is Boolean -> e.putBoolean(key, value)
            }
            e.apply()
        }
    }
}
