package com.kakario.synogallery.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** 전체화면(상태바·내비게이션바 숨김) */
@Composable
fun SystemBars(visible: Boolean, darkScreen: Boolean = true) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(visible, darkScreen) {
        val c = WindowCompat.getInsetsController(window, view)
        c.isAppearanceLightStatusBars = !darkScreen
        c.isAppearanceLightNavigationBars = !darkScreen
        if (visible) {
            c.show(WindowInsetsCompat.Type.systemBars())
        } else {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            c.show(WindowInsetsCompat.Type.systemBars())
            c.isAppearanceLightStatusBars = !com.kakario.synogallery.Palette.dark
            c.isAppearanceLightNavigationBars = !com.kakario.synogallery.Palette.dark
        }
    }
}

@Composable
fun MessageBox(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = com.kakario.synogallery.Palette.sub, textAlign = TextAlign.Center)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color(0x99000000), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) { Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall) }
}

// 날짜 글자 만들기: 여러 일꾼이 동시에 써도 안전한 방식 (예전 방식은 동시에 쓰면 망가져서 앱이 꺼졌음)
private val monthFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREA)
private val dayFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREA)
private val fullFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd (E) a h:mm", Locale.KOREA)
private val yearFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy년", Locale.KOREA)

private fun at(sec: Long) = java.time.Instant.ofEpochSecond(sec).atZone(java.time.ZoneId.systemDefault())

fun yearLabel(sec: Long): String = yearFmt.format(at(sec))
fun monthLabel(sec: Long): String = monthFmt.format(at(sec))
fun dayLabel(sec: Long): String = dayFmt.format(at(sec))
fun fullDate(sec: Long): String = fullFmt.format(at(sec))

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
