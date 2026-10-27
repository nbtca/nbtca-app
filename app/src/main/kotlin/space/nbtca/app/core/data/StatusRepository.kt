package space.nbtca.app.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import space.nbtca.app.core.model.ServiceStatus
import space.nbtca.app.core.network.NbtcaEndpoints
import space.nbtca.app.core.network.NbtcaHttp

/**
 * 服务状态直连 status.nbtca.space/api/status，替代旧 BFF 的 /tools/infrastructure。
 * 成功结果缓存 30 秒；失败时回退上一次成功数据，并标记为离线。
 */
class StatusRepository(
    private val http: OkHttpClient,
    private val cache: DiskCache
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val mutex = Mutex()

    @Volatile
    private var memory: ServiceStatus? = null

    @Volatile
    private var memoryAt = 0L

    suspend fun get(): ServiceStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            memory?.let {
                if (now - memoryAt < STATUS_TTL_MS) return@withLock it
            }
            cache.read(STATUS_CACHE_KEY, ServiceStatus.serializer(), STATUS_TTL_MS)?.let { cached ->
                memory = cached
                memoryAt = now
                return@withLock cached
            }

            try {
                val body = NbtcaHttp.getText(http, NbtcaEndpoints.STATUS_API, "application/json")
                val status = json.decodeFromString(ServiceStatus.serializer(), body)
                cache.write(STATUS_CACHE_KEY, ServiceStatus.serializer(), status)
                memory = status
                memoryAt = now
                status
            } catch (_: Exception) {
                val stale = cache.readStale(STATUS_CACHE_KEY, ServiceStatus.serializer())
                if (stale != null) {
                    memory = stale
                    memoryAt = 0L
                    stale.copy(
                        isOffline = true,
                        message = "网络不可用，显示上次获取的服务状态"
                    )
                } else {
                    ServiceStatus.offline("服务状态暂时不可用，请检查网络后重试")
                }
            }
        }
    }

    companion object {
        private const val STATUS_CACHE_KEY = "service-status"
        private const val STATUS_TTL_MS = 30 * 1000L
    }
}
