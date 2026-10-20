package space.nbtca.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import space.nbtca.app.core.auth.AuthManager
import space.nbtca.app.core.config.AppSettings
import space.nbtca.app.core.network.ApiClient
import space.nbtca.app.ui.navigation.MainScreen
import space.nbtca.app.ui.theme.NbtcaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appSettings = AppSettings.getInstance(this)
        val authManager = AuthManager.getInstance(this)
        val apiClient = ApiClient(appSettings, authManager)

        setContent {
            // 启动即恢复缓存会话：token 临期会静默刷新，避免每次打开都要重新登录。
            androidx.compose.runtime.LaunchedEffect(Unit) { authManager.bootstrap() }

            val systemDark = isSystemInDarkTheme()
            var darkMode by remember {
                mutableStateOf(
                    when (appSettings.themeMode) {
                        "light" -> false
                        "dark" -> true
                        else -> systemDark
                    }
                )
            }

            NbtcaTheme(darkTheme = darkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        appSettings = appSettings,
                        authManager = authManager,
                        apiClient = apiClient,
                        isDarkTheme = darkMode,
                        onToggleTheme = { enabled ->
                            darkMode = enabled
                            appSettings.themeMode = if (enabled) "dark" else "light"
                        }
                    )
                }
            }
        }
    }
}
