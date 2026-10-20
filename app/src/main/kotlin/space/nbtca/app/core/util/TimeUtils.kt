package space.nbtca.app.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CHINA_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 兼容 Saturday 的 'yyyy-MM-dd HH:mm:ss' 与 RFC3339 两种时间串，统一显示为 MM-dd HH:mm。 */
fun formatChinaDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val parsed = parseFlexible(raw.trim()) ?: return raw
    return DATE_TIME_FORMAT.format(parsed)
}

fun formatChinaDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val parsed = parseFlexible(raw.trim()) ?: return raw
    return DateTimeFormatter.ofPattern("yyyy-MM-dd").format(parsed)
}

/** 解析 Saturday 的 ISO 时间串（含时区）为 epoch 毫秒，用于稳定排序。 */
fun epochMillis(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    val value = raw.trim()
    return try {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(value.replace(" ", "T"))
                    .atZone(CHINA_ZONE).toInstant().toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }
}

fun parseChinaTime(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    return parseFlexible(raw.trim())
}

private fun parseFlexible(value: String): LocalDateTime? {
    runCatching { return LocalDateTime.parse(value.replace(" ", "T")) }
    runCatching {
        return OffsetDateTime.parse(value).atZoneSameInstant(CHINA_ZONE).toLocalDateTime()
    }
    runCatching {
        return Instant.parse(value).atZone(CHINA_ZONE).toLocalDateTime()
    }
    runCatching { return LocalDate.parse(value).atStartOfDay() }
    return null
}

/** '10 月 24 日 · 09:00 - 17:00' 形式的维修日文案。 */
fun repairDayTimeText(start: String?, end: String?, allDay: Boolean): String {
    val startTime = parseChinaTime(start) ?: return "近期暂无维修日安排，请关注协会公共日历"
    val date = "${startTime.monthValue} 月 ${startTime.dayOfMonth} 日"
    if (allDay) return "$date · 全天"
    val startClock = String.format("%02d:%02d", startTime.hour, startTime.minute)
    val endTime = parseChinaTime(end) ?: return "$date · $startClock"
    val endClock = String.format("%02d:%02d", endTime.hour, endTime.minute)
    return "$date · $startClock - $endClock"
}

/** epoch 秒 → MM-dd HH:mm */
fun formatEpochChina(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    val dt = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), CHINA_ZONE)
    return DateTimeFormatter.ofPattern("MM-dd HH:mm").format(dt)
}
