package space.nbtca.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.nbtca.app.core.auth.AuthManager
import space.nbtca.app.core.config.AppSettings
import space.nbtca.app.core.network.ApiClient
import space.nbtca.app.ui.screens.NewsroomScreen
import space.nbtca.app.ui.screens.ProfileScreen
import space.nbtca.app.ui.screens.RepairScreen
import space.nbtca.app.ui.screens.ToolsScreen
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors

private data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun MainScreen(
    appSettings: AppSettings,
    authManager: AuthManager,
    apiClient: ApiClient,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf(
        NavItem("拔电关机", Icons.Default.Newspaper),
        NavItem("维修", Icons.Default.BuildCircle),
        NavItem("工具", Icons.Default.CloudDone),
        NavItem("个人中心", Icons.Default.AccountCircle)
    )

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = AppThemeColors.surface,
                tonalElevation = 1.dp
            ) {
                items.forEachIndexed { i, item ->
                    NavigationBarItem(
                        selected = index == i,
                        onClick = { index = i },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 12.sp, fontWeight = if (index == i) FontWeight.SemiBold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Primary,
                            selectedTextColor = AppColors.Primary,
                            unselectedIconColor = AppThemeColors.textSecondary,
                            unselectedTextColor = AppThemeColors.textSecondary,
                            indicatorColor = AppColors.Primary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (index) {
                0 -> NewsroomScreen(apiClient = apiClient)
                1 -> RepairScreen(apiClient = apiClient, authManager = authManager)
                2 -> ToolsScreen(
                    apiClient = apiClient,
                    academicStore = space.nbtca.app.core.academic.AcademicStore.getInstance(
                        androidx.compose.ui.platform.LocalContext.current
                    )
                )
                else -> ProfileScreen(
                    appSettings = appSettings,
                    authManager = authManager,
                    academicStore = space.nbtca.app.core.academic.AcademicStore.getInstance(
                        androidx.compose.ui.platform.LocalContext.current
                    ),
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            }
        }
    }
}
