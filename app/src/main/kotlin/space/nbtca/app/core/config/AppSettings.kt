package space.nbtca.app.core.config

import android.content.Context
import android.content.SharedPreferences

class AppSettings private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var apiBaseUrl: String
        get() = prefs.getString(KEY_API_BASE_URL, PUBLIC_BASE_URL) ?: PUBLIC_BASE_URL
        set(value) {
            val normalized = normalize(value)
            if (normalized.isNotEmpty()) {
                prefs.edit().putString(KEY_API_BASE_URL, normalized).apply()
            }
        }

    /** system / light / dark */
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    private fun normalize(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return PUBLIC_BASE_URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }

    companion object {
        const val LOCAL_BASE_URL = "http://10.0.2.2:8080/api/v1"
        const val PUBLIC_BASE_URL = "https://bff.nbtca.space/api/v1"

        private const val KEY_API_BASE_URL = "settings.api_base_url"
        private const val KEY_THEME_MODE = "settings.theme_mode"

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
