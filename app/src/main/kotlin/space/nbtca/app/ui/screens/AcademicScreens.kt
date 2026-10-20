package space.nbtca.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.nbtca.app.core.academic.AcademicClient
import space.nbtca.app.core.academic.AcademicException
import space.nbtca.app.core.academic.AcademicSnapshot
import space.nbtca.app.core.academic.AcademicStore
import space.nbtca.app.core.academic.TimetableCourse
import space.nbtca.app.ui.components.FrostedCard
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors

private val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AcademicScaffold(
    title: String,
    store: AcademicStore,
    onBack: () -> Unit,
    content: @Composable (AcademicSnapshot?) -> Unit
) {
    var snapshot by remember { mutableStateOf(store.loadSnapshot()) }
    var showSync by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 只做提示，不自动拉取：是否同步由用户点「同步」决定
    fun requestSync() {
        // 同步页本身带登录能力：没会话时就在那里登录，不再拦截
        error = null
        showSync = true
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { requestSync() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "同步教务数据")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = snapshot?.let { "${it.termLabel} · 更新于 ${formatSyncTime(it.updatedAt)}" }
                        ?: "尚未同步教务数据",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = AppThemeColors.textSecondary
                )
                TextButton(onClick = { requestSync() }) { Text("同步") }
            }
            error?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (snapshot == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有本地教务数据", color = AppThemeColors.textSecondary)
                        Button(
                            onClick = { requestSync() },
                            modifier = Modifier.padding(top = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) { Text("立即同步") }
                    }
                }
            } else {
                content(snapshot)
            }
        }
    }

    if (showSync) {
        AcademicSyncScreen(
            store = store,
            onBack = { showSync = false },
            onSynced = {
                snapshot = store.loadSnapshot()
                error = null
                showSync = false
            }
        )
    }
}

private fun formatSyncTime(epoch: Long): String {
    if (epoch <= 0L) return "-"
    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
    return fmt.format(java.util.Date(epoch))
}

@Composable
fun TimetableScreen(
    apiClient: space.nbtca.app.core.network.ApiClient,
    store: AcademicStore,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var weekInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(Unit) { weekInfo = apiClient.getSchoolWeek() }

    AcademicScaffold(title = "课表", store = store, onBack = onBack) { snapshot ->
        val courses = snapshot?.timetable.orEmpty()
        if (courses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("本学期暂无课表数据", color = AppThemeColors.textSecondary)
            }
            return@AcademicScaffold
        }

        val maxWeek = maxOf(courses.maxOfOrNull { it.weeks.maxOrNull() ?: 0 } ?: 0, 1)
        val currentWeek = weekInfo?.first?.coerceIn(1, maxWeek) ?: 1
        val totalWeeks = (weekInfo?.second ?: maxWeek).coerceAtLeast(currentWeek)

        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = (currentWeek - 1).coerceAtLeast(0),
            pageCount = { totalWeeks }
        )
        // 校历是异步取回的；拿到当前周后跳到对应页
        LaunchedEffect(weekInfo) {
            val target = ((weekInfo?.first ?: 1) - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
            if (target != pagerState.currentPage) pagerState.scrollToPage(target)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 周次切换条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                }, enabled = pagerState.currentPage > 0) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "上一周")
                }
                Text(
                    text = "第 ${pagerState.currentPage + 1} 周 / 共 $totalWeeks 周",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Primary
                )
                IconButton(onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + 1).coerceAtMost(totalWeeks - 1)
                        )
                    }
                }, enabled = pagerState.currentPage < totalWeeks - 1) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "下一周")
                }
            }

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val week = page + 1
                val weekCourses = courses.filter { course ->
                    course.weeks.isEmpty() || course.weeks.contains(week)
                }
                if (weekCourses.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("第 $week 周没有课程", color = AppThemeColors.textSecondary)
                    }
                } else {
                    androidx.compose.foundation.layout.BoxWithConstraints(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        TimetableGrid(weekCourses, maxHeight)
                    }
                }
            }
        }
    }
}

/**
 * Excel 风格课表：行为节次、列为星期，课程按节次跨度合并；
 * 只显示有课的星期，列宽均分，长课程名换行完整显示（不用横向滚动）。
 */
@Composable
private fun TimetableGrid(courses: List<TimetableCourse>, availableHeight: androidx.compose.ui.unit.Dp) {
    // 列：工作日最多 5 列；周末有课时合并成「周末」一列
    val weekdays = (1..5).filter { day -> courses.any { it.weekday == day } }
    val hasWeekend = courses.any { it.weekday == 6 || it.weekday == 7 }
    val columns: List<Pair<String, List<Int>>> = buildList {
        weekdays.forEach { add(WEEKDAY_LABELS[it - 1] to listOf(it)) }
        if (hasWeekend) add("周末" to listOf(6, 7))
    }.ifEmpty {
        listOf("周一" to listOf(1), "周二" to listOf(2), "周三" to listOf(3), "周四" to listOf(4), "周五" to listOf(5))
    }

    // 固定显示 1~10 节（各周一致，避免翻周时表格高度跳动）；课程超过 10 节再扩展
    val maxPeriod = maxOf(10, courses.maxOfOrNull { it.endPeriod } ?: 10).coerceIn(1, 14)
    val headerHeight = 26.dp
    // 每小节一个单位高度，课程块按真实节次跨度定位（3 节就是 3 节，不会被拉成 4 节）
    val unit = ((availableHeight - headerHeight) / maxPeriod).coerceAtLeast(16.dp)
    val gridHeight = unit * maxPeriod
    val nameSize = (unit.value * 0.40f).coerceIn(9f, 15f).sp
    val nameLine = (unit.value * 0.46f).coerceIn(11f, 18f).sp
    val lineColor = AppThemeColors.divider

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(34.dp))
            columns.forEach { (label, _) ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f).padding(vertical = 5.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = AppColors.Primary
                )
            }
        }
        HorizontalDivider(color = lineColor, thickness = 0.6.dp)

        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧节次标签：每个小节一个数字
            Box(modifier = Modifier.width(34.dp).height(gridHeight)) {
                (1..maxPeriod).forEach { period ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = unit * (period - 1))
                            .height(unit),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = period.toString(),
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            color = AppThemeColors.textSecondary
                        )
                    }
                }
            }

            columns.forEach { (_, days) ->
                val dayCourses = courses.filter { it.weekday in days }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(gridHeight)
                        .border(0.4.dp, lineColor)
                ) {
                    // 每 2 小节一条横线
                    androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                        val step = unit.toPx() * 2
                        var y = step
                        while (y < size.height) {
                            drawLine(
                                color = lineColor,
                                start = androidx.compose.ui.geometry.Offset(0f, y),
                                end = androidx.compose.ui.geometry.Offset(size.width, y),
                                strokeWidth = 1f
                            )
                            y += step
                        }
                    }
                    dayCourses
                        .groupBy { it.startPeriod to it.endPeriod }
                        .forEach { (range, items) ->
                            val (startPeriod, endPeriod) = range
                            val span = (endPeriod - startPeriod + 1).coerceAtLeast(1)
                            CourseBlock(
                                courses = items,
                                nameSize = nameSize,
                                nameLineHeight = nameLine,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = unit * (startPeriod - 1))
                                    .height(unit * span)
                                    .padding(1.dp)
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun CourseBlock(
    courses: List<TimetableCourse>,
    nameSize: androidx.compose.ui.unit.TextUnit,
    nameLineHeight: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.Primary.copy(alpha = 0.08f))
            .border(0.6.dp, AppColors.Primary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Column {
            courses.forEachIndexed { index, course ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = AppColors.Primary.copy(alpha = 0.2f),
                        thickness = 0.4.dp
                    )
                }
                Text(
                    text = course.name,
                    fontSize = nameSize,
                    lineHeight = nameLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = AppThemeColors.textPrimary
                )
                val meta = listOf(course.location, course.teacher)
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        fontSize = (nameSize.value * 0.72f).sp,
                        lineHeight = (nameLineHeight.value * 0.75f).sp,
                        color = AppThemeColors.textSecondary
                    )
                }
            }
        }
    }
}

/** [1,2,3,5,6] → "1-3,5-6" */
private fun compressWeeks(weeks: List<Int>): String {
    if (weeks.isEmpty()) return ""
    val sorted = weeks.distinct().sorted()
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var prev = start
    for (index in 1 until sorted.size) {
        val week = sorted[index]
        if (week == prev + 1) {
            prev = week
            continue
        }
        parts.add(if (start == prev) "$start" else "$start-$prev")
        start = week
        prev = week
    }
    parts.add(if (start == prev) "$start" else "$start-$prev")
    return parts.joinToString(", ")
}

@Composable
fun ExamsScreen(store: AcademicStore, onBack: () -> Unit) {
    AcademicScaffold(title = "考试查询", store = store, onBack = onBack) { snapshot ->
        val exams = snapshot?.exams.orEmpty()
        if (exams.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("本学期的考试安排为空", color = AppThemeColors.textSecondary)
            }
            return@AcademicScaffold
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(exams) { exam ->
                FrostedCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = exam.courseName,
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppThemeColors.textPrimary
                        )
                        if (exam.examName.isNotEmpty()) {
                            Text(exam.examName, fontSize = 12.sp, color = AppColors.Primary)
                        }
                    }
                    if (exam.timeText.isNotEmpty()) {
                        Text(exam.timeText, modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, color = AppThemeColors.textSecondary)
                    }
                    val extra = listOf(
                        if (exam.location.isNotEmpty()) "地点 ${exam.location}" else "",
                        if (exam.seat.isNotEmpty()) "座位 ${exam.seat}" else "",
                        if (exam.assessment.isNotEmpty()) exam.assessment else ""
                    ).filter { it.isNotEmpty() }
                    if (extra.isNotEmpty()) {
                        Text(extra.joinToString(" · "), modifier = Modifier.padding(top = 2.dp), fontSize = 11.sp, color = AppThemeColors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun GradesScreen(store: AcademicStore, onBack: () -> Unit) {
    AcademicScaffold(title = "成绩查询", store = store, onBack = onBack) { snapshot ->
        val grades = snapshot?.grades.orEmpty()
        if (grades.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("本学期暂无成绩记录", color = AppThemeColors.textSecondary)
            }
            return@AcademicScaffold
        }
        val points = grades.mapNotNull { it.gradePoint.toDoubleOrNull() }
        val credits = grades.mapNotNull { it.credits.toDoubleOrNull() }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                FrostedCard {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("课程数", fontSize = 11.sp, color = AppThemeColors.textSecondary)
                            Text(grades.size.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.textPrimary)
                        }
                        Column {
                            Text("总学分", fontSize = 11.sp, color = AppThemeColors.textSecondary)
                            Text(
                                text = if (credits.isEmpty()) "-" else String.format("%.1f", credits.sum()),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppThemeColors.textPrimary
                            )
                        }
                        Column {
                            Text("平均绩点", fontSize = 11.sp, color = AppThemeColors.textSecondary)
                            Text(
                                text = if (points.isEmpty()) "-" else String.format("%.2f", points.average()),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Primary
                            )
                        }
                    }
                }
            }
            items(grades) { grade ->
                FrostedCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = grade.courseName,
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppThemeColors.textPrimary
                        )
                        Text(
                            text = grade.grade.ifEmpty { "-" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (grade.voided) AppThemeColors.textSecondary else AppColors.Primary
                        )
                    }
                    val meta = listOf(
                        if (grade.credits.isNotEmpty()) "${grade.credits} 学分" else "",
                        if (grade.gradePoint.isNotEmpty()) "绩点 ${grade.gradePoint}" else "",
                        if (grade.nature.isNotEmpty()) grade.nature else "",
                        if (grade.voided) "已作废" else ""
                    ).filter { it.isNotEmpty() }
                    if (meta.isNotEmpty()) {
                        Text(meta.joinToString(" · "), modifier = Modifier.padding(top = 4.dp), fontSize = 11.sp, color = AppThemeColors.textSecondary)
                    }
                }
            }
        }
    }
}