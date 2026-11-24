package com.kakario.synogallery

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 앱이 튕기면 원인을 파일로 남겨 두고, 다음에 켤 때 보여줌 (복사해서 알려주기용) */
object CrashLog {
    private lateinit var file: File

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "last_crash.txt")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val ver = try {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                } catch (_: Exception) {
                    "?"
                }
                file.writeText(
                    "시놀 갤러리 $ver / Android ${Build.VERSION.RELEASE} / ${Build.MODEL}\n" +
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date()) +
                        " (스레드: ${t.name})\n" +
                        Runtime.getRuntime().let { r -> "메모리: 사용 ${(r.totalMemory() - r.freeMemory()) / 1048576}MB / 최대 ${r.maxMemory() / 1048576}MB" } +
                        "\n\n" + AppLog.mask(sw.toString()).take(6000)
                )
            } catch (_: Exception) {
            }
            AppLog.err("튕김", "앱이 꺼짐 (스레드 ${t.name}) ${e.stackTrace.take(3).joinToString(" < ")}", e)
            prev?.uncaughtException(t, e)
        }
    }

    fun read(): String? = file.takeIf { it.exists() }?.readText()

    private val seen get() = File(file.parentFile, "last_crash_seen")

    /** 아직 안내하지 않은 튕김 기록 */
    fun unseen(): String? = if (file.exists() && (!seen.exists() || seen.lastModified() < file.lastModified())) read() else null

    /** 안내했음 (기록은 '최근 기록 복사'에 계속 포함) */
    fun markSeen() {
        try {
            seen.writeText("1")
        } catch (_: Exception) {
        }
    }

    fun clear() {
        file.delete()
    }
}
