package space.nbtca.app.core.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * 封面落盘。
 *
 * nbtca.space 的部分文章把 og:image 直接内联成 base64 data URI（单篇约 80KB），
 * 而 Coil 2.x 不支持 data: 协议，直接塞给列表会显示空白。
 * 这里把 data URI 解码后写入 filesDir，返回 file:// 路径交给 Coil 的 FileFetcher；
 * 普通 http(s) 封面原样返回，不复制流量。
 */
class CoverStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "nbtca-covers").apply { mkdirs() }

    /** data URI 落盘为图片文件；普通 URL 原样返回。失败返回空串。 */
    fun materialize(articleId: String, raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        if (!value.startsWith("data:", ignoreCase = true)) return value

        val comma = value.indexOf(',')
        if (comma <= 5) return ""
        val meta = value.substring(5, comma)
        if (!meta.contains("base64", ignoreCase = true)) return ""

        val bytes = runCatching {
            Base64.getDecoder().decode(value.substring(comma + 1).trim())
        }.getOrNull() ?: return ""
        if (bytes.isEmpty()) return ""

        return runCatching {
            val target = File(root, hash(articleId) + ".img")
            if (!target.isFile || target.length() != bytes.size.toLong()) {
                val temp = File(root, target.name + ".tmp")
                temp.writeBytes(bytes)
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
            "file://" + target.absolutePath
        }.getOrDefault("")
    }

    /** 缓存里的封面地址是否仍可用：file:// 需要文件还在。 */
    fun isUsable(value: String): Boolean {
        if (value.isBlank()) return false
        if (!value.startsWith("file://", ignoreCase = true)) return true
        return File(value.removePrefix("file://")).isFile
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
