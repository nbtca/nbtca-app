package space.nbtca.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import space.nbtca.app.core.card.CardClient

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardLoginScreen(onClose: () -> Unit, onLoggedIn: () -> Unit) {
    var view by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var completed by remember { mutableStateOf(false) }
    BackHandler { onClose() }
    DisposableEffect(Unit) { onDispose { CookieManager.getInstance().flush(); view?.destroy(); view = null } }
    Scaffold(topBar = { Column {
        TopAppBar(title = { Text("Card 授权") }, navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "关闭") }
        })
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it); TextButton(onClick = { error = null; view?.reload() }) { Text("重试") } }
    } }) { padding ->
        AndroidView(modifier = Modifier.fillMaxSize().padding(padding), factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                CookieManager.getInstance().setAcceptCookie(true)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(v: WebView?, url: String?, icon: Bitmap?) { loading = true }
                    override fun onPageFinished(v: WebView?, url: String?) {
                        loading = false
                        val uri = Uri.parse(url.orEmpty())
                        if (uri.scheme == "https" && uri.host == "card.nbtca.space" && uri.path == "/e") {
                            v?.evaluateJavascript("!!document.querySelector('nav a.me:not(.signin)')") { result ->
                                if (result == "true" && !completed) {
                                    completed = true
                                    CookieManager.getInstance().flush()
                                    onLoggedIn()
                                }
                            }
                        }
                    }
                    override fun onReceivedError(v: WebView?, request: WebResourceRequest?, failure: WebResourceError?) {
                        if (request?.isForMainFrame == true) { loading = false; error = "授权页面无法连接，请重试" }
                    }
                }
                view = this
                loadUrl(CardClient.ORIGIN + "/login?next=%2Fe")
            }
        })
    }
}
