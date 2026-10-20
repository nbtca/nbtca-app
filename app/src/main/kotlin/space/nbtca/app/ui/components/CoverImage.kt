package space.nbtca.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.LocalIsDark

private val dataUriCache = object : LruCache<String, Bitmap>(4) {}

/** 解析 data:image/...;base64,xxx（Coil 2.x 没有 data URI 的 Fetcher，只能自己解码）。 */
fun decodeDataUri(url: String): Bitmap? {
    if (!url.startsWith("data:", ignoreCase = true)) return null
    dataUriCache.get(url)?.let { return it }
    val comma = url.indexOf(',')
    if (comma <= 0) return null
    val meta = url.substring(5, comma)
    val payload = url.substring(comma + 1)
    val bytes = try {
        if (meta.contains("base64", ignoreCase = true)) {
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            java.net.URLDecoder.decode(payload, "UTF-8").toByteArray()
        }
    } catch (_: Exception) {
        return null
    }
    if (bytes.isEmpty()) return null
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    dataUriCache.put(url, bitmap)
    android.util.Log.d("CoilDebug", "DATA-URI decoded ${bitmap.width}x${bitmap.height} (${bytes.size}B)")
    return bitmap
}

/**
 * 文章封面：兼容 http(s)、.svg、data:base64 三种来源；
 * 加载中/失败都退回品牌渐变占位，避免出现空白块。
 */
@Composable
fun CoverImage(
    url: String,
    category: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    labelSize: Int = 20
) {
    val isDark = LocalIsDark.current
    val gradient = if (isDark) {
        listOf(Color(0xFF0F2027), Color(0xFF2C5364))
    } else {
        listOf(Color(0xFFE8F1FA), Color(0xFFBFD9F2))
    }

    if (url.isBlank()) {
        CoverPlaceholder(gradient, category, isDark, labelSize, modifier)
        return
    }

    if (url.startsWith("data:", ignoreCase = true)) {
        val bitmap = remember(url) { decodeDataUri(url) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = category,
                modifier = modifier,
                contentScale = contentScale
            )
        } else {
            CoverPlaceholder(gradient, category, isDark, labelSize, modifier)
        }
        return
    }

    SubcomposeAsyncImage(
        model = url,
        contentDescription = category,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(gradient)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = AppColors.Primary)
            }
        },
        error = {
            CoverPlaceholder(gradient, category, isDark, labelSize, Modifier.fillMaxSize())
        },
        success = { SubcomposeAsyncImageContent() }
    )
}

@Composable
private fun CoverPlaceholder(
    gradient: List<Color>,
    category: String,
    isDark: Boolean,
    labelSize: Int,
    modifier: Modifier
) {
    Box(
        modifier = modifier.background(Brush.linearGradient(gradient))
    ) {
        Icon(
            imageVector = Icons.Default.Newspaper,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            tint = AppColors.Primary.copy(alpha = if (isDark) 0.18f else 0.12f)
        )
        Text(
            text = category.ifEmpty { "NBTCA" },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            fontSize = labelSize.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp,
            color = if (isDark) AppColors.TextPrimaryDark else Color(0xFF0B3A66)
        )
    }
}
