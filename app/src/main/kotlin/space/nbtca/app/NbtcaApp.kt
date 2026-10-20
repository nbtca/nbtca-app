package space.nbtca.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import coil.EventListener
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局图片加载器：支持 SVG（协会多处封面是 .svg）、
 * 并附加浏览器 UA/Referer 以绕过图床防盗链（如 i.pinimg.com）。
 * data:base64 封面在 CoverImage 里自行解码（Coil 2.x 无 data URI Fetcher）。
 */
class NbtcaApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val builder = ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                            )
                            .header("Referer", "https://nbtca.space/")
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .crossfade(true)

        if (debuggable) {
            builder.eventListener(object : EventListener {
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    Log.d("CoilDebug", "OK  ${request.data}")
                }

                override fun onError(request: ImageRequest, result: ErrorResult) {
                    val data = request.data
                    val short = if (data is String && data.length > 80) data.take(60) + "..." else data
                    Log.w("CoilDebug", "ERR $short -> ${result.throwable.message}")
                }
            })
        }
        return builder.build()
    }
}
