package space.nbtca.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LaptopChromebook
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.nbtca.app.core.auth.AuthManager
import space.nbtca.app.core.model.AssociationCalendar
import space.nbtca.app.core.model.AssociationEvent
import space.nbtca.app.core.model.RepairTicket
import space.nbtca.app.core.network.ApiClient
import space.nbtca.app.core.util.isRepairDayEvent
import space.nbtca.app.core.util.repairDayTimeText
import space.nbtca.app.core.util.withoutPlaceholders
import space.nbtca.app.ui.components.FrostedCard
import space.nbtca.app.ui.components.StatusBadge
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors
import space.nbtca.app.ui.theme.LocalIsDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairScreen(apiClient: ApiClient, authManager: AuthManager) {
    val scope = rememberCoroutineScope()
    val auth by authManager.state.collectAsState()
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }

    var tickets by remember { mutableStateOf<List<RepairTicket>>(emptyList()) }
    var queueTickets by remember { mutableStateOf<List<RepairTicket>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var statusFilter by remember { mutableStateOf("all") }
    var tabIndex by remember { mutableIntStateOf(0) }
    var repairDayEvent by remember { mutableStateOf<AssociationEvent?>(null) }
    var calendar by remember { mutableStateOf<AssociationCalendar?>(null) }

    var showCreate by remember { mutableStateOf(false) }
    var openedTicket by remember { mutableStateOf<RepairTicket?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var roleMenuOpen by remember { mutableStateOf(false) }
    var showLoginPrompt by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }

    val isStaff = auth.isRepairStaff
    val isAdmin = auth.isRepairAdmin

    fun loadTickets() {
        isLoading = true
        scope.launch {
            try {
                var list: List<RepairTicket> = emptyList()
                var queue: List<RepairTicket> = emptyList()
                when {
                    !auth.isLoggedIn -> Unit
                    isAdmin -> {
                        val publicQueue = apiClient.getPublicTickets(100)
                        queue = publicQueue
                        val memberTickets = runCatching { apiClient.getMemberTickets(100) }.getOrDefault(emptyList())
                        queue = mergeTickets(memberTickets, publicQueue)
                        val pending = queue.filter { it.isOpen }.take(20)
                        if (pending.isNotEmpty()) {
                            val enriched = pending.map { ticket ->
                                apiClient.getTicketDetail(ticket.id, ticket).getOrDefault(ticket)
                            }.associateBy { it.id }
                            queue = queue.map { enriched[it.id] ?: it }
                        }
                        list = queue.filter { !it.isOpen }
                    }
                    isStaff -> {
                        val member = runCatching { apiClient.getMemberTickets(100) }.getOrDefault(emptyList())
                        val public = runCatching { apiClient.getPublicTickets(100) }.getOrDefault(emptyList())
                        list = member.filter { !it.isOpen }
                        queue = public
                    }
                    else -> list = runCatching { apiClient.getClientTickets(100) }.getOrDefault(emptyList())
                }
                tickets = list.sortedWith(NEWEST_FIRST)
                queueTickets = queue.sortedWith(NEWEST_FIRST)
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "加载工单失败")
            } finally {
                isLoading = false
            }
        }
    }

    fun loadCalendar() {
        scope.launch {
            apiClient.getAssociationCalendar().onSuccess { data ->
                calendar = data
                // 只有日历里确实排了维修/义诊场次时才展示维修日卡片。
                repairDayEvent = data.events.withoutPlaceholders().firstOrNull { it.isRepairDayEvent() }
            }
        }
    }

    LaunchedEffect(auth.isLoggedIn, auth.effectiveRole) {
        tabIndex = 0
        statusFilter = "all"
        loadTickets()
        loadCalendar()
    }

    if (showCreate) {
        CreateTicketScreen(
            apiClient = apiClient,
            authManager = authManager,
            onBack = { showCreate = false },
            onCreated = {
                showCreate = false
                loadTickets()
                tabIndex = if (isStaff) 2 else 1
            }
        )
        return
    }

    val opened = openedTicket
    if (opened != null) {
        TicketDetailScreen(
            initialTicket = opened,
            apiClient = apiClient,
            authManager = authManager,
            onBack = { openedTicket = null },
            onChanged = { loadTickets() }
        )
        return
    }

    if (showCalendar) {
        AssociationCalendarScreen(
            calendar = calendar,
            apiClient = apiClient,
            onBack = { showCalendar = false }
        )
        return
    }

    val filtered = when (statusFilter) {
        "open" -> tickets.filter { it.isOpen }
        "ongoing" -> tickets.filter { it.isAccepted || it.isCommitted }
        "completed" -> tickets.filter { it.isClosed }
        else -> tickets
    }

    val openCount = if (isAdmin) queueTickets.count { it.isOpen || it.isCommitted } else queueTickets.count { it.isOpen }

    fun runAction(block: suspend () -> Result<RepairTicket>, success: String) {
        scope.launch {
            block().fold(
                onSuccess = {
                    snackbar.showSnackbar(success)
                    loadTickets()
                },
                onFailure = { snackbar.showSnackbar(it.message ?: "操作失败") }
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("维修", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            if (isStaff) {
                                Spacer(modifier = Modifier.width(8.dp))
                                RoleBadge(text = auth.effectiveRoleName, isAdmin = isAdmin)
                            }
                        }
                    },
                    actions = {
                        if (auth.profile?.isRepairStaff == true) {
                            Box {
                                IconButton(onClick = { roleMenuOpen = true }) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = "切换界面视角")
                                }
                                DropdownMenu(expanded = roleMenuOpen, onDismissRequest = { roleMenuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("自动（最高权限）") },
                                        onClick = { authManager.setViewRole(null); roleMenuOpen = false }
                                    )
                                    if (auth.profile?.isRepairAdmin == true) {
                                        DropdownMenuItem(
                                            text = { Text("管理员视角") },
                                            onClick = { authManager.setViewRole("admin"); roleMenuOpen = false }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("技术员视角") },
                                        onClick = { authManager.setViewRole("member"); roleMenuOpen = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("报修用户视角") },
                                        onClick = { authManager.setViewRole("client"); roleMenuOpen = false }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { loadTickets() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新工单")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
                )
                val tabs = buildList {
                    add("服务概览")
                    add(if (isStaff) "工单总览 (${tickets.size})" else "维修记录 (${tickets.size})")
                    if (isStaff) add(if (isAdmin) "管理台 ($openCount)" else "待处理 ($openCount)")
                }
                TabRow(
                    selectedTabIndex = tabIndex.coerceIn(0, tabs.lastIndex),
                    containerColor = AppThemeColors.surface,
                    contentColor = AppColors.Primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 14.sp,
                                    fontWeight = if (tabIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tabIndex) {
                0 -> OverviewTab(
                    repairDayEvent = repairDayEvent,
                    onBook = {
                        if (auth.isLoggedIn) showCreate = true else showLoginPrompt = true
                    },
                    onOpenCalendar = { showCalendar = true },
                    isDark = isDark
                )
                1 -> RecordsTab(
                    tickets = filtered,
                    allCount = tickets.size,
                    isLoading = isLoading,
                    isStaff = isStaff,
                    isLoggedIn = auth.isLoggedIn,
                    statusFilter = statusFilter,
                    onFilterChange = { statusFilter = it },
                    onNew = {
                        if (auth.isLoggedIn) showCreate = true else showLoginPrompt = true
                    },
                    onOpen = { openedTicket = it },
                    onLoginHint = { showLoginPrompt = true }
                )
                else -> AdminTab(
                    queueTickets = queueTickets,
                    tickets = tickets,
                    isAdmin = isAdmin,
                    onOpen = { openedTicket = it },
                    onAccept = { runAction({ apiClient.acceptEvent(it.id) }, "成功接单") },
                    onClose = { runAction({ apiClient.closeEvent(it.id) }, "工单已审核结单") },
                    onReject = { runAction({ apiClient.rejectCommitEvent(it.id) }, "已退回补充") }
                )
            }
        }
    }

    if (showLoginPrompt) {
        LoginPromptDialog(
            onDismiss = { showLoginPrompt = false },
            onLogin = {
                showLoginPrompt = false
                showLogin = true
            }
        )
    }

    if (showLogin) {
        LoginScreen(
            authManager = authManager,
            onClose = {
                showLogin = false
                loadTickets()
            }
        )
    }
}

@Composable
private fun LoginPromptDialog(onDismiss: () -> Unit, onLogin: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要登录 NBTCA 账号") },
        text = {
            Text(
                "电脑维修服务需登录/注册 Logto 统一身份账号后方可预约，以便技术员确认维修时间并向您的邮箱同步进度。",
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onLogin,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) { Text("去登录 / 注册") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后再说") }
        }
    )
}

@Composable
private fun RoleBadge(text: String, isAdmin: Boolean) {
    val color = if (isAdmin) Color(0xFF673AB7) else AppColors.Primary
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Handyman,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun OverviewTab(
    repairDayEvent: AssociationEvent?,
    onBook: () -> Unit,
    onOpenCalendar: () -> Unit,
    isDark: Boolean
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                        )
                    )
                    .padding(18.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.24f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.VolunteerActivism,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("公益 · 免费服务", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "我们提供免费的电脑维修服务",
                        modifier = Modifier.padding(top = 10.dp),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 27.sp
                    )
                    Text(
                        text = "从清理磁盘到加装硬件再到环境配置，我们都帮你搞定。",
                        modifier = Modifier.padding(top = 6.dp),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    androidx.compose.material3.Button(
                        onClick = onBook,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("立即预约维修", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "服务范围涵盖",
                modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppThemeColors.textPrimary
            )
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ServicePill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CleaningServices,
                    title = "清理磁盘",
                    subtitle = "系统除尘/空间释放",
                    color = Color(0xFFFF9800)
                )
                ServicePill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Memory,
                    title = "加装硬件",
                    subtitle = "内存固态/硅脂重涂",
                    color = AppColors.Primary
                )
                ServicePill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Terminal,
                    title = "环境配置",
                    subtitle = "系统重装/开发部署",
                    color = Color(0xFF009688)
                )
            }
        }

        item {
            FrostedCard(
                margin = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                padding = PaddingValues(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFFFF5252).copy(alpha = 0.12f))
                            .padding(6.dp)
                    ) {
                        Icon(
                            Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("理工维修日", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("协会线下定期定点义诊专场", fontSize = 11.sp, color = AppThemeColors.textSecondary)
                    }
                    OutlinedButton(
                        onClick = onOpenCalendar,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("协会公共日历 →", fontSize = 11.sp, color = AppColors.Primary)
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .background(AppThemeColors.surfaceElevated, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    val hasRepairDay = repairDayEvent != null
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (hasRepairDay) AppColors.Primary else AppThemeColors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasRepairDay) {
                                repairDayTimeText(
                                    repairDayEvent?.start,
                                    repairDayEvent?.end,
                                    repairDayEvent?.allDay == true
                                )
                            } else {
                                "当前暂无维修日安排"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (hasRepairDay) AppThemeColors.textPrimary else AppThemeColors.textSecondary
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(14.dp)) }
    }
}

@Composable
private fun ServicePill(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppThemeColors.surface)
            .padding(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title,
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppThemeColors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                maxLines = 2,
                color = AppThemeColors.textSecondary
            )
        }
    }
}

@Composable
private fun RecordsTab(
    tickets: List<RepairTicket>,
    allCount: Int,
    isLoading: Boolean,
    isStaff: Boolean,
    isLoggedIn: Boolean,
    statusFilter: String,
    onFilterChange: (String) -> Unit,
    onNew: () -> Unit,
    onOpen: (RepairTicket) -> Unit,
    onLoginHint: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterPill("全部 ($allCount)", statusFilter == "all") { onFilterChange("all") }
            }
            if (!isStaff) {
                item {
                    val openCount = tickets.count { it.isOpen }
                    FilterPill("待受理 ($openCount)", statusFilter == "open") { onFilterChange("open") }
                }
            }
            item { FilterPill("维修中", statusFilter == "ongoing") { onFilterChange("ongoing") } }
            item { FilterPill("已完工", statusFilter == "completed") { onFilterChange("completed") } }
            item {
                TextButton(onClick = onNew) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新预约", fontSize = 13.sp)
                }
            }
        }

        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            tickets.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 60.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isLoggedIn) Icons.Default.LaptopChromebook else Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
                Text(
                    text = if (isLoggedIn) "暂无相关维修记录" else "登录后查看你的报修记录",
                    modifier = Modifier.padding(top = 16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppThemeColors.textSecondary
                )
                if (!isLoggedIn) {
                    TextButton(onClick = onLoginHint, modifier = Modifier.padding(top = 8.dp)) {
                        Text("登录 NBTCA 账号")
                    }
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 36.dp)) {
                itemsIndexed(tickets, key = { index, t -> "${t.id}#$index" }) { _, ticket ->
                    TicketCard(ticket = ticket, isStaff = isStaff, onOpen = { onOpen(ticket) })
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) AppColors.Primary else AppThemeColors.surface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, AppThemeColors.divider)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else AppThemeColors.textPrimary
        )
    }
}

@Composable
private fun TicketCard(ticket: RepairTicket, isStaff: Boolean, onOpen: () -> Unit) {
    FrostedCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Primary.copy(alpha = 0.1f))
                    .padding(6.dp)
            ) {
                Icon(
                    Icons.Default.LaptopChromebook,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("#${ticket.id}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            StatusBadge(status = ticket.status, label = ticket.statusName)
        }
        Text(
            text = ticket.title,
            modifier = Modifier.padding(top = 12.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AppThemeColors.textPrimary
        )
        if (ticket.description.trim() != ticket.title.trim()) {
            Text(
                text = ticket.description,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                color = AppThemeColors.textSecondary
            )
        }
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(AppThemeColors.divider)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.LaptopMac,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = AppThemeColors.textSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "型号: ${ticket.deviceType}",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = AppThemeColors.textSecondary
            )
            Text(
                text = "查看详情 →",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Primary
            )
        }
    }
}

@Composable
private fun AdminTab(
    queueTickets: List<RepairTicket>,
    tickets: List<RepairTicket>,
    isAdmin: Boolean,
    onOpen: (RepairTicket) -> Unit,
    onAccept: (RepairTicket) -> Unit,
    onClose: (RepairTicket) -> Unit,
    onReject: (RepairTicket) -> Unit
) {
    val pending = queueTickets.filter { it.isOpen }
    val committed = queueTickets.filter { it.isCommitted }
    val inProgress = if (isAdmin) queueTickets.filter { it.isAccepted } else tickets.filter { it.isAccepted }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp)
    ) {
        item { AdminSectionTitle("待受理工单 (${pending.size})", Color(0xFFFF9800)); Spacer(Modifier.height(8.dp)) }
        if (pending.isEmpty()) {
            item { EmptyHint("暂无待受理工单，所有预约已被处理") }
        } else {
            itemsIndexed(pending, key = { index, t -> "p-${t.id}#$index" }) { _, ticket ->
                AdminTicketCard(ticket, "accept", onOpen, onAccept, onClose, onReject)
            }
        }

        if (isAdmin) {
            item { Spacer(Modifier.height(20.dp)) }
            item { AdminSectionTitle("待审核完工工单 (${committed.size})", Color(0xFF9C27B0)); Spacer(Modifier.height(8.dp)) }
            if (committed.isEmpty()) {
                item { EmptyHint("暂无待审核完工单") }
            } else {
                itemsIndexed(committed, key = { index, t -> "c-${t.id}#$index" }) { _, ticket ->
                    AdminTicketCard(ticket, "audit", onOpen, onAccept, onClose, onReject)
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
        item { AdminSectionTitle("正在检修中 (${inProgress.size})", AppColors.Primary); Spacer(Modifier.height(8.dp)) }
        if (inProgress.isEmpty()) {
            item { EmptyHint("暂无正在检修的工单") }
        } else {
            itemsIndexed(inProgress, key = { index, t -> "i-${t.id}#$index" }) { _, ticket ->
                AdminTicketCard(ticket, "in_progress", onOpen, onAccept, onClose, onReject)
            }
        }
    }
}

@Composable
private fun AdminSectionTitle(title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.textPrimary)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 12.dp),
        fontSize = 13.sp,
        color = AppThemeColors.textSecondary
    )
}

@Composable
private fun AdminTicketCard(
    ticket: RepairTicket,
    actionType: String,
    onOpen: (RepairTicket) -> Unit,
    onAccept: (RepairTicket) -> Unit,
    onClose: (RepairTicket) -> Unit,
    onReject: (RepairTicket) -> Unit
) {
    FrostedCard(onClick = { onOpen(ticket) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = AppColors.Primary.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    "#${ticket.id}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = AppColors.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = ticket.deviceType,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            StatusBadge(status = ticket.status, label = ticket.statusName)
        }
        Text(
            text = ticket.description,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = AppThemeColors.textSecondary
        )
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = AppThemeColors.textSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = when {
                    ticket.phone.isNotEmpty() -> "机主电话: ${ticket.phone}"
                    ticket.qq.isNotEmpty() -> "机主 QQ: ${ticket.qq}"
                    else -> "公开工单未提供联系方式"
                },
                fontSize = 12.sp,
                color = AppThemeColors.textSecondary
            )
        }
        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(AppThemeColors.divider)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            when (actionType) {
                "accept" -> OutlinedButton(onClick = { onAccept(ticket) }) {
                    Icon(Icons.Default.Handyman, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("接受工单 (接单)")
                }
                "audit" -> {
                    TextButton(onClick = { onReject(ticket) }) {
                        Text("退回补充", color = Color(0xFFFF9800))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = { onClose(ticket) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("审核通过结单")
                    }
                }
                else -> TextButton(onClick = { onOpen(ticket) }) {
                    Text("查看与处理进度 →")
                }
            }
        }
    }
}

/** 严格按建立时间倒序；同一秒的记录再按工单序号倒序。 */
private val NEWEST_FIRST = Comparator<RepairTicket> { a, b ->
    val byTime = b.createdEpoch.compareTo(a.createdEpoch)
    if (byTime != 0) byTime else (b.id.toLongOrNull() ?: 0).compareTo(a.id.toLongOrNull() ?: 0)
}

private fun mergeTickets(primary: List<RepairTicket>, secondary: List<RepairTicket>): List<RepairTicket> {
    val map = linkedMapOf<String, RepairTicket>()
    primary.forEach { map[it.id] = it }
    secondary.forEach { if (!map.containsKey(it.id)) map[it.id] = it }
    return map.values.sortedWith(NEWEST_FIRST)
}
