package space.nbtca.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import space.nbtca.app.core.academic.AcademicStore
import space.nbtca.app.core.academic.AcademicWebSync
import space.nbtca.app.core.academic.TermCatalog
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors
import kotlin.coroutines.resume

/**
 * 教务同步：登录与取数在同一个 WebView 内完成。
 * webvpn 会话绑在 WebView 上，另开隐藏 WebView 会丢会话，所以必须复用这一页。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AcademicSyncScreen(
    store: AcademicStore,
    onBack: () -> Unit,
    onSynced: () -> Unit
) {
    var status by remember { mutableStateOf("正在连接 webvpn…") }
    var stage by remember { mutableStateOf(Stage.LOGIN) }
    var loading by remember { mutableStateOf(true) }
    var catalog by remember { mutableStateOf<TermCatalog?>(null) }
    var selectedYear by remember { mutableStateOf("") }
    var selectedSemester by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ssoRetries by remember { mutableStateOf(0) }

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("教务同步", fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
                )
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = AppThemeColors.textSecondary
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                val current = url.orEmpty()
                                android.util.Log.d("AcademicSync", "page[$stage]: $current")
                                if (current.contains("authserver")) {
                                    if (stage == Stage.LOGIN) {
                                        status = "请在此登录（含验证码），登录后会自动继续"
                                    }
                                    return
                                }
                                when (stage) {
                                    Stage.LOGIN -> {
                                        if (!current.contains("webvpn.nbt.edu.cn")) return
                                        // 用页面内容判断是否真的登录成功：门户有「常用站点」，
                                        // 且不再有登录表单。只看 URL 会被 webvpn 的 302 骗到。
                                        view?.evaluateJavascript(
                                            "(function(){var t=document.body?document.body.innerText:'';" +
                                                "return (t.indexOf('常用站点')>=0 && !document.getElementById('pwdEncryptSalt'))?'yes':'no';})()"
                                        ) { value ->
                                            if (value != null && value.contains("yes")) {
                                                stage = Stage.SSO
                                                status = "已登录，正在进入教务系统…"
                                                // 等 webvpn 门户把会话脚本跑完再跳 SSO，否则 jwxt 仍是未登录
                                                view.postDelayed({ view.loadUrl(SSO_URL) }, 1500)
                                            } else if (value != null) {
                                                status = "请在此登录（含验证码），登录后会自动继续"
                                            }
                                        }
                                    }
                                    Stage.SSO -> {
                                        if (!current.contains("jwxt-443.webvpn.nbt.edu.cn")) return
                                        stage = Stage.TERMS
                                        status = "正在读取学期列表…"
                                        // 不再跳转课表页，直接在当前教务页面里用 fetch 读学期，
                                        // 少一次跳转就少一次被弹回登录页的机会。
                                        view?.evaluateJavascript(AcademicWebSync.TERMS_SCRIPT, null)
                                    }
                                    Stage.TERMS -> {
                                        if (!current.contains("jwxt-443.webvpn.nbt.edu.cn")) return
                                        view?.evaluateJavascript(AcademicWebSync.TERMS_SCRIPT, null)
                                    }
                                    else -> Unit
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                        }
                        webView = this
                        loadUrl("https://webvpn.nbt.edu.cn/")
                    }
                }
            )

            if (stage != Stage.LOGIN) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (error == null && stage != Stage.PICK_TERM) {
                            CircularProgressIndicator(color = AppColors.Primary)
                        }
                        Text(
                            text = error ?: status,
                            modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp),
                            fontSize = 13.sp,
                            color = if (error != null) MaterialTheme.colorScheme.error else AppThemeColors.textSecondary
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(stage) {
        val view = webView
        when (stage) {
            Stage.TERMS -> {
                if (view == null) return@LaunchedEffect
                repeat(40) { attempt ->
                    delay(400)
                    val probe = evaluateJsValue(
                        view,
                        "(location.href+'|xnm='+!!document.getElementById('xnm')+'|terms='+(window.__nbtTerms?1:0))"
                    )
                    if (attempt < 3) android.util.Log.d("AcademicSync", "probe: $probe")
                    val raw = evaluateJsValue(
                        view,
                        "(window.__nbtTerms || (function(){try{return localStorage.getItem('nbt_terms')}catch(e){return null}})())"
                    )
                    if (attempt == 0) android.util.Log.d("AcademicSync", "terms raw: ${raw?.take(200)}")
                    if (!raw.isNullOrBlank() && raw != "null") {
                        runCatching { AcademicWebSync.parseTerms(raw) }
                            .onSuccess { parsed ->
                                catalog = parsed
                                selectedYear = parsed.selectedYear
                                selectedSemester = parsed.selectedSemester
                                status = "请选择要查看的学期"
                                stage = Stage.PICK_TERM
                            }
                            .onFailure {
                                if (ssoRetries < 1) {
                                    ssoRetries += 1
                                    status = "重新尝试进入教务系统…"
                                    stage = Stage.SSO
                                    webView?.loadUrl(SSO_URL)
                                } else {
                                    error = "读取学期失败：${it.message}"
                                }
                            }
                        return@LaunchedEffect
                    }
                }
                if (error == null) error = "读取学期超时，请重试"
            }
            Stage.FETCHING -> {
                if (view == null) return@LaunchedEffect
                repeat(120) {
                    delay(400)
                    val raw = evaluateJsValue(
                        view,
                        "(window.__nbtSync || (function(){try{return localStorage.getItem('nbt_sync')}catch(e){return null}})())"
                    )
                    if (!raw.isNullOrBlank() && raw != "null") {
                        val label = buildString {
                            catalog?.years?.firstOrNull { it.value == selectedYear }?.label?.let { append(it) }
                            catalog?.semesters?.firstOrNull { it.value == selectedSemester }?.label?.let {
                                if (isNotEmpty()) append(" · ")
                                append(it)
                            }
                        }
                        runCatching { AcademicWebSync.parse(store, raw, label) }
                            .onSuccess { onSynced() }
                            .onFailure { error = "同步失败：${it.message}" }
                        return@LaunchedEffect
                    }
                }
                if (error == null) error = "同步超时，请重试"
            }
            else -> Unit
        }
    }

    catalog?.let { current ->
        if (stage == Stage.PICK_TERM) {
            AlertDialog(
                onDismissRequest = { onBack() },
                title = { Text("选择学期") },
                text = {
                    // 学年可能十几个，横排滚动；学期只有 1/2/3，单独一行，避免被挤出对话框
                    Column(
                        modifier = Modifier.verticalScroll(
                            androidx.compose.foundation.rememberScrollState()
                        )
                    ) {
                        Text("学年", fontSize = 12.sp, color = AppThemeColors.textSecondary)
                        val yearListState = androidx.compose.foundation.lazy.rememberLazyListState(
                            initialFirstVisibleItemIndex = current.years
                                .indexOfFirst { it.value == selectedYear }
                                .coerceAtLeast(0)
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            state = yearListState,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            items(current.years.size) { index ->
                                val option = current.years[index]
                                FilterChip(
                                    selected = option.value == selectedYear,
                                    onClick = { selectedYear = option.value },
                                    label = { Text(option.label, fontSize = 12.sp) }
                                )
                            }
                        }
                        Text(
                            "学期",
                            modifier = Modifier.padding(top = 10.dp),
                            fontSize = 12.sp,
                            color = AppThemeColors.textSecondary
                        )
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            current.semesters.forEach { option ->
                                FilterChip(
                                    selected = option.value == selectedSemester,
                                    onClick = { selectedSemester = option.value },
                                    label = { Text(option.label, fontSize = 12.sp) }
                                )
                            }
                        }
                        Text(
                            text = "${current.years.firstOrNull { it.value == selectedYear }?.label ?: "-"} · " +
                                "${current.semesters.firstOrNull { it.value == selectedSemester }?.label ?: "-"}",
                            modifier = Modifier.padding(top = 6.dp),
                            fontSize = 12.sp,
                            color = AppColors.Primary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        error = null
                        status = "正在读取课表、考试与成绩…"
                        stage = Stage.FETCHING
                        webView?.evaluateJavascript(
                            AcademicWebSync.dataScript(selectedYear, selectedSemester),
                            null
                        )
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = onBack) { Text("取消") } }
            )
        }
    }
}

private enum class Stage { LOGIN, SSO, TERMS, PICK_TERM, FETCHING }

private const val SSO_URL = "https://jwxt-443.webvpn.nbt.edu.cn/sso/jziotlogin"
private const val TT_INDEX_URL =
    "https://jwxt-443.webvpn.nbt.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"

private suspend fun evaluateJsValue(view: WebView, script: String): String? =
    suspendCancellableCoroutine { continuation ->
        try {
            view.evaluateJavascript(script) { value ->
                if (continuation.isActive) continuation.resume(value)
            }
        } catch (_: Exception) {
            if (continuation.isActive) continuation.resume(null)
        }
    }
