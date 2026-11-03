package space.nbtca.app.core.card

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class CardLoginRequired : Exception("Card 登录会话已失效，请重新授权")
object CardClient {
    const val ORIGIN = "https://card.nbtca.space"
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun page(path: String, fields: Map<String, String>? = null): Document {
        require(path.startsWith("/") && !path.startsWith("//"))
        val cookie = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(ORIGIN).orEmpty() }
        return withContext(Dispatchers.IO) {
            var url = ORIGIN + path
            var body = fields?.let { values -> FormBody.Builder().apply { values.forEach { (k,v) -> add(k,v) } }.build() }
            repeat(6) {
                val request = Request.Builder().url(url).header("Cookie", cookie).header("Accept", "text/html")
                    .header("Cache-Control", "no-cache")
                body?.let { request.header("Origin", ORIGIN).header("Referer", url).post(it) }
                http.newCall(request.build()).execute().use { response ->
                    if (response.code in listOf(301,302,303,307,308)) {
                        val next = response.header("Location")?.let { response.request.url.resolve(it) }
                            ?: error("Card 返回了无效跳转")
                        if (next.scheme != "https" || next.host != "card.nbtca.space" || next.encodedPath.startsWith("/cdn-cgi/") || next.encodedPath == "/login") throw CardLoginRequired()
                        if (next.encodedPath == "/forbidden") error("当前 Card 账号没有社员权限")
                        url = next.toString()
                        if (response.code in listOf(301,302,303)) body = null
                    } else {
                        if (response.code == 401) throw CardLoginRequired()
                        if (response.code == 403) error("当前 Card 账号没有访问权限，或登录需要重新验证")
                        check(response.isSuccessful) { "Card 请求失败（${response.code}）" }
                        val doc = Jsoup.parse(response.body?.string().orEmpty(), url)
                        check(doc.selectFirst("nav .brand") != null) { "Card 返回了验证页面，请重新登录" }
                        return@withContext doc
                    }
                }
            }
            error("Card 跳转次数过多，请重新登录")
        }
    }
}
