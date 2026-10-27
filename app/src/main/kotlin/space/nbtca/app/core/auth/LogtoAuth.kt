package space.nbtca.app.core.auth

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

/** Logto 明确返回的 HTTP 错误（如 invalid_grant），与网络异常区分开。 */
class LogtoHttpError(val code: Int, val body: String) : Exception("HTTP $code")

@Serializable
data class LogtoTokenSet(
    val access_token: String,
    val token_type: String = "Bearer",
    val expires_in: Long = 3600,
    val refresh_token: String? = null,
    val id_token: String? = null,
    val scope: String = ""
)

object LogtoAuth {
    const val ENDPOINT = "https://auth.app.nbtca.space"
    const val APP_ID = "a3tt7oyxeounfiqqugiah"
    const val REDIRECT_URI = "https://nbtca.space/callback"
    const val SCOPE = "openid profile email offline_access roles custom_data"

    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    fun generateCodeVerifier(length: Int = 64): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(allowedChars[secureRandom.nextInt(allowedChars.length)])
        }
        return sb.toString()
    }

    fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun generateState(bytesCount: Int = 24): String {
        val bytes = ByteArray(bytesCount)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun buildAuthorizeUrl(codeChallenge: String, state: String): String {
        return Uri.parse("$ENDPOINT/oidc/auth")
            .buildUpon()
            .appendQueryParameter("client_id", APP_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()
            .toString()
    }

    fun isRedirect(url: String): Boolean {
        val uri = Uri.parse(url)
        val expected = Uri.parse(REDIRECT_URI)
        return uri.scheme == expected.scheme &&
                uri.host == expected.host &&
                uri.path == expected.path
    }

    suspend fun exchangeCode(
        client: OkHttpClient,
        code: String,
        codeVerifier: String
    ): Result<LogtoTokenSet> = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", APP_ID)
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()

            val request = Request.Builder()
                .url("$ENDPOINT/oidc/token")
                .post(body)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val bodyStr = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
                val tokenSet = json.decodeFromString<LogtoTokenSet>(bodyStr)
                Result.success(tokenSet)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(
        client: OkHttpClient,
        refreshToken: String
    ): Result<LogtoTokenSet> = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", APP_ID)
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url("$ENDPOINT/oidc/token")
                .post(body)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(LogtoHttpError(response.code, bodyStr))
                }
                if (bodyStr.isBlank()) {
                    return@withContext Result.failure(Exception("Logto 返回空响应"))
                }
                Result.success(json.decodeFromString<LogtoTokenSet>(bodyStr))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseJwtClaims(token: String?): Map<String, String>? {
        if (token.isNullOrEmpty()) return null
        val parts = token.split(".")
        if (parts.size < 2) return null
        return try {
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP)
            val jsonStr = String(payloadBytes, Charsets.UTF_8)
            val jsonObject = json.parseToJsonElement(jsonStr).jsonObject
            jsonObject.mapValues { it.value.jsonPrimitive.content }
        } catch (_: Exception) {
            null
        }
    }
}
