package space.nbtca.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import space.nbtca.app.core.academic.AcademicStore
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors

/** 教务 WebVPN 入口（仅校园网 / 机构出口可达）。 */
private const val CAMPUS_ENTRY = "https://webvpn.nbt.edu.cn/"

/**
 * 教务网页登录：只负责让用户完成 CAS 登录（含验证码）并保存会话，
 * 登录成功后立即返回，不自动拉取数据——是否同步由用户在课表页手动点「同步」。
 *
 * webvpn 是校园网内网入口，校外或代理环境下会直接连接重置，
 * 因此这里拦截主框架加载错误，给出可操作的中文提示而不是 Chromium 原生报错页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CampusLoginScreen(
    store: AcademicStore,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("请使用学号与密码登录（含验证码）") }
    var done by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun harvestCookies(): Boolean {
        val manager = CookieManager.getInstance()
        val hosts = listOf(
            "https://jwxt-443.webvpn.nbt.edu.cn",
            "https://webvpn.nbt.edu.cn",
            "https://authserver-443.webvpn.nbt.edu.cn"
        )
        val pairs = linkedMapOf<String, String>()
        hosts.forEach { url ->
            manager.getCookie(url)?.split(";")?.forEach { part ->
                val index = part.indexOf('=')
                if (index > 0) {
                    val name = part.substring(0, index).trim()
                    val value = part.substring(index + 1).trim()
                    if (name.isNotEmpty() && value.isNotEmpty()) pairs[name] = value
                }
            }
        }
        if (pairs.isEmpty()) return false
        store.cookies = pairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
        return true
    }

    fun retry() {
        loadError = null
        isLoading = true
        status = "正在重新连接教务门户…"
        val wv = webView
        if (wv != null) wv.loadUrl(CAMPUS_ENTRY) else reloadToken++
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("教务网页登录", fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
                )
                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = AppThemeColors.textSecondary
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val error = loadError
            if (error == null) {
                key(reloadToken) {
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { context ->
                            WebView(context).apply {
                                webView = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        errorDescription: WebResourceError?
                                    ) {
                                        // 只处理主框架错误，忽略图片/脚本等子资源失败
                                        if (request?.isForMainFrame == true) {
                                            isLoading = false
                                            loadError = errorDescription?.description?.toString()
                                                ?: "无法连接教务服务器"
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        if (loadError != null || done) return
                                        val current = url.orEmpty()
                                        // 只更新提示：webvpn 会被反复 302（门户 ↔ authserver），
                                        // 自动判定会误伤，因此改由用户点「我已登录完成」确认。
                                        status = when {
                                            current.contains("authserver") -> "请在下方完成登录（含验证码）"
                                            current.contains("webvpn.nbt.edu.cn") -> "看到「常用站点」门户后，点下方「我已登录完成」"
                                            else -> "请使用学号与密码登录"
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean = false
                                }
                                loadUrl(CAMPUS_ENTRY)
                            }
                        }
                    )
                }
            } else {
                // 校园网入口不可达：给出可操作指引，而不是暴露 Chromium 报错页
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AppThemeColors.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = AppColors.StatusAlert,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "无法连接教务门户",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppThemeColors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "webvpn.nbt.edu.cn 是校园网内网入口，只有在校园网 Wi-Fi / 有线网络或学校 VPN 下才能访问。",
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = AppThemeColors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "错误详情：$error",
                                fontSize = 11.sp,
                                color = AppThemeColors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { retry() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("重试连接")
                            }
                            OutlinedButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text("稍后再说", color = AppThemeColors.textSecondary)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "提示：不在校内时可先跳过教务同步，其余功能不受影响。",
                                    fontSize = 11.sp,
                                    color = AppThemeColors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (harvestCookies()) {
                        done = true
                        status = "登录成功"
                        onLoggedIn()
                    } else {
                        status = "还没检测到登录会话，请在网页里完成登录后再点一次"
                    }
                },
                enabled = loadError == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) { Text("我已登录完成") }
        }
    }
}
