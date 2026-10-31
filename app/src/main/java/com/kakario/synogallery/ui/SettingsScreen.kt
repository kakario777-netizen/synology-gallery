package com.kakario.synogallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.kakario.synogallery.Settings
import com.kakario.synogallery.Syno
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(nav: NavController, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheMsg by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item { Section("보기") }
            item {
                Choice(
                    "한 줄에 보이는 사진 수", Settings.columns,
                    (2..7).map { "$it" to it },
                ) { Settings.columns = it }
                Hint("갤러리에서 두 손가락으로 오므리거나 벌려도 바뀌어요. 펼친 화면(폴드)에서는 2개씩 더 보여요.")
            }
            item {
                Choice(
                    "날짜 묶음", Settings.grouping,
                    listOf("월별" to "month", "일별" to "day", "묶지 않음" to "none"),
                ) { Settings.grouping = it }
            }
            item {
                Choice(
                    "썸네일 화질", Settings.thumbSize,
                    listOf("빠르게" to "sm", "보통" to "m", "선명하게" to "xl"),
                ) { Settings.thumbSize = it }
            }
            item { Toggle("정사각형 썸네일", "끄면 사진 비율 그대로 보여줘요", Settings.squareThumbs) { Settings.squareThumbs = it } }
            item { Toggle("파일명 표시", null, Settings.showFilename) { Settings.showFilename = it } }

            item { Section("정렬 · 필터") }
            item {
                Choice(
                    "정렬 기준", Settings.sortBy,
                    listOf("촬영일" to "takentime", "파일명" to "filename"),
                ) { Settings.sortBy = it }
            }
            item {
                Choice(
                    "정렬 순서", Settings.sortDesc,
                    listOf("최신순" to true, "오래된순" to false),
                ) { Settings.sortDesc = it }
            }
            item {
                Choice(
                    "보여줄 종류", Settings.mediaFilter,
                    listOf("전체" to "all", "사진만" to "photo", "동영상만" to "video"),
                ) { Settings.mediaFilter = it }
            }

            item { Section("사진 뷰어") }
            item {
                Choice(
                    "사진 화질", Settings.viewerQuality,
                    listOf("빠르게 (큰 미리보기)" to "xl", "원본" to "original"),
                ) { Settings.viewerQuality = it }
                Hint("원본은 선명하지만 외부망(LTE/5G)에서는 느릴 수 있어요.")
            }
            item {
                Choice(
                    "슬라이드쇼 간격", Settings.slideshowSec,
                    listOf(2, 3, 5, 10, 20).map { "${it}초" to it },
                ) { Settings.slideshowSec = it }
            }
            item { Toggle("볼 때 화면 켜짐 유지", null, Settings.keepScreenOn) { Settings.keepScreenOn = it } }
            item { Toggle("완전 검은 배경", null, Settings.viewerBlack) { Settings.viewerBlack = it } }

            item { Section("동영상") }
            item { Toggle("열면 바로 재생", null, Settings.autoPlayVideo) { Settings.autoPlayVideo = it } }
            item { Toggle("반복 재생", null, Settings.loopVideo) { Settings.loopVideo = it } }
            item { Toggle("소리 끄고 시작", null, Settings.startMuted) { Settings.startMuted = it } }

            item { Section("연결") }
            item {
                Choice(
                    "사진 공간", Settings.sharedSpace,
                    listOf("개인 공간" to false, "공유 공간" to true),
                ) { Settings.sharedSpace = it }
            }
            item {
                Toggle("인증서 무시", "자체 서명 인증서(IP:5001 등)로 접속할 때 켜요", Settings.trustAll) {
                    Settings.trustAll = it
                    Syno.rebuildClient()
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("접속 중", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${Settings.account} @ ${Settings.host}",
                        color = Color.Gray, style = MaterialTheme.typography.bodySmall,
                    )
                    if (Syno.route.isNotEmpty()) {
                        Text(
                            "${Syno.route} (${Syno.currentBase})",
                            color = Color.Gray, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ctx.imageLoader.diskCache?.clear()
                            }
                            ctx.imageLoader.memoryCache?.clear()
                            cacheMsg = "비웠어요"
                        }
                    }) { Text("사진 캐시 비우기") }
                    Text(cacheMsg, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                OutlinedButton(onClick = onLogout, modifier = Modifier.padding(horizontal = 16.dp)) { Text("로그아웃") }
            }
            item {
                val ver = remember {
                    try {
                        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                    } catch (e: Exception) {
                        ""
                    }
                }
                Text(
                    "버전 $ver", color = Color.Gray, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(16.dp, 24.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp), color = Color(0xFF2A2A2E))
        Text(
            title, color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text, color = Color.Gray, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun <T> Choice(title: String, selected: T, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (label, v) ->
                FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun Toggle(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (sub != null) Text(sub, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
