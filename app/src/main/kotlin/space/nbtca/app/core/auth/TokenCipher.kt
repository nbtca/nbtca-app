package space.nbtca.app.core.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore 加密本地保存的 NBTCA 令牌（access / refresh / id），
 * 避免明文躺在 SharedPreferences 里。密钥不出 Keystore，卸载即失效。
 */
object TokenCipher {

    private const val ALIAS = "nbtca_account_tokens"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun secretKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }.getOrNull()

    fun encrypt(plain: String?): String? {
        if (plain.isNullOrEmpty()) return null
        return runCatching {
            val key = secretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            Base64.encodeToString(iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        }.getOrNull()
    }

    fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return null
        return runCatching {
            val key = secretKey() ?: return null
            val data = Base64.decode(stored, Base64.NO_WRAP)
            if (data.size <= 12) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
            String(cipher.doFinal(data, 12, data.size - 12))
        }.getOrNull()
    }
}
