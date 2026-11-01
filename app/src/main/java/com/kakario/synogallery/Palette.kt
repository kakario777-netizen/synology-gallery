package com.kakario.synogallery

import androidx.compose.ui.graphics.Color

/** 배경색 테마 (설정에서 선택) */
object Palette {
    val BACKGROUNDS = listOf("white" to "흰색", "ivory" to "아이보리", "gray" to "연회색", "dark" to "다크")

    val dark get() = Settings.bgTheme == "dark"

    /** 화면 배경 */
    val bg: Color
        get() = when (Settings.bgTheme) {
            "ivory" -> Color(0xFFFBF8F1)
            "gray" -> Color(0xFFF2F2F5)
            "dark" -> Color(0xFF121214)
            else -> Color.White
        }

    /** 기본 글자 */
    val fg get() = if (dark) Color(0xFFEDEDF0) else Color(0xFF1B1B1F)

    /** 보조 글자 */
    val sub get() = if (dark) Color(0xFF9A9AA2) else Color(0xFF6B6B70)

    /** 카드·떠 있는 바 */
    val card: Color
        get() = when (Settings.bgTheme) {
            "dark" -> Color(0xFF1E1E22)
            "gray" -> Color.White
            "ivory" -> Color(0xFFFFFDF8)
            else -> Color.White
        }

    /** 썸네일 자리(불러오기 전) */
    val placeholder get() = if (dark) Color(0xFF26262B) else Color(0xFFE9E9EE)

    val divider get() = if (dark) Color(0xFF2C2C32) else Color(0xFFE6E6EA)
}
