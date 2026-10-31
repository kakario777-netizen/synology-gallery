package com.kakario.synogallery

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kakario.synogallery.ui.AlbumDetailScreen
import com.kakario.synogallery.ui.FolderDetailScreen
import com.kakario.synogallery.ui.HomeScreen
import com.kakario.synogallery.ui.LoginScreen
import com.kakario.synogallery.ui.PlayerScreen
import com.kakario.synogallery.ui.SettingsScreen
import com.kakario.synogallery.ui.ViewerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
        )
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1A73E8),
                    secondary = Color(0xFF4A90D9),
                    background = Color.White,
                    surface = Color.White,
                    surfaceContainer = Color.White,
                    onBackground = Color(0xFF1B1B1F),
                    onSurface = Color(0xFF1B1B1F),
                )
            ) {
                Surface(color = Color.White, contentColor = Color(0xFF1B1B1F)) {
                    AppNav()
                }
            }
        }
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    var start by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        start = if (Settings.autoLogin && Settings.host.isNotBlank() && Settings.password.isNotEmpty()) {
            try {
                Syno.login(Settings.host, Settings.account, Settings.password, null)
                "home"
            } catch (e: Exception) {
                "login"
            }
        } else "login"
    }

    val s = start
    if (s == null) {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(navController = nav, startDestination = s) {
        composable("login") {
            LoginScreen(onDone = {
                nav.navigate("home") { popUpTo("login") { inclusive = true } }
            })
        }
        composable("home") { HomeScreen(nav) }
        composable("album/{id}/{title}") { e ->
            AlbumDetailScreen(
                id = e.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                title = Uri.decode(e.arguments?.getString("title").orEmpty()),
                nav = nav,
            )
        }
        composable("folder/{id}/{title}") { e ->
            FolderDetailScreen(
                id = e.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                title = Uri.decode(e.arguments?.getString("title").orEmpty()),
                nav = nav,
            )
        }
        composable("viewer/{index}") { e ->
            ViewerScreen(start = e.arguments?.getString("index")?.toIntOrNull() ?: 0, nav = nav)
        }
        composable("player/{index}") { e ->
            PlayerScreen(index = e.arguments?.getString("index")?.toIntOrNull() ?: 0, nav = nav)
        }
        composable("settings") {
            SettingsScreen(nav = nav, onLogout = {
                Syno.logout()
                Settings.password = ""
                nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } }
            })
        }
    }
}
