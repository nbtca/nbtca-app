package space.nbtca.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import space.nbtca.app.core.auth.AuthManager
import space.nbtca.app.core.config.AppSettings
import space.nbtca.app.ui.components.FrostedCard
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors
import space.nbtca.app.ui.theme.LocalIsDark

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    appSettings: AppSettings,
    authManager: AuthManager,
    academicStore: space.nbtca.app.core.academic.AcademicStore,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val auth by authManager.state.collectAsState()
    val isDark = LocalIsDark.current
    var showLogin by remember { mutableStateOf(false) }

    var showAboutDialog by remember { mutableStateOf(false) }
    var showAcademicDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<space.nbtca.app.core.update.ReleaseInfo?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var updateDone by remember { mutableStateOf(false) }
    var updateChannel by remember { mutableStateOf(space.nbtca.app.core.update.UpdateChannel.STABLE) }
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.1.0"
    }
    val updateScope = androidx.compose.runtime.rememberCoroutineScope()
    // 学号可能被外部修改，用状态跟踪以便对话框关闭后立即刷新展示
    var academicBound by remember { mutableStateOf(academicStore.studentId) }
    var academicSession by remember { mutableStateOf(!academicStore.cookies.isNullOrBlank()) }
    var showCampusLogin by remember { mutableStateOf(false) }

    if (showLogin) {
        LoginScreen(
            authManager = authManager,
            onClose = { showLogin = false }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("个人中心", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item {
                FrostedCard(
                    margin = PaddingValues(16.dp),
                    padding = PaddingValues(20.dp)
                ) {
                    val profile = auth.profile
                    if (auth.isLoggedIn && profile != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.Primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!profile.avatar.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = profile.avatar,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(40.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppThemeColors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF34C759).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Logto 已授权", color = Color(0xFF34C759), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    text = "账号: ${profile.email}",
                                    modifier = Modifier.padding(top = 4.dp),
                                    fontSize = 13.sp,
                                    color = AppThemeColors.textSecondary
                                )
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val roles = profile.roles.ifEmpty { listOf("认证用户") }
                                    roles.forEach { role ->
                                        Box(
                                            modifier = Modifier
                                                .background(AppColors.Primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = role,
                                                color = AppColors.Primary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                            IconButton(onClick = { authManager.logout() }) {
                                Icon(Icons.Default.Logout, contentDescription = "退出登录", tint = Color.Gray)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(AppThemeColors.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(44.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("未登录 NBTCA 账号", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.textPrimary)
                                Text(
                                    "注册/登录后方可使用预约维修功能",
                                    modifier = Modifier.padding(top = 4.dp),
                                    fontSize = 13.sp,
                                    color = AppThemeColors.textSecondary
                                )
                            }
                            Button(
                                onClick = { showLogin = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                            ) { Text("登录 / 注册") }
                        }
                    }
                }
            }

            item { GroupLabel("首选项与网络") }

            item {
                FrostedCard(margin = PaddingValues(horizontal = 16.dp, vertical = 4.dp), padding = PaddingValues(0.dp)) {
                    SettingRow(
                        icon = Icons.Default.Brightness6,
                        title = "外观深浅主题切换",
                        subtitle = if (isDark) "当前：深色模式" else "当前：浅色模式",
                        trailing = {
                            androidx.compose.material3.Switch(
                                checked = isDarkTheme,
                                onCheckedChange = { onToggleTheme(it) },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppColors.Primary,
                                    checkedBorderColor = AppColors.Primary,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFD1D1D6),
                                    uncheckedBorderColor = Color(0xFFC7C7CC)
                                )
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = AppThemeColors.divider)
                    SettingRow(
                        icon = Icons.Default.SystemUpdate,
                        title = "检查更新",
                        subtitle = "当前版本 $appVersion",
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
                    ) { showUpdateDialog = true }
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = AppThemeColors.divider)
                    SettingRow(
                        icon = Icons.Default.School,
                        title = "教务系统账号",
                        subtitle = buildString {
                            if (academicBound.isNotEmpty()) append("学号: $academicBound")
                            append(if (academicBound.isNotEmpty()) " · " else "")
                            append(if (academicSession) "已登录" else "未登录")
                        },
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
                    ) { showAcademicDialog = true }
                }
            }

            item { GroupLabel("社团开源生态") }

            item {
                FrostedCard(margin = PaddingValues(horizontal = 16.dp, vertical = 4.dp), padding = PaddingValues(0.dp)) {
                    SettingRow(
                        icon = Icons.Default.Code,
                        iconTint = Color(0xFF448AFF),
                        title = "GitHub @nbtca 组织主页",
                        subtitle = "浏览协会开源项目与代码仓库",
                        trailing = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray) }
                    ) { openUrl(context, "https://github.com/nbtca") }
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = AppThemeColors.divider)
                    SettingRow(
                        icon = Icons.Default.Language,
                        iconTint = Color(0xFF009688),
                        title = "拔电关机官方网站 (nbtca.space)",
                        subtitle = "浏览网页端完整版图文与活动展示",
                        trailing = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray) }
                    ) { openUrl(context, "https://nbtca.space") }
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = AppThemeColors.divider)
                    SettingRow(
                        icon = Icons.Default.Info,
                        iconTint = Color(0xFFFFC107),
                        title = "关于 NBTCA",
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
                    ) { showAboutDialog = true }
                }
            }
        }
    }

if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = {
                showUpdateDialog = false
                updateDone = false
                updateError = null
                updateResult = null
            },
            title = { Text("检查更新") },
            text = {
                Column {
                    Text("当前版本 $appVersion", fontSize = 12.sp, color = AppThemeColors.textSecondary)
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = updateChannel == space.nbtca.app.core.update.UpdateChannel.STABLE,
                            onClick = { updateChannel = space.nbtca.app.core.update.UpdateChannel.STABLE },
                            label = { Text("稳定版", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = updateChannel == space.nbtca.app.core.update.UpdateChannel.BETA,
                            onClick = { updateChannel = space.nbtca.app.core.update.UpdateChannel.BETA },
                            label = { Text("Beta", fontSize = 12.sp) }
                        )
                    }
                    when {
                        updateChecking -> Text(
                            "正在检查…",
                            modifier = Modifier.padding(top = 12.dp),
                            fontSize = 13.sp
                        )
                        updateError != null -> Text(
                            "检查失败：${updateError}",
                            modifier = Modifier.padding(top = 12.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        updateDone && updateResult == null -> Text(
                            "已是最新版本",
                            modifier = Modifier.padding(top = 12.dp),
                            fontSize = 13.sp,
                            color = AppThemeColors.textSecondary
                        )
                        updateResult != null -> {
                            val info = updateResult!!
                            Text(
                                "发现新版本 ${info.version}${if (info.prerelease) "（Beta）" else ""}",
                                modifier = Modifier.padding(top = 12.dp),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Primary
                            )
                            Text(
                                text = info.notes.take(600),
                                modifier = Modifier.padding(top = 8.dp),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = AppThemeColors.textSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                val info = updateResult
                if (info != null) {
                    TextButton(onClick = {
                        val target = info.apkUrl ?: info.pageUrl
                        showUpdateDialog = false
                        updateDone = false
                        updateResult = null
                        openUrl(context, target)
                    }) { Text("前往下载") }
                } else {
                    TextButton(onClick = {
                        updateChecking = true
                        updateDone = false
                        updateError = null
                        updateResult = null
                        updateScope.launch {
                            val result = space.nbtca.app.core.update.UpdateChecker.check(appVersion, updateChannel)
                            updateChecking = false
                            updateDone = true
                            result.onSuccess { updateResult = it }
                                .onFailure { updateError = it.message ?: "检查更新失败" }
                        }
                    }) { Text("检查") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    updateDone = false
                    updateError = null
                    updateResult = null
                }) { Text("关闭") }
            }
        )
    }

    if (showAcademicDialog) {
        AlertDialog(
            onDismissRequest = { showAcademicDialog = false },
            title = { Text("教务系统账号") },
            text = {
                Column {
                    Text(
                        "学校登录已启用验证码，请在应用内打开教务网页完成登录；登录后的会话会保存在本机，课表/考试/成绩全部缓存到本地。",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = AppThemeColors.textSecondary
                    )
                    Text(
                        text = if (academicSession) {
                            "教务会话：已登录" + (if (academicBound.isNotBlank()) "（学号 $academicBound）" else "")
                        } else {
                            "教务会话：未登录"
                        },
                        modifier = Modifier.padding(top = 12.dp),
                        fontSize = 12.sp,
                        color = if (academicSession) AppColors.Primary else AppThemeColors.textSecondary
                    )
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        Button(
                            onClick = {
                                showAcademicDialog = false
                                showCampusLogin = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) { Text("教务网页登录") }
                        Spacer(modifier = Modifier.width(10.dp))
                        TextButton(onClick = {
                            academicStore.clearCredentials()
                            academicStore.cookies = null
                            academicBound = ""
                            academicSession = false
                        }) { Text("清除会话", color = Color(0xFFFF5252)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAcademicDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showCampusLogin) {
        CampusLoginScreen(
            store = academicStore,
            onBack = { showCampusLogin = false },
            onLoggedIn = {
                academicSession = true
                academicBound = academicStore.studentId
                showCampusLogin = false
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于 拔电关机 (NBTCA)") },
            text = {
                Column {
                    Text("NBTCA Android App v1.0.0", fontWeight = FontWeight.Bold)
                    Text(
                        "“To be at the intersection of technology and liberal arts.”",
                        modifier = Modifier.padding(top = 8.dp),
                        fontStyle = FontStyle.Italic
                    )
                    Text(
                        "浙大宁波理工学院 计算机协会 官方开源移动客户端。\n整合 Newsroom 资讯、Saturday 维修系统、基础设施监测等服务。",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("关闭") } }
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = AppThemeColors.textSecondary
    )
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = AppColors.Primary,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = AppThemeColors.textPrimary)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = AppThemeColors.textSecondary
                )
            }
        }
        trailing?.invoke()
    }
}


