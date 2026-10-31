package com.kakario.synogallery.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.kakario.synogallery.Settings
import com.kakario.synogallery.Syno
import com.kakario.synogallery.ViewerStore

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(index: Int, nav: NavController) {
    val items = ViewerStore.items
    val item = items.getOrNull(index)
    if (item == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(Settings.startMuted) }
    var loop by remember { mutableStateOf(Settings.loopVideo) }
    var controls by remember { mutableStateOf(true) }

    val player = remember {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(Syno.client)))
            .build()
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "재생할 수 없어요.\n(${e.errorCodeName})"
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(Syno.originalUrl(item)))
        player.prepare()
        player.playWhenReady = Settings.autoPlayVideo
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(loop) { player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }

    KeepScreenOn(true)
    SystemBars(controls)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerShowTimeoutMs = 2500
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                        controls = v == android.view.View.VISIBLE
                    })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (controls) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x66000000))
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
        }

        error?.let {
            Text(
                it, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
}
