package space.nbtca.app.core.academic

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 教务数据本地仓库：学号、密码（Keystore 加密）、课表/考试/成绩快照全部落盘，
 * 断网也能看。密码只用于自动重新登录教务系统。
 */
class AcademicStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("academic_store", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    var studentId: String
        get() = prefs.getString(KEY_STUDENT_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_STUDENT_ID, value.trim()).apply()

    var password: String
        get() = decrypt(prefs.getString(KEY_PASSWORD, null)).orEmpty()
        set(value) {
            if (value.isEmpty()) {
                prefs.edit().remove(KEY_PASSWORD).apply()
            } else {
                encrypt(value)?.let { prefs.edit().putString(KEY_PASSWORD, it).apply() }
            }
        }

    val hasCredentials: Boolean get() = studentId.isNotEmpty() && password.isNotEmpty()

    fun loadSnapshot(): AcademicSnapshot? {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { json.decodeFromString<AcademicSnapshot>(raw) }.getOrNull()
    }

    fun saveSnapshot(snapshot: AcademicSnapshot) {
        prefs.edit().putString(KEY_SNAPSHOT, json.encodeToString(AcademicSnapshot.serializer(), snapshot)).apply()
    }

    fun clearCredentials() {
        prefs.edit().remove(KEY_STUDENT_ID).remove(KEY_PASSWORD).remove(KEY_COOKIES).apply()
    }

    fun clearAll() = prefs.edit().clear().apply()

    var cookies: String?
        get() = prefs.getString(KEY_COOKIES, null)
        set(value) = prefs.edit().putString(KEY_COOKIES, value).apply()

    // ---------------- Keystore AES/GCM ----------------

    private fun secretKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        generator.generateKey()
    }.getOrNull()

    private fun encrypt(plain: String): String? = runCatching {
        val key = secretKey() ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray())
        Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return null
        return runCatching {
            val key = secretKey() ?: return null
            val data = Base64.decode(stored, Base64.NO_WRAP)
            if (data.size <= 12) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
            String(cipher.doFinal(data, 12, data.size - 12))
        }.getOrNull()
    }

    companion object {
        private const val KEY_STUDENT_ID = "student_id"
        private const val KEY_PASSWORD = "password_cipher"
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_ALIAS = "nbtca_academic_password"

        @Volatile
        private var instance: AcademicStore? = null

        fun getInstance(context: Context): AcademicStore =
            instance ?: synchronized(this) {
                instance ?: AcademicStore(context.applicationContext).also { instance = it }
            }
    }
}
