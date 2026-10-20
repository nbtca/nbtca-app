package space.nbtca.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import space.nbtca.app.core.auth.AuthManager
import space.nbtca.app.core.model.RepairTicket
import space.nbtca.app.core.network.ApiClient
import space.nbtca.app.core.network.PhotoPayload
import space.nbtca.app.core.util.formatChinaDateTime
import space.nbtca.app.ui.components.FrostedCard
import space.nbtca.app.ui.components.OfflineBanner
import space.nbtca.app.ui.components.StatusBadge
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors
import space.nbtca.app.ui.theme.LocalIsDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    initialTicket: RepairTicket,
    apiClient: ApiClient,
    authManager: AuthManager,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth by authManager.state.collectAsState()
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }

    var ticket by remember { mutableStateOf(initialTicket) }
    var isLoading by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCommit by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    fun loadDetail() {
        isLoading = true
        error = null
        scope.launch {
            apiClient.getTicketDetail(ticket.id, ticket).fold(
                onSuccess = { ticket = it },
                onFailure = { error = it.message }
            )
            isLoading = false
        }
    }

    // 进入页面即拉取详情：公开列表不带 phone/qq/logs/photos，必须补一次详情请求。
    androidx.compose.runtime.LaunchedEffect(ticket.id) { loadDetail() }

    fun runAction(block: suspend () -> Result<RepairTicket>, success: String, closeAfter: Boolean = false) {
        isProcessing = true
        scope.launch {
            block().fold(
                onSuccess = { updated ->
                    if (updated.id.isNotEmpty()) ticket = updated
                    isProcessing = false
                    snackbar.showSnackbar(success)
                    loadDetail()
                    onChanged()
                    if (closeAfter) onBack()
                },
                onFailure = {
                    isProcessing = false
                    snackbar.showSnackbar(it.message ?: "操作失败")
                }
            )
        }
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("工单 #${ticket.id}", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { loadDetail() }, enabled = !isLoading) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新工单")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
                )
                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            error?.let { message ->
                item { OfflineBanner(message) }
            }

            item {
                FrostedCard(padding = PaddingValues(18.dp), margin = PaddingValues(0.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ticket.deviceTypeName,
                            modifier = Modifier.weight(1f),
                            color = AppColors.Primary,
                            fontWeight = FontWeight.Bold
                        )
                        StatusBadge(status = ticket.status, label = ticket.statusName)
                    }
                    Text(
                        text = ticket.title,
                        modifier = Modifier.padding(top = 10.dp),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppThemeColors.textPrimary
                    )
                    if (ticket.description.trim() != ticket.title.trim()) {
                        Text(
                            text = ticket.description,
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
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
                    val canViewContact = ticket.canViewContact(auth.profile, auth.isRepairStaff, auth.isRepairAdmin)
                    if (canViewContact) {
                        InfoRow("联系电话", ticket.phone)
                        InfoRow("QQ", ticket.qq)
                        if (ticket.phone.isEmpty() && ticket.qq.isEmpty()) InfoRow("联系方式", "未提供")
                    } else {
                        InfoRow("联系方式", "接单后可见")
                    }
                    InfoRow("设备型号", ticket.deviceType)
                    if (ticket.size.isNotEmpty()) InfoRow("工单大小", ticket.size)
                    ticket.memberName?.takeIf { it.isNotEmpty() }?.let { InfoRow("经办技术员", it) }
                    ticket.closedByName?.takeIf { it.isNotEmpty() }?.let { InfoRow("结单管理员", it) }
                    if (ticket.clientId.isNotEmpty()) InfoRow("报修人编号", ticket.clientId)
                }
            }

            if (ticket.photos.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            "故障照片",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppThemeColors.textPrimary
                        )
                        LazyRow(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ticket.photos) { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(width = 130.dp, height = 100.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            if (auth.isRepairStaff) {
                item {
                    StaffActions(
                        ticket = ticket,
                        isAdmin = auth.isRepairAdmin,
                        isProcessing = isProcessing,
                        onAccept = { runAction({ apiClient.acceptEvent(ticket.id) }, "已成功接单") },
                        onCommit = { showCommit = true },
                        onDrop = { runAction({ apiClient.dropEvent(ticket.id) }, "已放弃该工单") },
                        onClose = { runAction({ apiClient.closeEvent(ticket.id) }, "工单已审核结单") },
                        onReject = { runAction({ apiClient.rejectCommitEvent(ticket.id) }, "已退回重测") }
                    )
                }
            }

            if (!auth.isRepairStaff && ticket.isOpen && auth.isLoggedIn) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.Primary.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEdit = true },
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("修改信息")
                        }
                        OutlinedButton(
                            onClick = { showCancelConfirm = true },
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                        ) {
                            Text("取消预约")
                        }
                    }
                }
            }

            if (!auth.isRepairStaff && ticket.isClosed) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("该工单已全流程闭环归档", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                    }
                }
            }

            item {
                Text(
                    "维修处理进度时间轴",
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppThemeColors.textPrimary
                )
            }

            item {
                FrostedCard(padding = PaddingValues(18.dp), margin = PaddingValues(0.dp)) {
                    if (ticket.timeline.isEmpty()) {
                        Text("暂无进度记录", color = AppThemeColors.textSecondary)
                    } else {
                        ticket.timeline.forEachIndexed { index, log ->
                            TimelineItem(
                                title = log.title,
                                timestamp = formatChinaDateTime(log.timestamp),
                                detail = log.detail,
                                operator = log.operator,
                                isLast = index == ticket.timeline.lastIndex
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCommit) {
        ModalBottomSheet(onDismissRequest = { showCommit = false }) {
            CommitReportSheet(
                ticket = ticket,
                onDismiss = { showCommit = false },
                onSubmit = { content, size, photos ->
                    showCommit = false
                    isProcessing = true
                    scope.launch {
                        if (photos.isNotEmpty()) {
                            val payloads = photos.mapNotNull { uri -> uriToPhotoPayload(context, uri) }
                            if (payloads.isEmpty()) {
                                isProcessing = false
                                snackbar.showSnackbar("无法读取所选照片，请重新选择")
                                return@launch
                            }
                            val upload = apiClient.uploadEventPhotos(ticket.id, payloads)
                            upload.onFailure {
                                isProcessing = false
                                snackbar.showSnackbar(it.message ?: "照片上传失败")
                                return@launch
                            }
                        }
                        apiClient.commitEvent(ticket.id, content, size).fold(
                            onSuccess = {
                                isProcessing = false
                                snackbar.showSnackbar("维修报告已提交，等待审核")
                                loadDetail()
                                onChanged()
                            },
                            onFailure = {
                                isProcessing = false
                                snackbar.showSnackbar(it.message ?: "提交失败")
                            }
                        )
                    }
                }
            )
        }
    }

    if (showEdit) {
        EditTicketDialog(
            ticket = ticket,
            onDismiss = { showEdit = false },
            onSave = { problem, model, phone, qq ->
                showEdit = false
                isProcessing = true
                scope.launch {
                    apiClient.updateClientEvent(ticket.id, problem, model, phone, qq).fold(
                        onSuccess = {
                            ticket = it
                            isProcessing = false
                            snackbar.showSnackbar("报修信息已更新")
                            onChanged()
                        },
                        onFailure = {
                            isProcessing = false
                            snackbar.showSnackbar(it.message ?: "更新失败")
                        }
                    )
                }
            }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("取消维修预约") },
            text = { Text("确定要取消这个维修预约吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    isProcessing = true
                    scope.launch {
                        apiClient.cancelClientEvent(ticket.id).fold(
                            onSuccess = {
                                isProcessing = false
                                onChanged()
                                onBack()
                            },
                            onFailure = {
                                isProcessing = false
                                snackbar.showSnackbar(it.message ?: "取消失败")
                            }
                        )
                    }
                }) { Text("确认取消", color = Color(0xFFFF5252)) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("保留预约") }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isEmpty()) return
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.width(86.dp), fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppThemeColors.textPrimary)
    }
}

@Composable
private fun TimelineItem(
    title: String,
    timestamp: String,
    detail: String,
    operator: String,
    isLast: Boolean
) {
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isLast) AppColors.Primary else Color.Gray)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(56.dp)
                        .background(AppThemeColors.divider)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.textPrimary)
                Text(timestamp, fontSize = 11.sp, color = AppThemeColors.textSecondary)
            }
            Text(
                text = detail.ifEmpty { "无补充说明" },
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 13.sp,
                color = AppThemeColors.textSecondary
            )
            Text(
                text = "经办人: ${operator.ifEmpty { "系统" }}",
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 11.sp,
                color = AppColors.Primary
            )
        }
    }
}

@Composable
private fun StaffActions(
    ticket: RepairTicket,
    isAdmin: Boolean,
    isProcessing: Boolean,
    onAccept: () -> Unit,
    onCommit: () -> Unit,
    onDrop: () -> Unit,
    onClose: () -> Unit,
    onReject: () -> Unit
) {
    val isDark = LocalIsDark.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDark) Color(0xFF1E2633) else Color(0xFFF0F6FF),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Handyman, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isAdmin) "管理员操作控制台" else "技术员工作台",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = AppColors.Primary
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        Spacer(modifier = Modifier.height(12.dp))

        when {
            ticket.isOpen -> Button(
                onClick = onAccept,
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) { Text("接受工单并排期 (接单)", fontWeight = FontWeight.Bold) }

            ticket.isAccepted -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCommit,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
                ) { Text("提交维修报告") }
                OutlinedButton(onClick = onDrop, enabled = !isProcessing) { Text("放弃工单") }
            }

            ticket.isCommitted && isAdmin -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onClose,
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                ) { Text("审核通过结单") }
                OutlinedButton(
                    onClick = onReject,
                    enabled = !isProcessing,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9800))
                ) { Text("退回重测") }
            }

            ticket.isClosed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("该工单已全流程闭环归档", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
            }
        }
    }
}

@Composable
private fun CommitReportSheet(
    ticket: RepairTicket,
    onDismiss: () -> Unit,
    onSubmit: (content: String, size: String, photos: List<Uri>) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var selectedSize by remember { mutableStateOf(ticket.size.takeIf { it.isNotBlank() } ?: "") }
    val photos = remember { mutableStateListOf<Uri>() }
    var errorText by remember { mutableStateOf<String?>(null) }

    val sizes = listOf(
        "xs" to "XS · 极小",
        "s" to "S · 小",
        "m" to "M · 中",
        "l" to "L · 大",
        "xl" to "XL · 极大"
    )

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            if (photos.size + uris.size > 6) {
                errorText = "最多上传 6 张现场照片"
            } else {
                photos.addAll(uris)
                errorText = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("提交维修报告", modifier = Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭") }
        }

        ReportLabel("型号")
        ReadOnlyValue(ticket.deviceType)

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                ReportLabel("联系电话")
                ReadOnlyValue(ticket.phone)
            }
            Column(modifier = Modifier.weight(1f)) {
                ReportLabel("QQ")
                ReadOnlyValue(ticket.qq)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ReportLabel("现场照片", modifier = Modifier.weight(1f))
            TextButton(onClick = { picker.launch("image/*") }) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加照片")
            }
        }
        if (ticket.photos.isEmpty() && photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppThemeColors.surfaceElevated, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text("还没有照片，可添加维修后的现场照片", fontSize = 12.sp, color = AppThemeColors.textSecondary)
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ticket.photos) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.size(width = 96.dp, height = 84.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                items(photos) { uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.size(width = 96.dp, height = 84.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(onClick = { photos.remove(uri) }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "移除", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ReportLabel("维修记录")
        if (ticket.timeline.isEmpty()) {
            Text("暂无维修记录", fontSize = 12.sp, color = AppThemeColors.textSecondary)
        } else {
            ticket.timeline.reversed().take(3).forEach { log ->
                Text(
                    text = "${log.title} · ${formatChinaDateTime(log.timestamp)}${if (log.operator.isEmpty()) "" else " · ${log.operator}"}",
                    modifier = Modifier.padding(bottom = 4.dp),
                    fontSize = 12.sp,
                    color = AppThemeColors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ReportLabel("维修难度")
        sizes.forEach { (value, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedSize == value, onClick = { selectedSize = value })
                Text(label, fontSize = 13.sp, color = AppThemeColors.textPrimary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ReportLabel("维修描述")
        TextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            placeholder = { Text("请输入维修描述") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AppThemeColors.surfaceElevated,
                unfocusedContainerColor = AppThemeColors.surfaceElevated,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(10.dp)
        )

        errorText?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (content.trim().isEmpty()) {
                        errorText = "请输入维修描述"
                        return@Button
                    }
                    if (selectedSize.isEmpty()) {
                        errorText = "请选择维修难度"
                        return@Button
                    }
                    onSubmit(content.trim(), selectedSize, photos.toList())
                },
                modifier = Modifier.weight(1f).height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) { Text("提交") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(46.dp)) { Text("放弃") }
        }
    }
}

@Composable
private fun ReportLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = AppThemeColors.textSecondary
    )
}

@Composable
private fun ReadOnlyValue(value: String) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .background(AppThemeColors.surfaceElevated, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(value.ifEmpty { "—" }, fontSize = 13.sp, color = AppThemeColors.textPrimary)
    }
}

@Composable
private fun EditTicketDialog(
    ticket: RepairTicket,
    onDismiss: () -> Unit,
    onSave: (problem: String, model: String, phone: String, qq: String) -> Unit
) {
    var problem by remember { mutableStateOf(ticket.description) }
    var model by remember { mutableStateOf(ticket.deviceType) }
    var phone by remember { mutableStateOf(ticket.phone) }
    var qq by remember { mutableStateOf(ticket.qq) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改报修信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = problem,
                    onValueChange = { problem = it },
                    label = { Text("问题描述") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                TextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("设备型号") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                TextField(
                    value = phone,
                    onValueChange = { input -> if (input.length <= 11) phone = input.filter { it.isDigit() } },
                    label = { Text("电话号码") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                TextField(
                    value = qq,
                    onValueChange = { qq = it.filter { ch -> ch.isDigit() } },
                    label = { Text("QQ") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (problem.trim().isEmpty()) return@TextButton
                onSave(problem.trim(), model.trim(), phone.trim(), qq.trim())
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 读取相册 Uri 为可上传载荷。Saturday 只接受 jpeg/webp，
 * 其他格式（如 png/HEIC 解码后）统一转成 JPEG，避免服务端 422。
 */
private fun uriToPhotoPayload(context: android.content.Context, uri: Uri): PhotoPayload? {
    return try {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.isEmpty()) return null

        val isDirectlyAccepted = mime == "image/jpeg" || mime == "image/webp"
        if (isDirectlyAccepted) {
            val ext = if (mime == "image/webp") "webp" else "jpg"
            PhotoPayload("repair_${System.nanoTime()}.$ext", bytes, mime)
        } else {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            bitmap.recycle()
            PhotoPayload("repair_${System.nanoTime()}.jpg", out.toByteArray(), "image/jpeg")
        }
    } catch (_: Exception) {
        null
    }
}
