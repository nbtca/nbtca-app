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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import space.nbtca.app.core.auth.AuthManager
import space.nbtca.app.core.model.DiagnosisResponse
import space.nbtca.app.core.network.ApiClient
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors
import space.nbtca.app.ui.theme.LocalIsDark

/**
 * 预约维修表单：对齐 Flutter create_ticket_page.dart 的 1:1 扁平卡片风。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketScreen(
    apiClient: ApiClient,
    authManager: AuthManager,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val auth by authManager.state.collectAsState()
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }

    var problem by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var qq by remember { mutableStateOf("") }
    val email = auth.profile?.email.orEmpty()

    var isSubmitting by remember { mutableStateOf(false) }
    var isDiagnosing by remember { mutableStateOf(false) }
    var diagnosis by remember { mutableStateOf<DiagnosisResponse?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF5F5F7)
    val subtext = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)

    BackHandler { onBack() }

    // 描述输入 600ms 防抖触发 BFF 排障预检
    LaunchedEffect(problem) {
        if (problem.trim().length > 5) {
            delay(600)
            isDiagnosing = true
            diagnosis = apiClient.diagnoseIssue(model.ifBlank { "laptop" }, problem.trim())
            isDiagnosing = false
        }
    }

    fun submit() {
        if (problem.trim().isEmpty()) {
            errorText = "问题描述不能为空"
            return
        }
        if (phone.trim().length < 11) {
            errorText = "请输入 11 位有效手机号码"
            return
        }
        errorText = null
        isSubmitting = true
        scope.launch {
            apiClient.createTicket(problem.trim(), model.trim(), phone.trim(), qq.trim()).fold(
                onSuccess = {
                    isSubmitting = false
                    snackbar.showSnackbar("工单创建成功")
                    delay(300)
                    onCreated()
                },
                onFailure = {
                    isSubmitting = false
                    errorText = it.message
                }
            )
        }
    }

    Scaffold(
        containerColor = if (isDark) Color.Black else Color.White,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) Color.Black else Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("预约维修", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            Text(
                text = "在你填写表单之后，我们会尽快联系你，确认维修时间和地点。",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = subtext
            )

            Text(
                text = "问题描述",
                modifier = Modifier.padding(top = 28.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            FlatField(
                label = "问题描述",
                required = true,
                value = problem,
                onValueChange = { problem = it },
                placeholder = "告诉我们你遇到的问题",
                bgColor = cardBg,
                labelColor = subtext,
                minLines = 4
            )

            FlatField(
                label = "型号",
                required = false,
                value = model,
                onValueChange = { model = it },
                placeholder = "你的设备型号",
                bgColor = cardBg,
                labelColor = subtext
            )
            Text(
                text = "填写设备型号，帮助我们更快的定位问题",
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                fontSize = 12.sp,
                color = subtext
            )

            if (isDiagnosing) {
                Row(modifier = Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.Primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("AI 正在分析排障建议...", color = AppColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            diagnosis?.let { result ->
                Column(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .background(
                            if (isDark) Color(0xFF1E2633) else Color(0xFFF0F6FF),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI 智能预检与自救建议",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Primary
                        )
                    }
                    if (result.summary.isNotBlank()) {
                        Text(
                            text = result.summary,
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = AppThemeColors.textPrimary
                        )
                    }
                    if (result.possibleCauses.isNotEmpty()) {
                        Text("可能原因", modifier = Modifier.padding(top = 10.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        result.possibleCauses.forEach { item ->
                            Text(
                                text = "• $item",
                                modifier = Modifier.padding(top = 3.dp),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = AppThemeColors.textSecondary
                            )
                        }
                    }
                    if (result.selfHelpSteps.isNotEmpty()) {
                        Text("可先尝试", modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        result.selfHelpSteps.forEach { item ->
                            Text(
                                text = "① $item",
                                modifier = Modifier.padding(top = 3.dp),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = AppThemeColors.textSecondary
                            )
                        }
                    }
                }
            }

            Text(
                text = "联系方式",
                modifier = Modifier.padding(top = 32.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            FlatField(
                label = "邮箱",
                required = true,
                value = email,
                onValueChange = {},
                placeholder = "example@nbtca.space",
                bgColor = cardBg,
                labelColor = subtext,
                readOnly = true,
                keyboardType = KeyboardType.Email
            )
            Text(
                text = "我们会向此邮箱发送维修相关的通知",
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                fontSize = 12.sp,
                color = subtext
            )

            FlatField(
                label = "电话号码",
                required = true,
                value = phone,
                onValueChange = { input -> if (input.length <= 11) phone = input.filter { it.isDigit() } },
                placeholder = "必填",
                bgColor = cardBg,
                labelColor = subtext,
                keyboardType = KeyboardType.Phone
            )

            FlatField(
                label = "QQ",
                required = false,
                value = qq,
                onValueChange = { qq = it.filter { ch -> ch.isDigit() } },
                placeholder = "你的QQ号",
                bgColor = cardBg,
                labelColor = subtext,
                keyboardType = KeyboardType.Number
            )

            errorText?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = { submit() },
                enabled = !isSubmitting,
                modifier = Modifier
                    .padding(top = 36.dp)
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0071E3))
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("提交", fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FlatField(
    label: String,
    required: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    bgColor: Color,
    labelColor: Color,
    readOnly: Boolean = false,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = labelColor)
            if (required) {
                Text(" *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
            }
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            minLines = minLines,
            maxLines = if (minLines > 1) minLines else 1,
            placeholder = {
                Text(placeholder, fontSize = 15.sp, color = labelColor.copy(alpha = 0.7f))
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Next
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    }
}
