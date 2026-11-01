package com.kakario.synogallery.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kakario.synogallery.Settings
import kotlinx.coroutines.delay
import java.security.MessageDigest

/** 앱 잠금 상태 */
object AppLock {
    var locked by mutableStateOf(false)
    private var stoppedAt = 0L
    private var started = false

    val enabled get() = Settings.lockPattern.isNotEmpty()

    fun hash(p: List<Int>): String =
        MessageDigest.getInstance("SHA-256").digest(("syno-" + p.joinToString("-")).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun check(p: List<Int>) = enabled && hash(p) == Settings.lockPattern

    fun onStart() {
        if (!enabled) return
        if (!started) {
            started = true
            locked = true
            return
        }
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt >= Settings.lockDelaySec * 1000L) locked = true
        stoppedAt = 0L
    }

    fun onStop() {
        if (enabled && !locked) stoppedAt = SystemClock.elapsedRealtime()
    }

    /** 설정에서 방금 잠금을 켰을 때: 지금 이미 열려 있는 상태로 시작 */
    fun armed() {
        started = true
        stoppedAt = 0L
    }

    fun unlock() {
        locked = false
        stoppedAt = 0L
    }
}

fun biometricAvailable(activity: FragmentActivity): Boolean =
    BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS

fun showBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("시놀 갤러리 잠금 해제")
        .setSubtitle("지문으로 열기")
        .setNegativeButtonText("패턴 사용")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}

@Composable
fun LockScreen() {
    val activity = LocalContext.current as? FragmentActivity
    var message by remember { mutableStateOf("패턴을 그려 주세요") }
    var error by remember { mutableStateOf(false) }
    val canBio = activity != null && Settings.lockBiometric && biometricAvailable(activity)

    BackHandler { activity?.finish() }

    LaunchedEffect(Unit) {
        if (canBio) {
            delay(250)
            showBiometric(activity!!) { AppLock.unlock() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(com.kakario.synogallery.Palette.bg)
            .pointerInput(Unit) { detectTapGestures { } } // 아래 화면으로 터치가 새지 않게
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text(
            message,
            color = if (error) MaterialTheme.colorScheme.error else com.kakario.synogallery.Palette.fg,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
        )
        PatternPad(
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            onStart = { error = false; message = "패턴을 그려 주세요" },
        ) { p ->
            if (AppLock.check(p)) {
                AppLock.unlock()
            } else {
                error = true
                message = "패턴이 맞지 않아요"
            }
        }
        Spacer(Modifier.height(24.dp))
        if (canBio) {
            TextButton(onClick = { showBiometric(activity!!) { AppLock.unlock() } }) {
                Icon(Icons.Filled.Fingerprint, null)
                Text("지문으로 열기", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** 패턴 새로 만들기 (두 번 그려서 확인) */
@Composable
fun PatternSetup(onDone: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf<List<Int>?>(null) }
    var message by remember { mutableStateOf("새 패턴을 그려 주세요 (4개 점 이상)") }
    var error by remember { mutableStateOf(false) }

    BackHandler { onCancel() }

    Column(
        Modifier
            .fillMaxSize()
            .background(com.kakario.synogallery.Palette.bg)
            .pointerInput(Unit) { detectTapGestures { } } // 아래 화면으로 터치가 새지 않게
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text(
            message,
            color = if (error) MaterialTheme.colorScheme.error else com.kakario.synogallery.Palette.fg,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
        )
        PatternPad(
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            onStart = { error = false },
        ) { p ->
            val f = first
            when {
                p.size < 4 -> {
                    error = true; message = "점을 4개 이상 이어 주세요"
                }
                f == null -> {
                    first = p; message = "확인을 위해 한 번 더 그려 주세요"
                }
                f == p -> onDone(AppLock.hash(p))
                else -> {
                    first = null; error = true; message = "두 패턴이 달라요. 처음부터 다시 그려 주세요"
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) { Text("취소") }
    }
}

/** 3×3 패턴 입력판 */
@Composable
fun PatternPad(modifier: Modifier = Modifier, onStart: () -> Unit = {}, onComplete: (List<Int>) -> Unit) {
    val path = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val accent = MaterialTheme.colorScheme.primary

    Box(modifier.aspectRatio(1f)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val cell = size.width / 3f
                    val hitR = cell * 0.32f
                    fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
                    fun hit(p: Offset) {
                        for (i in 0 until 9) {
                            if (i !in path && (p - center(i)).getDistance() < hitR) {
                                // 사이에 건너뛴 점이 있으면 자동으로 넣음 (일반 안드로이드 패턴과 같게)
                                path.lastOrNull()?.let { last ->
                                    val mid = (last + i) / 2
                                    if ((last + i) % 2 == 0 && mid != last && mid !in path &&
                                        (last % 3 + i % 3) % 2 == 0 && (last / 3 + i / 3) % 2 == 0
                                    ) path += mid
                                }
                                path += i
                            }
                        }
                    }
                    detectDragGestures(
                        onDragStart = { p ->
                            path.clear(); onStart(); finger = p; hit(p)
                        },
                        onDragEnd = {
                            finger = null
                            val result = path.toList()
                            if (result.isNotEmpty()) onComplete(result)
                            path.clear()
                        },
                        onDragCancel = { finger = null; path.clear() },
                    ) { change, _ ->
                        change.consume()
                        finger = change.position
                        hit(change.position)
                    }
                }
        ) {
            val cell = size.width / 3f
            fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
            for (k in 1 until path.size) {
                drawLine(accent, center(path[k - 1]), center(path[k]), strokeWidth = 10f, cap = StrokeCap.Round)
            }
            val f = finger
            if (f != null && path.isNotEmpty()) {
                drawLine(accent.copy(alpha = 0.5f), center(path.last()), f, strokeWidth = 10f, cap = StrokeCap.Round)
            }
            for (i in 0 until 9) {
                val on = i in path
                drawCircle(if (on) accent else Color(0xFFD0D0D6), radius = if (on) cell * 0.11f else cell * 0.07f, center = center(i))
                if (on) drawCircle(accent.copy(alpha = 0.18f), radius = cell * 0.22f, center = center(i))
            }
        }
    }
}
