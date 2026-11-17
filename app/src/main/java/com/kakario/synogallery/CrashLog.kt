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
                        " (스레드: ${t.name})\n\n" + sw.toString().take(6000)
                )
            } catch (_: Exception) {
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun read(): String? = file.takeIf { it.exists() }?.readText()

    fun clear() {
        file.delete()
    }
}
