package space.nbtca.app.core.card

import space.nbtca.app.core.auth.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class CardLoginRequired : Exception("NBTCA 登录状态需要更新，请重新登录")
object CardClient {
    const val ORIGIN = "https://card.nbtca.space"
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun page(auth: AuthManager, path: String, fields: Map<String, String>? = null): Document {
        require(path.startsWith("/") && !path.startsWith("//"))
        val credentials = auth.getCardCredentials()
        return withContext(Dispatchers.IO) {
            var url = ORIGIN + "/native" + path
            var body = fields?.let { values -> FormBody.Builder().apply { values.forEach { (k,v) -> add(k,v) } }.build() }
            repeat(6) {
                val request = Request.Builder().url(url).header("Authorization", "Bearer ${credentials.accessToken}").header("X-Logto-Userinfo", credentials.identityToken).header("Accept", "text/html")
                    .header("Cache-Control", "no-cache")
                body?.let { request.header("Origin", ORIGIN).header("Referer", url).post(it) }
                http.newCall(request.build()).execute().use { response ->
                    if (response.code in listOf(301,302,303,307,308)) {
                        val next = response.header("Location")?.let { response.request.url.resolve(it) }
                            ?: error("Card 返回了无效跳转")
                        if (next.scheme != "https" || next.host != "card.nbtca.space" || !next.encodedPath.startsWith("/native/")) throw CardLoginRequired()
                        if (next.encodedPath == "/forbidden") error("当前 Card 账号没有社员权限")
                        url = next.toString()
                        if (response.code in listOf(301,302,303)) body = null
                    } else {
                        if (response.code == 401) throw CardLoginRequired()
                        if (response.code == 403) error("当前 NBTCA 账号没有 Card 访问权限")
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
