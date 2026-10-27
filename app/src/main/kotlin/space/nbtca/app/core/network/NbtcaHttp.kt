package space.nbtca.app.core.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 直连上游用的 OkHttp 客户端：
 * · 浏览器 UA（部分图床/网关会按 UA 拒绝请求）
 * · 10MB HTTP 缓存，自动复用 ETag / Last-Modified
 * · 统一超时，避免弱网下页面长时间转圈
 */
object NbtcaHttp {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    fun client(context: Context): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.applicationContext.cacheDir, "nbtca-http"), 10L * 1024 * 1024))
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                .build()
            chain.proceed(request)
        }
        .build()

    /** 读取文本。RSS/ICS/HTML 都声明 UTF-8，这里统一按字节解码，避免平台默认字符集问题。 */
    suspend fun getText(
        client: OkHttpClient,
        url: String,
        accept: String = "*/*"
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val bytes = response.body?.bytes() ?: throw IOException("响应为空")
            bytes.toString(Charsets.UTF_8)
        }
    }
}
