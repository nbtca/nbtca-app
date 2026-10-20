package space.nbtca.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import space.nbtca.app.core.academic.AcademicStore
import space.nbtca.app.ui.theme.AppThemeColors

/**
 * 教务网页登录：只负责让用户完成 CAS 登录（含验证码）并保存会话，
 * 登录成功后立即返回，不自动拉取数据——是否同步由用户在课表页手动点「同步」。
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
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            if (done) return
                            val current = url.orEmpty()
                            // 只更新提示：webvpn 会被反复 302（门户 ↔ authserver），
                            // 自动判定会误伤，因此改由用户点「我已登录完成」确认。
                            status = if (current.contains("authserver")) {
                                "请在下方完成登录（含验证码）"
                            } else if (current.contains("webvpn.nbt.edu.cn")) {
                                "看到「常用站点」门户后，点下方「我已登录完成」"
                            } else {
                                "请使用学号与密码登录"
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                    }
                    loadUrl("https://webvpn.nbt.edu.cn/")
                }
            }
        )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = space.nbtca.app.ui.theme.AppColors.Primary
                )
            ) { Text("我已登录完成") }
        }
    }
}
