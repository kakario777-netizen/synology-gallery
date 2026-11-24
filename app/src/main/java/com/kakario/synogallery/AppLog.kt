package com.kakario.synogallery

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 앱 기록: 접속·로그인·로그아웃·오류·튕김·업데이트 등 특이사항을 시간순으로 남김.
 * 설정 → 앱 기록에서 보고 복사할 수 있음. 너무 커지면 오래된 기록부터 지움.
 */
object AppLog {
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA)
    private const val MAX = 400_000L

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "app_log.txt")
    }

    /** 종류: 시작·접속·로그인·로그아웃·오류·튕김·업데이트·분석·썸네일·목록 등 */
    @Synchronized
    fun add(kind: String, msg: String) {
        val f = file ?: return
        try {
            if (f.length() > MAX) {
                // 뒤쪽 절반만 남김
                val keep = f.readText().takeLast((MAX / 2).toInt())
                f.writeText(keep.substring(keep.indexOf('\n') + 1))
            }
            f.appendText("${fmt.format(Date())} [$kind] ${mask(msg).replace('\n', ' ')}\n")
        } catch (_: Exception) {
        }
    }

    fun err(kind: String, msg: String, e: Throwable?) =
        add(kind, "$msg — ${e?.javaClass?.simpleName ?: ""}: ${e?.message ?: ""}")

    fun read(): String = try {
        file?.takeIf { it.exists() }?.readText().orEmpty()
    } catch (_: Exception) {
        ""
    }

    @Synchronized
    fun clear() {
        file?.delete()
    }

    /** 기록에 NAS 주소·IP·세션값이 남지 않게 가림 (복사해서 공유해도 안전하게) */
    fun mask(s: String): String = s
        .replace(Regex("(https?://)[^/\\s:]+"), "$1***")
        .replace(Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b"), "***.***.***.***")
        .replace(Regex("[A-Za-z0-9-]+\\.(quickconnect\\.(to|cn)|synology\\.me|direct\\.quickconnect\\.to)"), "***.$1")
        .replace(Regex("(_sid|sid|passwd|SynoToken)=[^&\\s]+"), "$1=***")
}
