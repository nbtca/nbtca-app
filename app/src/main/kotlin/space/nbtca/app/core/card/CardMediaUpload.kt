package space.nbtca.app.core.card

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object CardMediaUpload {
    suspend fun upload(context: Context, kind: String, uri: Uri) {
        require(kind in listOf("portrait", "background"))
        val cookie = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(CardClient.ORIGIN).orEmpty() }
        withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
            val options = BitmapFactory.Options()
            while (maxOf(bounds.outWidth,bounds.outHeight) / options.inSampleSize.coerceAtLeast(1) > 2400) options.inSampleSize = options.inSampleSize.coerceAtLeast(1)*2
            val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,options) } ?: error("图片解码失败")
            fun encode(max: Int): ByteArray {
                val scale = minOf(1f, max.toFloat()/maxOf(bitmap.width,bitmap.height))
                val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width*scale).toInt().coerceAtLeast(1), (bitmap.height*scale).toInt().coerceAtLeast(1),true)
                return try { ByteArrayOutputStream().use { output -> scaled.compress(Bitmap.CompressFormat.PNG,100,output); output.toByteArray() } }
                finally { if(scaled !== bitmap) scaled.recycle() }
            }
            val full: ByteArray
            val thumb: ByteArray
            try { full = encode(1600); thumb = encode(400) } finally { bitmap.recycle() }
            check(full.size+thumb.size <= 4*1024*1024) { "图片处理后仍超过 4MB，请选择较小图片" }
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("full","full.png",full.toRequestBody("image/png".toMediaType()))
                .addFormDataPart("thumb","thumb.png",thumb.toRequestBody("image/png".toMediaType())).build()
            val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(45,TimeUnit.SECONDS).build()
            val request=Request.Builder().url(CardClient.ORIGIN+"/api/me/media?kind="+kind).header("Cookie",cookie)
                .header("Origin",CardClient.ORIGIN).header("Referer",CardClient.ORIGIN+"/me").put(body).build()
            client.newCall(request).execute().use { response ->
                if(response.code in 300..399 || response.code==401) throw CardLoginRequired()
                check(response.isSuccessful) { "图片保存失败（${response.code}）" }
                check(Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject["key"]?.jsonPrimitive?.content?.startsWith("members/")==true) { "图片保存结果异常" }
            }
        }
    }
}
