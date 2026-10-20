package space.nbtca.app.core.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val version: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val prerelease: Boolean = false
)

enum class UpdateChannel { STABLE, BETA }

/**
 * 从 GitHub Releases 检查更新：
 * · 稳定版读取 /releases/latest
 * · Beta 读取 /releases 列表并取最新的 prerelease（含普通版本）
 */
object UpdateChecker {

    private const val LATEST_API = "https://api.github.com/repos/nbtca/nbtca-app/releases/latest"
    private const val LIST_API = "https://api.github.com/repos/nbtca/nbtca-app/releases?per_page=20"

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(
        currentVersion: String,
        channel: UpdateChannel
    ): Result<ReleaseInfo?> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
        val releases: List<ReleaseInfo> = when (channel) {
            UpdateChannel.STABLE -> {
                val one = parseReleaseObject(json.parseToJsonElement(get(LATEST_API)).jsonObject)
                listOfNotNull(one)
            }
            UpdateChannel.BETA -> {
                val array = json.parseToJsonElement(get(LIST_API)).jsonArray
                array.mapNotNull { item -> (item as? JsonObject)?.let(::parseReleaseObject) }
                    .filter { it.prerelease }   // Beta 只看预发布；没有预发布则视为无更新
            }
        }
            releases.firstOrNull { isNewer(it.version, currentVersion) }
        }.onFailure {
            android.util.Log.e("UpdateChecker", "check failed", it)
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "nbtca-app-android")
            .get()
            .build()
        return http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub 返回 HTTP ${response.code}")
            }
            text
        }
    }

    private fun parseReleaseObject(root: JsonObject): ReleaseInfo? {
        val tag = (root["tag_name"] as? JsonPrimitive)?.contentOrNull ?: return null
        val assets = (root["assets"] as? JsonArray) ?: JsonArray(emptyList())
        val apk = assets.mapNotNull { it as? JsonObject }
            .firstOrNull { asset ->
                (asset["name"] as? JsonPrimitive)?.contentOrNull?.endsWith(".apk", true) == true
            }
            ?.let { asset -> (asset["browser_download_url"] as? JsonPrimitive)?.contentOrNull }
        return ReleaseInfo(
            version = normalize(tag),
            name = (root["name"] as? JsonPrimitive)?.contentOrNull ?: tag,
            notes = (root["body"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            pageUrl = (root["html_url"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            apkUrl = apk,
            prerelease = (root["prerelease"] as? JsonPrimitive)?.contentOrNull?.toBoolean() ?: false
        )
    }

    fun normalize(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

    /** 语义化比较：1.2.3 > 1.2.0；缺省段按 0 处理。 */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = normalize(candidate).split(".", "-").map { it.toIntOrNull() ?: 0 }
        val b = normalize(current).split(".", "-").map { it.toIntOrNull() ?: 0 }
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
