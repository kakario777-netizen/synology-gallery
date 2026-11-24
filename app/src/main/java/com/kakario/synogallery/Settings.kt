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
        // 접속 정보는 안드로이드 키 저장소로 암호화한 별도 저장소에
        sec = try {
            val alias = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC)
            androidx.security.crypto.EncryptedSharedPreferences.create(
                "secure_settings", alias, ctx,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            AppLog.err("보안", "암호화 저장소를 열지 못함", e)
            sp
        }
        // 예전 버전에서 그냥 저장했던 접속 정보를 암호화 저장소로 옮김
        if (sec !== sp) {
            val e = sp.edit()
            val s = sec.edit()
            var moved = false
            for (k in SECURE) if (sp.contains(k)) {
                s.putString(k, sp.getString(k, ""))
                e.remove(k)
                moved = true
            }
            if (moved) {
                s.commit()
                e.commit()
            }
        }
    }

    private lateinit var sec: SharedPreferences
    private val SECURE = setOf("host", "account", "password", "savedSid", "savedSidFor", "lastBase", "lastBaseFor")
    private fun store(key: String) = if (key in SECURE) sec else sp

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
    var tabOrder by P("tabOrder", "0,1,2,3")       // 탭 순서
    var hiddenTabs by P("hiddenTabs", "")          // 숨긴 탭
    var showTags by P("showTags", false)
    var similarLevel by P("similarLevel", 3)       // 1 엄격 ~ 4 아주 넓게
    var accent by P("accent", "red")
    var bgTheme by P("bgTheme", "white")          // white / ivory / gray / dark
    var inlineVideo by P("inlineVideo", false)    // 뷰어에서 영상 바로 재생
    var motionPhoto by P("motionPhoto", "manual")   // auto / manual / off (갤럭시 모션 포토)
    var autoLocalThumbs by P("autoLocalThumbs", true) // NAS에 없는 썸네일을 앱이 미리 전부 만들기
    var thumbsPaused by P("thumbsPaused", false)     // 썸네일 받기 일시정지
    var thumbsInBackground by P("thumbsInBackground", true) // 알림을 띄워 화면이 꺼져도 계속 받기
    var prefetchThumbs by P("prefetchThumbs", true)  // 전체 썸네일 미리 받기
    var savedSid by P("savedSid", "")                // 로그인 기억 (앱을 다시 켜면 바로)
    var savedSidFor by P("savedSidFor", "")
    var cacheGb by P("cacheGb", 30)                 // 사진 캐시(썸네일·큰 사진) 최대 용량
    var allVideosAlbum by P("allVideosAlbum", true) // 앨범 탭 맨 앞 '모든 동영상'
    var videoSort by P("videoSort", "date_desc")  // date_desc / date_asc / name / size / length

    // 마지막으로 연결된 실제 주소 (QuickConnect 다시 찾는 시간 절약)
    var lastBase by P("lastBase", "")
    var lastBaseFor by P("lastBaseFor", "")
    var lastBaseInsecure by P("lastBaseInsecure", false)
    var lastRoute by P("lastRoute", "")

    // 잠금
    var lockPattern by P("lockPattern", "")        // 패턴 해시 (비어 있으면 잠금 꺼짐)
    var lockBiometric by P("lockBiometric", true)
    var lockDelaySec by P("lockDelaySec", 0)

    /** 보이는 탭 (순서대로) */
    fun visibleTabs(): List<Int> {
        val hidden = hiddenTabs.split(',').mapNotNull { it.toIntOrNull() }.toSet()
        val order = tabOrder.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0..5 }.distinct()
        val full = order + (0..5).filter { it !in order }
        return full.filter { it !in hidden }.ifEmpty { listOf(0) }
    }

    fun orderedTabs(): List<Int> {
        val order = tabOrder.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0..5 }.distinct()
        return order + (0..5).filter { it !in order }
    }

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
            is String -> Settings.store(key).getString(key, def) as T
            is Int -> Settings.store(key).getInt(key, def) as T
            is Boolean -> Settings.store(key).getBoolean(key, def) as T
            else -> def
        }

        private fun state(): MutableState<T> = st ?: mutableStateOf(read()).also { st = it }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state().value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state().value = value
            val e = Settings.store(key).edit()
            when (value) {
                is String -> e.putString(key, value)
                is Int -> e.putInt(key, value)
                is Boolean -> e.putBoolean(key, value)
            }
            e.apply()
        }
    }
}
