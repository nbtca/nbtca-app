package space.nbtca.app.core.parse

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class IcsEvent(
    val uid: String,
    val title: String,
    val description: String,
    val location: String,
    val url: String,
    val start: ZonedDateTime?,
    val end: ZonedDateTime?,
    val date: LocalDate?,
    val allDay: Boolean
)

/**
 * 极简 iCalendar 解析器，覆盖 school.ics / events.ics 用到的字段：
 * SUMMARY、DESCRIPTION、UID、LOCATION、URL、DTSTART、DTEND。
 * 处理折行、全天事件、UTC/带偏移/浮动本地时间。
 */
object IcsParser {
    private val china: ZoneId = ZoneId.of("Asia/Shanghai")

    fun parse(text: String): List<IcsEvent> {
        val lines = unfold(text)
        val events = mutableListOf<IcsEvent>()
        var current: Builder? = null

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            when (line.uppercase()) {
                "BEGIN:VEVENT" -> current = Builder()
                "END:VEVENT" -> {
                    current?.build()?.let { events.add(it) }
                    current = null
                }
                else -> {
                    val builder = current ?: return@forEach
                    val (name, value) = splitProperty(line)
                    when (name) {
                        "SUMMARY" -> builder.title = value
                        "DESCRIPTION" -> builder.description = value
                        "UID" -> builder.uid = value
                        "LOCATION" -> builder.location = value
                        "URL" -> builder.url = value
                        "DTSTART" -> parseDateTime(value)?.let { parsed ->
                            builder.start = parsed.first
                            builder.date = parsed.second
                            builder.allDay = parsed.third
                        }
                        "DTEND" -> parseDateTime(value)?.let { parsed ->
                            builder.end = parsed.first
                            if (builder.date == null) builder.date = parsed.second
                        }
                    }
                }
            }
        }

        return events.filter { it.title.isNotBlank() && (it.date != null || it.start != null) }
            .sortedWith(compareBy({ it.date }, { it.title }))
    }

    private class Builder {
        var uid = ""
        var title = ""
        var description = ""
        var location = ""
        var url = ""
        var start: ZonedDateTime? = null
        var end: ZonedDateTime? = null
        var date: LocalDate? = null
        var allDay = false

        fun build(): IcsEvent? {
            if (title.isBlank()) return null
            val resolvedDate = date ?: start?.toLocalDate() ?: return null
            return IcsEvent(
                uid = uid,
                title = title,
                description = description,
                location = location,
                url = url,
                start = start ?: resolvedDate.atStartOfDay(china),
                end = end,
                date = resolvedDate,
                allDay = allDay
            )
        }
    }

    private fun unfold(text: String): List<String> {
        val raw = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val lines = mutableListOf<String>()
        raw.forEach { line ->
            if (line.isNotEmpty() && (line[0] == ' ' || line[0] == '\t') && lines.isNotEmpty()) {
                lines[lines.lastIndex] = lines.last() + line.trimStart(' ', '\t')
            } else {
                lines.add(line)
            }
        }
        return lines
    }

    private fun splitProperty(line: String): Pair<String, String> {
        val index = line.indexOf(':')
        if (index < 0) return "" to ""
        val head = line.substring(0, index)
        val name = head.substringBefore(';').trim().uppercase()
        return name to unescape(line.substring(index + 1))
    }

    /** 返回 (时间, 日期, 是否全天)。 */
    private fun parseDateTime(rawValue: String): Triple<ZonedDateTime, LocalDate, Boolean>? {
        val value = rawValue.trim().trim('"')
        if (value.length >= 8 && value.take(8).all { it.isDigit() }) {
            val year = value.substring(0, 4).toIntOrNull() ?: return null
            val month = value.substring(4, 6).toIntOrNull() ?: return null
            val day = value.substring(6, 8).toIntOrNull() ?: return null
            if (month !in 1..12 || day !in 1..31) return null
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
            if (value.length == 8 || value[8] != 'T') {
                return Triple(date.atStartOfDay(china), date, true)
            }
            val hour = value.substring(9, 11).toIntOrNull() ?: 0
            val minute = value.substring(11, 13).toIntOrNull() ?: 0
            val second = if (value.length >= 15) value.substring(13, 15).toIntOrNull() ?: 0 else 0
            val local = runCatching {
                LocalDateTime.of(date, java.time.LocalTime.of(hour, minute, second))
            }.getOrNull() ?: return null
            if (value.endsWith("Z", ignoreCase = true)) {
                val instant = local.toInstant(java.time.ZoneOffset.UTC)
                return Triple(instant.atZone(china), date, false)
            }
            val offset = value.takeLast(5)
            if (value.length >= 19 && (offset.startsWith("+") || offset.startsWith("-"))) {
                val parsed = runCatching {
                    OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssZ"))
                }.getOrNull()
                if (parsed != null) return Triple(parsed.atZoneSameInstant(china), date, false)
            }
            return Triple(local.atZone(china), date, false)
        }

        val parsed = runCatching { OffsetDateTime.parse(value).atZoneSameInstant(china) }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(value).withZoneSameInstant(china) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atZone(china) }.getOrNull()
            ?: runCatching { LocalDate.parse(value).atStartOfDay(china) }.getOrNull()
            ?: return null
        return Triple(parsed, parsed.toLocalDate(), false)
    }

    private fun unescape(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == '\\' && index + 1 < value.length) {
                when (value[index + 1]) {
                    'n', 'N' -> result.append('\n')
                    ',', ';', '\\' -> result.append(value[index + 1])
                    else -> result.append(value[index + 1])
                }
                index += 2
            } else {
                result.append(ch)
                index += 1
            }
        }
        return result.toString()
    }
}
