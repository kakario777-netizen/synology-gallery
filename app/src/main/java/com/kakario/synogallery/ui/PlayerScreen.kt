package com.kakario.synogallery.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.kakario.synogallery.Item
import com.kakario.synogallery.Settings
import com.kakario.synogallery.Syno
import com.kakario.synogallery.ViewerStore
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
fun newPlayer(ctx: Context, item: Item): ExoPlayer =
    ExoPlayer.Builder(ctx)
        .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(Syno.client)))
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()
        .apply {
            setMediaItem(MediaItem.fromUri(Syno.originalUrl(item)))
            prepare()
        }

/** 화면만 그려주는 영상 표면 (터치는 Compose가 처리) */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { c ->
            (android.view.LayoutInflater.from(c).inflate(com.kakario.synogallery.R.layout.texture_player, null) as PlayerView).apply {
                useController = false
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

/** 재생 위치를 주기적으로 읽어오는 상태 */
private class PlayState {
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
}

@Composable
private fun rememberPlayState(player: ExoPlayer): PlayState {
    val st = remember { PlayState() }
    LaunchedEffect(player) {
        while (true) {
            st.pos = player.currentPosition
            st.dur = player.duration.takeIf { it > 0 } ?: 0L
            st.playing = player.isPlaying
            st.buffering = player.playbackState == Player.STATE_BUFFERING
            delay(200)
        }
    }
    return st
}

@Composable
private fun SeekBar(player: ExoPlayer, st: PlayState, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = st.dur.coerceAtLeast(1L)
    val value = dragging ?: (st.pos.toFloat() / dur)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(formatDuration(if (dragging != null) (value * dur).toLong() else st.pos), color = Color.White, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { player.seekTo((it * dur).toLong()) }
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color(0x66FFFFFF),
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(formatDuration(st.dur), color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

// ───────────────────────── 전체 화면 플레이어 ─────────────────────────

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(index: Int, nav: NavController) {
    val item = ViewerStore.playItem ?: ViewerStore.items.getOrNull(index)
    if (item == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var error by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(Settings.startMuted) }
    var loop by remember { mutableStateOf(Settings.loopVideo) }
    var controls by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenu by remember { mutableStateOf(false) }
    var rotation by remember { mutableIntStateOf(0) } // 0 자동, 1 가로, 2 세로
    var hint by remember { mutableStateOf<String?>(null) }
    var hintTick by remember { mutableIntStateOf(0) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }

    val player = remember { newPlayer(ctx, item) }
    val st = rememberPlayState(player)

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "재생할 수 없어요.\n(${e.errorCodeName})"
            }
        }
        player.addListener(listener)
        player.playWhenReady = Settings.autoPlayVideo
        onDispose {
            player.removeListener(listener)
            player.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = -1f
                w.attributes = lp
            }
        }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(loop) { player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    LaunchedEffect(speed) { player.setPlaybackSpeed(speed) }
    LaunchedEffect(rotation) {
        activity?.requestedOrientation = when (rotation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // 조작하지 않으면 컨트롤 숨김
    LaunchedEffect(controls, st.playing, speedMenu) {
        if (controls && st.playing && !speedMenu) {
            delay(3000)
            controls = false
        }
    }
    LaunchedEffect(hintTick) {
        if (hint != null) {
            delay(900)
            if (seekPreview == null) hint = null
        }
    }

    fun showHint(t: String) {
        hint = t
        hintTick++
    }

    KeepScreenOn(true)
    SystemBars(controls)
    BackHandler { nav.popBackStack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(player, Modifier.fillMaxSize())

        // 제스처: 한 번 탭 = 컨트롤, 두 번 탭 = 10초 앞/뒤, 상하 = 밝기(왼쪽)/음량(오른쪽), 좌우 = 탐색
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controls = !controls },
                        onDoubleTap = { p ->
                            when {
                                p.x < size.width / 3f -> {
                                    player.seekBack(); showHint("10초 뒤로")
                                }
                                p.x > size.width * 2 / 3f -> {
                                    player.seekForward(); showHint("10초 앞으로")
                                }
                                else -> if (player.isPlaying) player.pause() else player.play()
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        var mode = 0 // 1 좌우 탐색, 2 밝기, 3 음량
                        var total = androidx.compose.ui.geometry.Offset.Zero
                        val startPos = player.currentPosition
                        val startVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                        val w = activity?.window
                        var bright = w?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                        var vol = startVol
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            total += c.position - c.previousPosition
                            if (mode == 0 && total.getDistance() > slop) {
                                mode = if (abs(total.x) > abs(total.y)) 1
                                else if (down.position.x < size.width / 2f) 2 else 3
                            }
                            when (mode) {
                                1 -> {
                                    c.consume()
                                    val dur = player.duration.coerceAtLeast(1L)
                                    // 화면 너비 = 90초
                                    val target = (startPos + (total.x / size.width) * 90_000).toLong().coerceIn(0L, dur)
                                    seekPreview = target
                                    val diff = (target - startPos) / 1000
                                    hint = "${if (diff >= 0) "+" else "-"}${formatDuration(abs(diff) * 1000)}  (${formatDuration(target)})"
                                }
                                2 -> {
                                    c.consume()
                                    val d = -(c.position.y - c.previousPosition.y) / size.height
                                    bright = (bright + d * 1.4f).coerceIn(0.02f, 1f)
                                    w?.let { win ->
                                        val lp = win.attributes
                                        lp.screenBrightness = bright
                                        win.attributes = lp
                                    }
                                    hint = "밝기 ${(bright * 100).roundToInt()}%"
                                }
                                3 -> {
                                    c.consume()
                                    val d = -(c.position.y - c.previousPosition.y) / size.height
                                    vol = (vol + d * maxVol * 1.4f).coerceIn(0f, maxVol.toFloat())
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, vol.roundToInt(), 0)
                                    if (muted && vol > 0f) muted = false
                                    hint = "음량 ${vol.roundToInt()} / $maxVol"
                                }
                            }
                        }
                        seekPreview?.let { player.seekTo(it) }
                        seekPreview = null
                        if (mode != 0) hintTick++
                    }
                },
        )

        // 가운데 안내 (밝기·음량·탐색·10초)
        hint?.let {
            Text(
                it, color = Color.White, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x99000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // 위쪽 바
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x88000000))
                        .statusBarsPadding()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color.White)
                    }
                    Text(
                        item.filename, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                    )
                    Box {
                        TextButton(onClick = { speedMenu = true }) {
                            Text("${if (speed % 1f == 0f) speed.toInt() else speed}x", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f).forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s}배" + if (s == speed) "  ✓" else "") },
                                    onClick = { speed = s; speedMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        rotation = (rotation + 1) % 3
                        showHint(listOf("화면 자동 회전", "가로로 고정", "세로로 고정")[rotation])
                    }) {
                        Icon(Icons.Filled.ScreenRotation, "회전", tint = if (rotation != 0) MaterialTheme.colorScheme.primary else Color.White)
                    }
                    IconButton(onClick = { loop = !loop }) {
                        Icon(
                            if (loop) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "반복",
                            tint = if (loop) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    IconButton(onClick = { muted = !muted }) {
                        Icon(
                            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            "소리", tint = Color.White,
                        )
                    }
                }

                // 가운데 버튼
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton(Icons.Filled.Replay10, "10초 뒤로", 52) { player.seekBack() }
                    RoundButton(if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "재생", 72) {
                        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                        if (player.isPlaying) player.pause() else player.play()
                    }
                    RoundButton(Icons.Filled.Forward10, "10초 앞으로", 52) { player.seekForward() }
                }

                // 아래 탐색 바
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0x88000000))
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    SeekBar(player, st)
                    Text(
                        "두 번 탭: 10초 이동 · 좌우 밀기: 탐색 · 왼쪽 상하: 밝기 · 오른쪽 상하: 음량",
                        color = Color(0xAAFFFFFF), style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        error?.let {
            Text(
                it, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, sizeDp: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(sizeDp.dp)
            .background(Color(0x66000000), CircleShape),
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size((sizeDp * 0.6f).dp))
    }
}

// ───────────────────────── 뷰어 안에서 바로 재생 ─────────────────────────

/**
 * 구글 포토처럼 사진 넘기는 화면에서 바로 재생.
 * 좌우로 넘기면 이 화면이 사라지면서 재생도 멈춤.
 */
@Composable
fun InlineVideo(
    item: Item,
    uiVisible: Boolean,
    onTap: () -> Unit,
    onFullscreen: () -> Unit,
    poster: @Composable () -> Unit = {},
) {
    val ctx = LocalContext.current
    val player = remember(item.id) { newPlayer(ctx, item) }
    val st = rememberPlayState(player)
    var muted by remember { mutableStateOf(Settings.startMuted) }
    var error by remember { mutableStateOf<String?>(null) }
    // 첫 화면이 나오기 전까지는 표지 그림을 그대로 보여줌 (검은 화면 깜빡임 방지)
    var firstFrame by remember(item.id) { mutableStateOf(false) }

    DisposableEffect(item.id) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "재생할 수 없어요 (${e.errorCodeName})"
            }

            override fun onRenderedFirstFrame() {
                firstFrame = true
            }
        }
        player.addListener(listener)
        player.repeatMode = if (Settings.loopVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    KeepScreenOn(true)

    Box(Modifier.fillMaxSize()) {
        if (!firstFrame) poster()
        VideoSurface(player, Modifier.fillMaxSize().graphicsLayer { alpha = if (firstFrame) 1f else 0f })
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(item.id) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { p ->
                            when {
                                p.x < size.width / 3f -> player.seekBack()
                                p.x > size.width * 2 / 3f -> player.seekForward()
                                else -> if (player.isPlaying) player.pause() else player.play()
                            }
                        },
                    )
                }
        )
        if (firstFrame && !st.playing && !st.buffering && error == null) {
            Box(Modifier.align(Alignment.Center)) {
                RoundButton(Icons.Filled.PlayArrow, "재생", 72) {
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                    player.play()
                }
            }
        }
        AnimatedVisibility(
            uiVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x77000000))
                    .navigationBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "재생", tint = Color.White)
                }
                SeekBar(player, st, Modifier.weight(1f))
                IconButton(onClick = { muted = !muted }) {
                    Icon(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "소리", tint = Color.White)
                }
                IconButton(onClick = onFullscreen) {
                    Icon(Icons.Filled.Fullscreen, "크게 보기", tint = Color.White)
                }
            }
        }
        error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp), textAlign = TextAlign.Center)
        }
    }
}


// ───────────────────────── 갤럭시 모션 포토 재생 ─────────────────────────

/** 사진 위에 모션 포토 영상을 한 번 재생하고 끝나면 사라짐 */
@Composable
fun MotionOverlay(file: java.io.File, onEnd: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    var firstFrame by remember(file) { mutableStateOf(false) }
    DisposableEffect(file) {
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrame = true
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnd()
            }

            override fun onPlayerError(error: PlaybackException) = onEnd()
        }
        player.addListener(l)
        onDispose {
            player.removeListener(l)
            player.release()
        }
    }
    VideoSurface(player, Modifier.fillMaxSize().graphicsLayer { alpha = if (firstFrame) 1f else 0f })
}
