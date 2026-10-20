package space.nbtca.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.nbtca.app.core.model.AssociationCalendar
import space.nbtca.app.core.model.ServiceComponent
import space.nbtca.app.core.model.ServiceIncident
import space.nbtca.app.core.model.ServiceStatus
import space.nbtca.app.core.network.ApiClient
import space.nbtca.app.core.util.formatEpochChina
import space.nbtca.app.core.util.htmlUnescape
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(apiClient: ApiClient, academicStore: space.nbtca.app.core.academic.AcademicStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<ServiceStatus?>(null) }
    var calendar by remember { mutableStateOf<AssociationCalendar?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showCalendar by remember { mutableStateOf(false) }
    var monitorExpanded by remember { mutableStateOf(false) }
    var academicPage by remember { mutableStateOf<String?>(null) }

    fun load() {
        isLoading = true
        scope.launch {
            status = apiClient.getServiceStatus()
            calendar = apiClient.getAssociationCalendar().getOrNull()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    if (showCalendar) {
        AssociationCalendarScreen(
            calendar = calendar,
            apiClient = apiClient,
            onBack = { showCalendar = false }
        )
        return
    }

    when (academicPage) {
        "timetable" -> {
            TimetableScreen(apiClient, academicStore, onBack = { academicPage = null })
            return
        }
        "exams" -> {
            ExamsScreen(academicStore, onBack = { academicPage = null })
            return
        }
        "grades" -> {
            GradesScreen(academicStore, onBack = { academicPage = null })
            return
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("工具箱", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新服务状态")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp)
        ) {
            if (status?.isOffline == true) {
                item {
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                            .fillMaxWidth()
                            .background(AppColors.StatusPending.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = AppColors.StatusPending,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = status?.message ?: "服务状态暂时不可用",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = AppThemeColors.textSecondary
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "校园与协会工具",
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppThemeColors.textPrimary
                )
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolEntry(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Groups,
                        color = Color(0xFF009688),
                        title = "协会活动日历",
                        subtitle = "events.ics 公开活动"
                    ) { showCalendar = true }
                    ToolEntry(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.MenuBook,
                        color = Color(0xFFFF9800),
                        title = "协会文档",
                        subtitle = "docs.nbtca.space"
                    ) { openUrl(context, "https://docs.nbtca.space") }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolEntry(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CalendarMonth,
                        color = Color(0xFF5B6EF5),
                        title = "课表",
                        subtitle = "本学期课程安排"
                    ) { academicPage = "timetable" }
                    ToolEntry(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.EventNote,
                        color = Color(0xFFE91E63),
                        title = "考试查询",
                        subtitle = "考试时间与考场"
                    ) { academicPage = "exams" }
                    ToolEntry(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Assessment,
                        color = Color(0xFF34C759),
                        title = "成绩查询",
                        subtitle = "成绩与绩点"
                    ) { academicPage = "grades" }
                }
            }

            item {
                MonitorCard(
                    status = status,
                    expanded = monitorExpanded,
                    onToggle = { monitorExpanded = !monitorExpanded }
                )
            }
        }
    }
}

/** 服务节点监测：折叠成单张卡片，展开后显示各节点与近期事件。 */
@Composable
private fun MonitorCard(
    status: ServiceStatus?,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val total = status?.totalComponents ?: 0
    val healthy = status?.healthyComponents ?: 0
    val stateColor = stateColor(status?.state ?: "unknown")
    val updated = formatEpochChina(status?.updated ?: 0)
    val arrowAngle by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppThemeColors.surface)
            .clickable { onToggle() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(stateColor.copy(alpha = 0.12f))
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = stateColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("服务节点监测", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.textPrimary)
                Text(
                    text = buildString {
                        if (total > 0) append("$healthy / $total 正常")
                        if (updated.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append("更新于 $updated")
                        }
                        if (isEmpty()) append(status?.message ?: "暂无服务状态数据")
                    },
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 11.sp,
                    color = AppThemeColors.textSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(22.dp).rotate(arrowAngle),
                tint = AppThemeColors.textSecondary
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                val groups = status?.groups.orEmpty()
                groups.forEach { group ->
                    Row(
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (group.infra) AppColors.TextSecondaryLight else AppColors.Primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = htmlUnescape(group.name),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppThemeColors.textSecondary
                        )
                    }
                    group.components.forEach { component ->
                        ComponentRow(component)
                    }
                }

                val incidents = status?.incidents.orEmpty()
                if (incidents.isNotEmpty()) {
                    Text(
                        text = "近期事件",
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppThemeColors.textSecondary
                    )
                    incidents.forEach { incident -> IncidentRow(incident) }
                }
            }
        }
    }
}

@Composable
private fun ComponentRow(component: ServiceComponent) {
    val color = stateColor(component.state)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = htmlUnescape(component.name),
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    color = AppThemeColors.textPrimary
                )
                Text(
                    text = stateLabel(component.state),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }
            if (component.failed.isNotEmpty()) {
                Text(
                    "异常区域：${component.failed.joinToString("、")}",
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 11.sp,
                    color = color
                )
            }
            component.error?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = AppThemeColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun IncidentRow(incident: ServiceIncident) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = incident.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppThemeColors.textPrimary
        )
        Text(
            "影响级别：${incident.impact}",
            fontSize = 11.sp,
            color = AppThemeColors.textSecondary
        )
    }
}

@Composable
private fun ToolEntry(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppThemeColors.surface)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Text(
                text = title,
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppThemeColors.textPrimary
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 3.dp),
                fontSize = 11.sp,
                maxLines = 1,
                color = AppThemeColors.textSecondary
            )
        }
    }
}

private fun stateColor(state: String): Color = when (state.lowercase()) {
    "operational" -> AppColors.StatusCompleted
    "degraded", "partial" -> AppColors.StatusPending
    "major", "down", "offline" -> AppColors.StatusAlert
    else -> AppColors.TextSecondaryLight
}

private fun stateLabel(state: String): String = when (state.lowercase()) {
    "operational" -> "正常"
    "degraded" -> "降级"
    "partial" -> "部分可用"
    "major", "down" -> "故障"
    "offline" -> "离线"
    else -> "未知"
}

private fun overallTitle(status: ServiceStatus?): String = when (status?.state?.lowercase()) {
    "operational" -> "所有核心服务正常运行"
    "degraded", "partial" -> "服务部分降级"
    "major", "down" -> "核心服务存在故障"
    else -> if (status?.isOffline == true) "暂时无法获取服务状态" else "服务状态未知"
}