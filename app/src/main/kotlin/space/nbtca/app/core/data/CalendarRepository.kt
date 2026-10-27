package space.nbtca.app.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import space.nbtca.app.core.model.AssociationCalendar
import space.nbtca.app.core.model.AssociationEvent
import space.nbtca.app.core.network.ApiException
import space.nbtca.app.core.network.NbtcaEndpoints
import space.nbtca.app.core.network.NbtcaHttp
import space.nbtca.app.core.parse.IcsEvent
import space.nbtca.app.core.parse.IcsParser
import space.nbtca.app.core.parse.SchoolCalendar
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
private data class SchoolWeekSnapshot(
    val current: Int = 1,
    val total: Int = 20
)

/**
 * 校历与协会活动直连 ical.nbtca.space，替代旧 BFF 的 /tools/calendar 与
 * /tools/association-calendar。解析结果缓存 30 分钟，失败时回退上一次成功数据。
 */
class CalendarRepository(
    private val http: OkHttpClient,
    private val cache: DiskCache
) {
    private val china = ZoneId.of("Asia/Shanghai")
    private val mutex = Mutex()

    @Volatile
    private var weekMemory: SchoolWeekSnapshot? = null

    @Volatile
    private var weekMemoryAt = 0L

    @Volatile
    private var eventsMemory: AssociationCalendar? = null

    @Volatile
    private var eventsMemoryAt = 0L

    suspend fun getSchoolWeek(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            weekMemory?.let {
                if (now - weekMemoryAt < CALENDAR_TTL_MS) return@withLock it.current to it.total
            }
            cache.read(WEEK_CACHE_KEY, SchoolWeekSnapshot.serializer(), CALENDAR_TTL_MS)?.let { cached ->
                weekMemory = cached
                weekMemoryAt = now
                return@withLock cached.current to cached.total
            }

            try {
                val text = NbtcaHttp.getText(http, NbtcaEndpoints.SCHOOL_ICS, "text/calendar,*/*")
                val events = IcsParser.parse(text)
                val week = SchoolCalendar.currentWeek(events, ZonedDateTime.now(china))
                val snapshot = SchoolWeekSnapshot(week.current, week.total)
                cache.write(WEEK_CACHE_KEY, SchoolWeekSnapshot.serializer(), snapshot)
                weekMemory = snapshot
                weekMemoryAt = now
                snapshot.current to snapshot.total
            } catch (_: Exception) {
                val stale = cache.readStale(WEEK_CACHE_KEY, SchoolWeekSnapshot.serializer())
                if (stale != null) {
                    weekMemory = stale
                    weekMemoryAt = 0L
                    stale.current to stale.total
                } else {
                    null
                }
            }
        }
    }

    suspend fun getAssociationCalendar(): Result<AssociationCalendar> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            eventsMemory?.let {
                if (now - eventsMemoryAt < CALENDAR_TTL_MS) return@withLock Result.success(it)
            }
            cache.read(EVENTS_CACHE_KEY, AssociationCalendar.serializer(), CALENDAR_TTL_MS)?.let { cached ->
                eventsMemory = cached
                eventsMemoryAt = now
                return@withLock Result.success(cached)
            }

            try {
                val text = NbtcaHttp.getText(http, NbtcaEndpoints.EVENTS_ICS, "text/calendar,*/*")
                val calendar = buildCalendar(IcsParser.parse(text), ZonedDateTime.now(china))
                cache.write(EVENTS_CACHE_KEY, AssociationCalendar.serializer(), calendar)
                eventsMemory = calendar
                eventsMemoryAt = now
                Result.success(calendar)
            } catch (error: Exception) {
                val stale = cache.readStale(EVENTS_CACHE_KEY, AssociationCalendar.serializer())
                if (stale != null) {
                    eventsMemory = stale
                    eventsMemoryAt = 0L
                    Result.success(stale)
                } else {
                    Result.failure(
                        ApiException(
                            error.message ?: "协会日历暂时不可用",
                            null
                        )
                    )
                }
            }
        }
    }

    private fun buildCalendar(events: List<IcsEvent>, now: ZonedDateTime): AssociationCalendar {
        val from = now.toLocalDate().atStartOfDay(china)
        val to = now.plusDays(180)
        val output = events.mapNotNull { event ->
            val start = event.start ?: return@mapNotNull null
            if (start.isBefore(from) || start.isAfter(to)) return@mapNotNull null
            val end = event.end ?: if (event.allDay) start.plusDays(1) else start.plusHours(1)
            AssociationEvent(
                id = event.uid,
                title = event.title,
                description = event.description,
                location = event.location,
                url = event.url.ifBlank { firstUrl(event.description) },
                start = start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                end = end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                allDay = event.allDay
            )
        }.sortedBy { it.start }

        return AssociationCalendar(
            name = "NBTCA 计算机协会日历",
            updated = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            events = output
        )
    }

    private fun firstUrl(text: String): String {
        val match = Regex("https?://[^\\s，。；）]+").find(text) ?: return ""
        return match.value.trimEnd('.', ',', ';', ':')
    }

    companion object {
        private const val WEEK_CACHE_KEY = "school-week"
        private const val EVENTS_CACHE_KEY = "association-calendar"
        private const val CALENDAR_TTL_MS = 30 * 60 * 1000L
    }
}
