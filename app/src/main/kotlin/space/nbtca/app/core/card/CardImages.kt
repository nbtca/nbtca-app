package space.nbtca.app.core.card

import android.content.Context
import space.nbtca.app.core.auth.AuthManager
import kotlinx.coroutines.runBlocking
import coil.ImageLoader
import coil.disk.DiskCache
import coil.decode.SvgDecoder
import coil.memory.MemoryCache
import coil.request.ImageRequest
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest

object CardImages {
    @Volatile private var shared: ImageLoader? = null
    fun loader(context: Context): ImageLoader = shared ?: synchronized(this) {
        shared ?: ImageLoader.Builder(context.applicationContext)
            .components { add(SvgDecoder.Factory()) }
            .memoryCache { MemoryCache.Builder(context.applicationContext).maxSizePercent(.08).build() }
            .diskCache { DiskCache.Builder().directory(File(context.applicationContext.cacheDir,"card-images")).maxSizeBytes(64L*1024*1024).build() }
            .okHttpClient(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).addInterceptor { chain ->
                val original = chain.request()
                if (original.url.host != "card.nbtca.space" || original.url.scheme != "https" || !original.url.encodedPath.startsWith("/native/media/members/")) throw java.io.IOException("不支持的 Card 图片地址")
                val credentials = try { runBlocking { AuthManager.getInstance(context.applicationContext).getCardCredentials() } }
                    catch (_: Exception) { throw java.io.IOException("图片身份验证失败") }
                val scope = original.header("X-Card-Cache-Subject")
                if (scope != credentials.subject) throw java.io.IOException("账号已切换")
                chain.proceed(original.newBuilder().removeHeader("X-Card-Cache-Subject").header("Authorization", "Bearer ${credentials.accessToken}")
                    .header("X-Logto-Userinfo", credentials.identityToken).build())
            }.build())
            .build().also { shared=it }
    }
    fun request(context: Context, model: Any, subject: String): ImageRequest {
        return ImageRequest.Builder(context).apply {
            if(model is String && model.startsWith("/media/members/")) {
                val scope=MessageDigest.getInstance("SHA-256").digest(subject.toByteArray()).joinToString("") { "%02x".format(it) }
                val key="$scope:$model"
                data(CardClient.ORIGIN+"/native"+model)
                addHeader("X-Card-Cache-Subject", subject)
                memoryCacheKey(key)
                diskCacheKey(key)
            } else if(model !is String || !model.startsWith("http")) data(model)
            listener(onSuccess={_,result -> android.util.Log.d("CardImageCache", result.dataSource.name) })
        }.build()
    }
    fun clear() { shared?.memoryCache?.clear(); shared?.diskCache?.clear() }
    fun thumbnail(path: String): String {
        if(!path.startsWith("/media/members/") || path.contains(".thumb.")) return path
        val dot=path.lastIndexOf('.')
        return if(dot>path.lastIndexOf('/')) path.substring(0,dot)+".thumb"+path.substring(dot) else path
    }
}
