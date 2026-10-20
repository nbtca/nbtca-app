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

    BackHandler { onClose() }

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
