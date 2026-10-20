package space.nbtca.app.core.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import space.nbtca.app.core.model.AuthState
import space.nbtca.app.core.model.UserProfile
import java.util.concurrent.TimeUnit

/**
 * Logto 身份 + Saturday 会员角色 + 界面视角。
 * 与 Flutter AuthService 行为对齐：token 过期自动 refresh，视角切换不改 token。
 */
class AuthManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val refreshMutex = kotlinx.coroutines.sync.Mutex()


    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var idToken: String? = null
    private var expiresAt: Long = 0L
    private var viewRole: String? = null

    init {
        restore()
    }

    private fun restore() {
        accessToken = TokenCipher.decrypt(prefs.getString(KEY_ACCESS, null))
            ?: prefs.getString(KEY_ACCESS, null)   // 兼容旧版本明文令牌
        refreshToken = TokenCipher.decrypt(prefs.getString(KEY_REFRESH, null))
            ?: prefs.getString(KEY_REFRESH, null)
        idToken = TokenCipher.decrypt(prefs.getString(KEY_ID, null))
            ?: prefs.getString(KEY_ID, null)
        expiresAt = prefs.getLong(KEY_EXPIRES, 0L)
        viewRole = prefs.getString(KEY_VIEW_ROLE, null)
        val cached = prefs.getString(KEY_PROFILE, null)
        val profile = cached?.let { runCatching { json.decodeFromString<UserProfile>(it) }.getOrNull() }
        if (profile != null && !accessToken.isNullOrEmpty()) {
            _state.value = AuthState(profile = profile, isLoggedIn = true, viewRole = viewRole)
        }
    }

    fun currentToken(): String? = accessToken

    /** 业务请求前取有效 token，过期则自动 refresh。 */
    suspend fun getValidAccessToken(): String? {
        val token = accessToken ?: return null
        if (token.startsWith("dev_") || token.startsWith("mock_")) return token
        if (expiresAt == 0L || System.currentTimeMillis() < expiresAt - 60_000L) return token
        return refresh()
    }

    /**
     * 串行刷新：Logto 的 refresh_token 是一次性轮换的，并发刷新会让后一次拿到
     * 已失效的旧 token（invalid_grant）从而被误判成"登录过期"。
     */
    private suspend fun refresh(): String? = refreshMutex.withLock {
        // 拿到锁后先复查：可能已被其他请求刷新过。
        val current = accessToken
        if (!current.isNullOrEmpty() && System.currentTimeMillis() < expiresAt - 60_000L) {
            return@withLock current
        }
        val rt = refreshToken
        if (rt.isNullOrEmpty()) {
            logout()
            return@withLock null
        }
        var resolved: String? = current
        LogtoAuth.refreshToken(http, rt).fold(
            onSuccess = { tokens ->
                applyTokens(tokens, rt)
                resolved = tokens.access_token
            },
            onFailure = { error ->
                // 只有 Logto 明确拒绝（400 invalid_grant / 401）才判定登录失效；
                // 断网、超时等一律保留缓存登录态，下次再试。
                if (error is LogtoHttpError && (error.code == 400 || error.code == 401)) {
                    logout()
                    resolved = null
                } else {
                    resolved = current?.takeIf { System.currentTimeMillis() < expiresAt }
                }
            }
        )
        resolved
    }

    /**
     * App 启动时恢复登录：token 临近过期则静默刷新，再校验一次用户资料。
     * 全程不阻塞首屏，网络失败时保留本地缓存身份（不需要重新登录）。
     */
    suspend fun bootstrap() {
        val token = accessToken ?: return
        if (token.startsWith("dev_") || token.startsWith("mock_")) return
        if (System.currentTimeMillis() >= expiresAt - 60_000L) {
            refresh()
        }
        validateStoredSession()
    }

    private fun applyTokens(tokens: LogtoTokenSet, fallbackRefresh: String? = null) {
        accessToken = tokens.access_token
        refreshToken = tokens.refresh_token ?: fallbackRefresh ?: refreshToken
        idToken = tokens.id_token ?: idToken
        expiresAt = System.currentTimeMillis() + tokens.expires_in.coerceAtLeast(60) * 1000L
        prefs.edit()
            .putString(KEY_ACCESS, TokenCipher.encrypt(accessToken))
            .putString(KEY_REFRESH, TokenCipher.encrypt(refreshToken))
            .putString(KEY_ID, TokenCipher.encrypt(idToken))
            .putLong(KEY_EXPIRES, expiresAt)
            .apply()
    }

    /** PKCE 换取 token 后建立登录态，并拉取 Logto + Saturday 身份。 */
    suspend fun loginWithTokens(tokens: LogtoTokenSet): Result<UserProfile> = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(isAuthenticating = true)
        try {
            applyTokens(tokens)
            val profile = fetchProfile(tokens.access_token, tokens.id_token)
            viewRole = null
            prefs.edit()
                .putString(KEY_PROFILE, json.encodeToString(UserProfile.serializer(), profile))
                .remove(KEY_VIEW_ROLE)
                .apply()
            _state.value = AuthState(profile = profile, isLoggedIn = true, viewRole = null, isAuthenticating = false)
            Result.success(profile)
        } catch (e: Exception) {
            clearLocal()
            _state.value = AuthState()
            Result.failure(e)
        }
    }

    private fun fetchProfile(token: String, idTokenValue: String?): UserProfile {
        val info = tryGetJson("${LogtoAuth.ENDPOINT}/oidc/me", token)
        val claims = LogtoAuth.parseJwtClaims(idTokenValue)
        val member = tryGetJson("${SATURDAY_ENDPOINT}/member", token)

        val merged = info ?: claims ?: member
            ?: throw IllegalStateException("无法验证登录身份，请检查网络后重试")

        val roles = LinkedHashSet<String>()
        info?.get("roles")?.let { raw -> splitRoles(raw).forEach { roles.add(it) } }
        claims?.get("roles")?.let { raw -> splitRoles(raw).forEach { roles.add(it) } }
        when (member?.get("role")?.lowercase()) {
            "admin" -> {
                roles.add("Repair Admin")
                roles.add("Repair Member")
            }
            "member" -> roles.add("Repair Member")
        }

        val sub = merged["sub"]?.takeIf { it.isNotBlank() }
            ?: member?.get("memberId")
            ?: ""
        if (sub.isBlank()) throw IllegalStateException("身份服务未返回用户标识")

        return UserProfile(
            sub = sub,
            name = merged["name"] ?: merged["username"] ?: member?.get("alias") ?: member?.get("name") ?: sub,
            email = merged["email"] ?: "",
            username = merged["username"] ?: merged["preferred_username"] ?: member?.get("alias"),
            avatar = merged["picture"] ?: merged["avatar"] ?: member?.get("avatar"),
            roles = roles.toList(),
            memberId = member?.get("memberId"),
            saturdayRole = member?.get("role")
        )
    }

    private fun splitRoles(raw: String): List<String> =
        raw.split("|", ",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun tryGetJson(url: String, token: String): Map<String, String>? {
        return try {
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                parseFlatObject(res.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFlatObject(raw: String): Map<String, String>? {
        return try {
            val obj: JsonObject = json.parseToJsonElement(raw).jsonObject
            obj.mapValues { (_, value) ->
                when (value) {
                    is JsonArray -> runCatching {
                        value.jsonArray.joinToString("|") { it.jsonPrimitive.content }
                    }.getOrElse { "" }
                    is JsonObject -> ""
                    else -> value.jsonPrimitive.contentOrNull ?: ""
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 只切换界面视角，不改账号与 token。 */
    fun setViewRole(role: String?) {
        val current = _state.value
        val profile = current.profile
        if (!current.isLoggedIn || profile == null) {
            viewRole = null
            _state.value = current.copy(viewRole = null)
            return
        }
        val normalized = role?.trim()?.lowercase()
        if (normalized != null && normalized !in setOf("admin", "member", "client")) return
        if (normalized == "admin" && !profile.isRepairAdmin) return
        if (normalized == "member" && !profile.isRepairStaff) return
        viewRole = normalized
        prefs.edit().putString(KEY_VIEW_ROLE, normalized).apply()
        _state.value = current.copy(viewRole = normalized)
    }

    /** 手动退出：清除本机保存的账号信息、令牌与头像缓存。 */
    fun logout() {
        clearLocal()
        _state.value = AuthState()
        runCatching {
            coil.Coil.imageLoader(appContext).apply {
                memoryCache?.clear()
                diskCache?.clear()
            }
        }
    }

    private fun clearLocal() {
        accessToken = null
        refreshToken = null
        idToken = null
        expiresAt = 0L
        viewRole = null
        prefs.edit().clear().apply()
    }

    /** 启动时静默校验；网络失败保留缓存身份，不强制退出。 */
    suspend fun validateStoredSession() {
        val token = accessToken ?: return
        if (token.startsWith("dev_") || token.startsWith("mock_")) return
        try {
            val profile = withContext(Dispatchers.IO) { fetchProfile(token, idToken) }
            prefs.edit().putString(KEY_PROFILE, json.encodeToString(UserProfile.serializer(), profile)).apply()
            _state.value = _state.value.copy(profile = profile, isLoggedIn = true)
        } catch (_: Exception) {
            // 离线保留缓存身份。
        }
    }

    companion object {
        const val SATURDAY_ENDPOINT = "https://api.nbtca.space/v2"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_ID = "id_token"
        private const val KEY_EXPIRES = "expires_at"
        private const val KEY_PROFILE = "user_profile"
        private const val KEY_VIEW_ROLE = "view_role"

        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager =
            instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
    }
}
