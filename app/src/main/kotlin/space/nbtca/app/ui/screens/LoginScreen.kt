package space.nbtca.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import space.nbtca.app.core.auth.AuthManager
import space.nbtca.app.core.auth.LogtoAuth
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors
import java.util.concurrent.TimeUnit

/**
 * 视口高度兜底脚本：仅当 100vh 解析为 0（WebView 缺陷）时才注入固定像素高度，
 * 正常环境探测到 vh > 0 会直接返回，不做任何修改。
 */
private const val VIEWPORT_HEIGHT_FALLBACK = """
(function () {
  try {
    var probe = document.createElement('div');
    probe.style.cssText = 'position:absolute;visibility:hidden;height:100vh';
    document.body.appendChild(probe);
    var vh = probe.getBoundingClientRect().height;
    probe.parentNode.removeChild(probe);
    if (vh > 0) return 'ok';
    var h = window.innerHeight || document.documentElement.clientHeight;
    if (h <= 0) return 'skip';
    var style = document.getElementById('nbtca-viewport-fallback');
    if (!style) {
      style = document.createElement('style');
      style.id = 'nbtca-viewport-fallback';
      document.head.appendChild(style);
    }
    style.textContent =
      'html,body{height:' + h + 'px !important;min-height:' + h + 'px !important}' +
      'div[class*="viewBox"]{position:relative !important;height:' + h + 'px !important;min-height:' + h + 'px !important}' +
      '.logto_page-container,.logto_main-content{min-height:' + h + 'px !important}';
    return 'fixed';
  } catch (e) {
    return 'error';
  }
})()
"""


/**
 * Logto OAuth2 + PKCE 登录：WebView 拦截官网回调地址，取 code 本地换 token。
 * 与 Flutter logto_webview_page.dart 的流程一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    authManager: AuthManager,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val verifier = remember { LogtoAuth.generateCodeVerifier() }
    val state = remember { LogtoAuth.generateState() }
    val authorizeUrl = remember { LogtoAuth.buildAuthorizeUrl(LogtoAuth.generateCodeChallenge(verifier), state) }
    val http = remember {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var exchanging by remember { mutableStateOf(false) }
    // 记录 WebView 实例，退出时显式销毁：否则每次打开登录都会留下一个存活的 WebView 渲染进程。
    val webViewHolder = remember { arrayOfNulls<WebView>(1) }

    BackHandler { onClose() }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder[0]?.let { view ->
                view.stopLoading()
                view.destroy()
            }
            webViewHolder[0] = null
        }
    }

    fun handleCode(code: String) {
        if (exchanging) return
        exchanging = true
        scope.launch {
            val result = LogtoAuth.exchangeCode(http, code, verifier)
            result.fold(
                onSuccess = { tokens ->
                    val login = authManager.loginWithTokens(tokens)
                    exchanging = false
                    login.fold(
                        onSuccess = { onClose() },
                        onFailure = { error = it.message ?: "登录失败" }
                    )
                },
                onFailure = {
                    exchanging = false
                    error = "授权码换取 Token 失败: ${it.message}"
                }
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("登录 NBTCA 账号", fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
                )
                if (isLoading || exchanging) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewHolder[0] = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                // Logto 用 position:absolute;inset:0 撑满视口。个别 WebView
                                // （Android 17 预览镜像的 WebView 155）把 100vh/100dvh 解析成 0，
                                // 整页会塌成空白；探测到这种环境时用 innerHeight 兜底。
                                view?.evaluateJavascript(VIEWPORT_HEIGHT_FALLBACK, null)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val target = request?.url?.toString() ?: return false
                                if (LogtoAuth.isRedirect(target)) {
                                    val code = android.net.Uri.parse(target).getQueryParameter("code")
                                    if (!code.isNullOrEmpty()) {
                                        handleCode(code)
                                    } else {
                                        error = "授权回调缺少 code 参数"
                                    }
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(authorizeUrl)
                    }
                }
            )

            if (exchanging) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
            }

            error?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
