package com.kakario.synogallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.collectAsState
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
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.kakario.synogallery.ACCENTS
import com.kakario.synogallery.Settings
import com.kakario.synogallery.Similar
import com.kakario.synogallery.Syno
import com.kakario.synogallery.accentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 확인이 필요한 동작 */
private data class Ask(val title: String, val text: String, val confirm: String, val action: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(nav: NavController, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheMsg by remember { mutableStateOf("") }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var settingPattern by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    // 최근 200줄 + 기기 정보 (채팅에 붙여 넣기용)
    fun recentLog(): String {
        val ver = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        val body = com.kakario.synogallery.AppLog.read().lines().filter { it.isNotBlank() }.takeLast(200).joinToString("\n")
        val crash = com.kakario.synogallery.CrashLog.read()?.let { "\n\n[마지막 튕김 상세]\n$it" } ?: ""
        return "시놀 갤러리 $ver / Android ${android.os.Build.VERSION.RELEASE} / ${android.os.Build.MODEL}\n$body$crash"
    }

    if (settingPattern) {
        PatternSetup(
            onDone = { hash ->
                Settings.lockPattern = hash
                AppLock.armed()
                settingPattern = false
                toast(ctx, "앱 잠금을 켰어요")
            },
            onCancel = { settingPattern = false },
        )
        return
    }

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
            // ─── 화면 ───
            item { Section("화면") }
            item { AccentPicker() }
            item {
                Choice(
                    "배경색", Settings.bgTheme,
                    com.kakario.synogallery.Palette.BACKGROUNDS.map { (k, label) -> label to k },
                ) { Settings.bgTheme = it }
            }
            item {
                val visible = Settings.visibleTabs()
                Choice(
                    "첫 화면", Settings.startTab.takeIf { it in visible } ?: visible.first(),
                    visible.map { HOME_TABS[it] to it },
                ) { Settings.startTab = it }
            }
            item { TabManager() }
            item {
                Toggle(
                    "앨범 탭에 '모든 동영상' 앨범",
                    "앨범 탭 맨 앞에 모든 동영상을 모아 보는 앨범을 보여줘요",
                    Settings.allVideosAlbum,
                ) { Settings.allVideosAlbum = it }
            }

            // ─── 보기 ───
            item { Section("사진 목록") }
            item {
                Choice(
                    "날짜별로 나누기", Settings.grouping,
                    listOf("연도별" to "year", "월별" to "month", "일별" to "day", "나누지 않음" to "none"),
                ) { Settings.grouping = it }
            }
            item {
                Choice(
                    "한 줄에 보이는 사진 수", Settings.columns,
                    (2..7).map { "$it" to it },
                ) { Settings.columns = it }
                Hint("두 손가락으로 오므리거나 벌려도 바뀌어요. 펼친 화면(폴드)에서는 2개씩 더 보여요.")
            }
            item {
                Choice(
                    "썸네일 화질", Settings.thumbSize,
                    listOf("빠르게" to "sm", "보통" to "m", "선명하게" to "xl"),
                ) { Settings.thumbSize = it }
            }
            item {
                Toggle(
                    "없는 썸네일 미리 만들기",
                    "NAS가 썸네일을 안 만들어 둔 사진·영상(HEIC·HEVC 등)을 앱을 켜 둔 동안 미리 전부 만들어 둬요",
                    Settings.autoLocalThumbs,
                ) { Settings.autoLocalThumbs = it }
            }
            item { Toggle("사진에 태그 표시", "썸네일 아래쪽에 붙은 태그를 보여줘요", Settings.showTags) { Settings.showTags = it } }
            item { Toggle("정사각형 썸네일", "끄면 사진 비율 그대로 보여줘요", Settings.squareThumbs) { Settings.squareThumbs = it } }
            item { Toggle("파일명 표시", null, Settings.showFilename) { Settings.showFilename = it } }

            // ─── 비슷한 사진 ───
            item { Section("비슷한 사진") }
            item {
                Toggle(
                    "비슷한 사진 모으기",
                    "비슷한 사진을 한 장으로 묶고 장수를 표시해요 (촬영일 정렬일 때)",
                    Settings.groupSimilar,
                ) { Settings.groupSimilar = it }
            }
            if (Settings.groupSimilar) {
                item {
                    Choice(
                        "묶는 정도", Settings.similarLevel,
                        listOf("1 꼼꼼히" to 1, "2 보통" to 2, "3 넓게" to 3, "4 아주 넓게" to 4),
                    ) { Settings.similarLevel = it }
                    Hint("숫자가 클수록 시간 차이가 크고 조금 달라 보이는 사진도 함께 묶어요. 구글 포토와 비슷한 건 3이에요.")
                }
                item {
                    OutlinedButton(
                        onClick = {
                            ask = Ask("비슷한 사진 다시 분석", "저장된 분석 결과를 지우고 처음부터 다시 분석할까요?", "다시 분석") {
                                Similar.clear()
                                toast(ctx, "처음부터 다시 분석해요")
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    ) { Text("다시 분석") }
                }
            }

            // ─── 정렬·필터 ───
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

            // ─── 사진 뷰어 ───
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
            item { Toggle("사진 볼 때 검은 배경", "끄면 흰 배경으로 보여요", Settings.viewerBlack) { Settings.viewerBlack = it } }

            // ─── 동영상 ───
            item { Section("동영상") }
            item {
                Toggle(
                    "넘기면서 바로 재생",
                    "구글 포토처럼 사진 보다가 영상이 나오면 그 자리에서 재생돼요. 끄면 눌렀을 때 플레이어가 열려요",
                    Settings.inlineVideo,
                ) { Settings.inlineVideo = it }
            }
            item { Toggle("플레이어 열면 바로 재생", null, Settings.autoPlayVideo) { Settings.autoPlayVideo = it } }
            item {
                Choice(
                    "갤럭시 모션 포토", Settings.motionPhoto,
                    listOf("자동 재생" to "auto", "버튼으로 재생" to "manual", "표시 안 함" to "off"),
                ) { Settings.motionPhoto = it }
                Hint("모션 포토는 사진을 열 때 원본을 한 번 확인해요. 버튼으로 재생은 사진 위쪽의 모션 버튼을 누르면 움직여요.")
            }
            item { Toggle("반복 재생", null, Settings.loopVideo) { Settings.loopVideo = it } }
            item { Toggle("소리 끄고 시작", null, Settings.startMuted) { Settings.startMuted = it } }

            // ─── 잠금 ───
            item { Section("앱 잠금") }
            item {
                Toggle("앱 잠금", "앱을 열 때 지문 또는 패턴으로 잠금을 풀어요", Settings.lockPattern.isNotEmpty()) { on ->
                    if (on) settingPattern = true
                    else ask = Ask("앱 잠금 끄기", "잠금을 끄면 누구나 앱을 열 수 있어요. 끌까요?", "끄기") {
                        Settings.lockPattern = ""
                    }
                }
            }
            if (Settings.lockPattern.isNotEmpty()) {
                item {
                    val act = ctx as? FragmentActivity
                    val bio = act != null && biometricAvailable(act)
                    Toggle(
                        "지문 우선 사용",
                        if (bio) "잠금 화면에서 지문 인식을 먼저 띄워요" else "이 폰에 등록된 지문이 없어요",
                        Settings.lockBiometric,
                    ) { Settings.lockBiometric = it }
                }
                item {
                    Choice(
                        "잠그는 시점", Settings.lockDelaySec,
                        listOf("바로" to 0, "30초 뒤" to 30, "1분 뒤" to 60, "5분 뒤" to 300),
                    ) { Settings.lockDelaySec = it }
                    Hint("앱을 나갔다가 이 시간이 지나서 돌아오면 다시 잠겨요.")
                }
                item {
                    OutlinedButton(
                        onClick = { settingPattern = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    ) { Text("패턴 바꾸기") }
                }
            }

            // ─── 연결 ───
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
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val used = remember(cacheMsg) {
                        (ctx.imageLoader.diskCache?.size ?: 0L) / (1024.0 * 1024 * 1024)
                    }
                    Text("사진 캐시 용량 (지금 %.1fGB 사용 중)".format(used), style = MaterialTheme.typography.bodyMedium)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 10, 30, 50, 100).forEach { gb ->
                            FilterChip(
                                selected = Settings.cacheGb == gb,
                                onClick = { Settings.cacheGb = gb; toast(ctx, "앱을 다시 켜면 적용돼요") },
                                label = { Text("${gb}GB") },
                            )
                        }
                    }
                    Text(
                        "썸네일과 크게 본 사진을 폰에 저장해 두는 공간이에요. 넉넉할수록 다시 받을 일이 없어 빨라요.",
                        color = Color.Gray, style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = {
                        ask = Ask("사진 캐시 비우기", "폰에 저장된 썸네일을 모두 지울까요? 다음에 볼 때 다시 받아와요.", "비우기") {
                            scope.launch {
                                withContext(Dispatchers.IO) { ctx.imageLoader.diskCache?.clear() }
                                ctx.imageLoader.memoryCache?.clear()
                                cacheMsg = "캐시를 비웠어요"
                            }
                        }
                    }) { Text("사진 캐시 비우기") }
                    if (cacheMsg.isNotEmpty()) Text(cacheMsg, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        ask = Ask("로그아웃", "로그아웃할까요? 저장된 비밀번호도 지워져요.", "로그아웃", onLogout)
                    }) { Text("로그아웃", color = MaterialTheme.colorScheme.error) }
                }
            }
            item { Section("앱 업데이트") }
            item {
                val updater = remember { com.kakario.synogallery.update.Updater.get(ctx) }
                val upState by updater.state.collectAsState()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("시놀 갤러리 빌드 #${updater.currentBuild}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (upState is com.kakario.synogallery.update.Updater.State.Checking) "확인하는 중…"
                            else "앱을 켤 때마다 새 버전을 자동으로 확인해요",
                            color = Color.Gray, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = { updater.check(quiet = false) }) { Text("업데이트 확인") }
                }
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

            // ─── 앱 기록 (맨 아래) ───
            item { Section("앱 기록") }
            item {
                Hint("접속·로그인·로그아웃·오류·튕김·업데이트 같은 특이사항이 자동으로 기록돼요. 문제가 생기면 '최근 기록 복사'를 눌러 그대로 붙여 넣어 주세요.")
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { showLog = true }) { Text("기록 보기") }
                    OutlinedButton(onClick = {
                        clip.setText(androidx.compose.ui.text.AnnotatedString(recentLog()))
                        toast(ctx, "최근 기록을 복사했어요")
                    }) { Text("최근 기록 복사") }
                    OutlinedButton(onClick = {
                        ask = Ask("기록 지우기", "앱 기록을 모두 지울까요?", "지우기") {
                            com.kakario.synogallery.AppLog.clear()
                            com.kakario.synogallery.CrashLog.clear()
                            toast(ctx, "기록을 지웠어요")
                        }
                    }) { Text("지우기") }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showLog) {
        val lines = remember { com.kakario.synogallery.AppLog.read().lines().filter { it.isNotBlank() }.takeLast(400).reversed() }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("앱 기록 (최신순)") },
            text = {
                if (lines.isEmpty()) Text("아직 기록이 없어요.")
                else LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(lines.size) { i ->
                        val l = lines[i]
                        val bad = l.contains("[튕김]") || l.contains("실패") || l.contains("[오류]")
                        Text(
                            l, style = MaterialTheme.typography.bodySmall,
                            color = if (bad) MaterialTheme.colorScheme.error else com.kakario.synogallery.Palette.fg,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    clip.setText(androidx.compose.ui.text.AnnotatedString(recentLog()))
                    toast(ctx, "최근 기록을 복사했어요")
                }) { Text("최근 기록 복사") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showLog = false }) { Text("닫기") } },
        )
    }

    ask?.let { a ->
        ConfirmDialog(
            title = a.title, text = a.text, confirm = a.confirm,
            onConfirm = { ask = null; a.action() },
            onDismiss = { ask = null },
        )
    }
}

@Composable
private fun AccentPicker() {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("강조색", style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ACCENTS.forEach { (key, label) ->
                val c = accentColor(key)
                val on = Settings.accent == key
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { Settings.accent = key }) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(c, CircleShape)
                            .border(if (on) 3.dp else 0.dp, if (on) Color(0xFFDADADF) else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** 탭 켜기/끄기 + 순서 바꾸기 */
@Composable
private fun TabManager() {
    val ctx = LocalContext.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("탭 (체크해서 보이기, 화살표로 순서 바꾸기)", style = MaterialTheme.typography.bodyMedium)
        val order = Settings.orderedTabs()
        val hidden = Settings.hiddenTabs.split(',').mapNotNull { it.toIntOrNull() }.toSet()
        order.forEachIndexed { pos, tab ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = tab !in hidden,
                    onCheckedChange = { on ->
                        val newHidden = if (on) hidden - tab else hidden + tab
                        if (newHidden.size >= HOME_TABS.size) {
                            toast(ctx, "탭은 하나 이상 있어야 해요")
                        } else {
                            Settings.hiddenTabs = newHidden.joinToString(",")
                        }
                    },
                )
                Text(HOME_TABS[tab], modifier = Modifier.weight(1f))
                IconButton(enabled = pos > 0, onClick = {
                    val m = order.toMutableList()
                    m[pos] = m[pos - 1]; m[pos - 1] = tab
                    Settings.tabOrder = m.joinToString(",")
                }) { Icon(Icons.Filled.KeyboardArrowUp, "위로") }
                IconButton(enabled = pos < order.lastIndex, onClick = {
                    val m = order.toMutableList()
                    m[pos] = m[pos + 1]; m[pos + 1] = tab
                    Settings.tabOrder = m.joinToString(",")
                }) { Icon(Icons.Filled.KeyboardArrowDown, "아래로") }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp), color = com.kakario.synogallery.Palette.divider)
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
