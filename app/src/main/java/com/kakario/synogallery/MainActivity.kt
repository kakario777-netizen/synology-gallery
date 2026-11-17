package com.kakario.synogallery

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.async
import androidx.compose.ui.graphics.compositeOver
import androidx.fragment.app.FragmentActivity
import com.kakario.synogallery.ui.AppLock
import com.kakario.synogallery.ui.LockScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kakario.synogallery.ui.AlbumDetailScreen
import com.kakario.synogallery.ui.FolderDetailScreen
import com.kakario.synogallery.ui.HomeScreen
import com.kakario.synogallery.ui.LoginScreen
import com.kakario.synogallery.ui.PlayerScreen
import com.kakario.synogallery.ui.SettingsScreen
import com.kakario.synogallery.ui.TagDetailScreen
import com.kakario.synogallery.ui.ViewerScreen
import com.kakario.synogallery.ui.VideosScreen
import com.kakario.synogallery.ui.PlaceDetailScreen

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱을 켤 때 새 버전 확인 (최신이면 아무것도 안 띄움)
        if (savedInstanceState == null) com.kakario.synogallery.update.Updater.get(this).check(quiet = true)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
        )
        setContent {
            val accent = readableAccent(accentColor(Settings.accent), Palette.dark)
            val bg = Palette.bg
            val fg = Palette.fg
            val dark = Palette.dark
            val view = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.SideEffect {
                val c = androidx.core.view.WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg.toArgb()))
            }
            val tint = accent.copy(alpha = if (dark) 0.28f else 0.14f).compositeOver(bg)
            val base = if (dark) darkColorScheme() else lightColorScheme()
            MaterialTheme(
                colorScheme = base.copy(
                    primary = accent,
                    onPrimary = Color.White,
                    primaryContainer = tint,
                    onPrimaryContainer = if (dark) Color.White else accent,
                    secondary = accent,
                    secondaryContainer = tint,
                    onSecondaryContainer = fg,
                    background = bg,
                    surface = bg,
                    surfaceContainer = Palette.card,
                    surfaceContainerLow = Palette.card,
                    surfaceContainerHigh = Palette.card,
                    surfaceContainerHighest = Palette.placeholder,
                    onBackground = fg,
                    onSurface = fg,
                    onSurfaceVariant = Palette.sub,
                    outline = if (dark) Color(0xFF55555C) else Color(0xFFB8B8BF),
                    outlineVariant = Palette.divider,
                )
            ) {
                Surface(color = bg, contentColor = fg) {
                    Box(Modifier.fillMaxSize()) {
                        AppNav()
                        if (AppLock.locked) LockScreen()
                        else {
                            // 새 버전 알림·받기·설치 창 (앱 어디서나)
                            val updater = remember { com.kakario.synogallery.update.Updater.get(this@MainActivity) }
                            val upState by updater.state.collectAsState()
                            com.kakario.synogallery.ui.UpdateDialog(upState, updater)
                            // 지난번에 튕겼으면 원인을 보여주고 복사할 수 있게
                            var crash by remember { mutableStateOf(CrashLog.unseen()) }
                            crash?.let { text ->
                                val clip = androidx.compose.ui.platform.LocalClipboardManager.current
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { crash = null; CrashLog.markSeen() },
                                    title = { androidx.compose.material3.Text("지난번에 앱이 꺼졌어요") },
                                    text = {
                                        androidx.compose.material3.Text(
                                            "아래 '복사'를 눌러 채팅에 붙여 넣어 주시면 원인을 고칠게요.\n\n" + text.lines().take(8).joinToString("\n"),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    },
                                    confirmButton = {
                                        androidx.compose.material3.TextButton(onClick = {
                                            clip.setText(androidx.compose.ui.text.AnnotatedString(text))
                                            android.widget.Toast.makeText(this@MainActivity, "복사했어요", android.widget.Toast.LENGTH_SHORT).show()
                                            crash = null; CrashLog.markSeen()
                                        }) { androidx.compose.material3.Text("복사") }
                                    },
                                    dismissButton = {
                                        androidx.compose.material3.TextButton(onClick = { crash = null; CrashLog.markSeen() }) {
                                            androidx.compose.material3.Text("닫기")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onStart()
    }

    override fun onStop() {
        super.onStop()
        AppLock.onStop()
    }
}

/** 배경과 너무 비슷한 강조색은 밝기를 조정해서 글자·버튼이 잘 보이게 */
fun readableAccent(c: Color, darkBg: Boolean): Color {
    val l = c.luminance()
    return when {
        darkBg && l < 0.18f -> androidx.compose.ui.graphics.lerp(c, Color.White, 0.72f)
        !darkBg && l > 0.6f -> androidx.compose.ui.graphics.lerp(c, Color.Black, 0.35f)
        else -> c
    }
}

/** 상태바(시계)와 아래 내비게이션 바를 남겨두고 그 사이에만 화면을 그림 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun Framed(content: @Composable () -> Unit) {
    // 사진 보기에서 바를 숨겼다 돌아와도 항상 다시 보이게
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val w = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        androidx.core.view.WindowCompat.getInsetsController(w, view)
            .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            // 바가 잠깐 숨겨져 있어도 그 자리는 늘 비워 둠 → 시계·내비바를 침범하지 않음
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
    ) { content() }
}

/** 강조색 목록 (설정 화면과 같이 씀) */
val ACCENTS = listOf(
    "red" to "빨강", "black" to "검정", "blue" to "파랑", "green" to "초록",
    "orange" to "주황", "purple" to "보라", "pink" to "분홍",
)

fun accentColor(name: String): Color = when (name) {
    "black" -> Color(0xFF222226)
    "blue" -> Color(0xFF1E6FE0)
    "green" -> Color(0xFF239B56)
    "orange" -> Color(0xFFEF6C00)
    "purple" -> Color(0xFF7B4FC4)
    "pink" -> Color(0xFFE0457B)
    else -> Color(0xFFE53935)
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    var start by remember { mutableStateOf<String?>(null) }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val auto = Settings.autoLogin && Settings.host.isNotBlank() && Settings.password.isNotEmpty()
        if (!auto) {
            start = "login"
            return@LaunchedEffect
        }
        // 저장해 둔 사진 목록으로 바로 화면을 띄우고, 로그인은 뒤에서 (지문 인식하는 동안 같이 진행)
        val task = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async { Syno.quickLogin() }
        Syno.loginTask = task
        start = "home"
        try {
            task.await()
        } catch (e: Exception) {
            AppLog.err("시작", "시작 자동 로그인 실패", e)
            val authFail = e is Syno.SynoException && e.code in listOf(400, 401, 402, 403, 404, 105)
            if (authFail) {
                android.widget.Toast.makeText(ctx, e.message ?: "다시 로그인해 주세요", android.widget.Toast.LENGTH_LONG).show()
                try {
                    nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } }
                } catch (_: Exception) {
                    start = "login"
                }
            } else {
                android.widget.Toast.makeText(ctx, "NAS에 연결하지 못했어요. 저장된 목록을 보여줘요.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val s = start
    if (s == null) {
        Box(Modifier.fillMaxSize().background(Palette.bg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(navController = nav, startDestination = s) {
        composable("login") {
            Framed {
                LoginScreen(onDone = {
                    nav.navigate("home") { popUpTo("login") { inclusive = true } }
                })
            }
        }
        composable("home") { Framed { HomeScreen(nav) } }
        composable("album/{id}/{title}") { e ->
            Framed {
                AlbumDetailScreen(
                    id = e.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    title = Uri.decode(e.arguments?.getString("title").orEmpty()),
                    nav = nav,
                )
            }
        }
        composable("videos") { Framed { VideosScreen(nav) } }
        composable("map") { Framed { com.kakario.synogallery.ui.MapScreen(nav) } }
        composable("place/{id}/{title}") { e ->
            Framed {
                PlaceDetailScreen(
                    id = e.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    title = Uri.decode(e.arguments?.getString("title").orEmpty()),
                    nav = nav,
                )
            }
        }
        composable("tag/{id}/{title}") { e ->
            Framed {
                TagDetailScreen(
                    id = e.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    title = Uri.decode(e.arguments?.getString("title").orEmpty()),
                    nav = nav,
                )
            }
        }
        composable("folder/{id}/{title}") { e ->
            Framed {
                FolderDetailScreen(
                    id = e.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    title = Uri.decode(e.arguments?.getString("title").orEmpty()),
                    nav = nav,
                )
            }
        }
        // 사진·영상 보기만 진짜 전체 화면
        composable("viewer/{index}") { e ->
            ViewerScreen(start = e.arguments?.getString("index")?.toIntOrNull() ?: 0, nav = nav)
        }
        composable("player/{index}") { e ->
            PlayerScreen(index = e.arguments?.getString("index")?.toIntOrNull() ?: 0, nav = nav)
        }
        composable("settings") {
            Framed {
                SettingsScreen(nav = nav, onLogout = {
                    Syno.logout()
                    Settings.password = ""
                    nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } }
                })
            }
        }
    }
}
