package space.nbtca.app.core.parse

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class SchoolWeek(val current: Int, val total: Int = TOTAL_WEEKS) {
    companion object {
        const val TOTAL_WEEKS = 20
        val EMPTY = SchoolWeek(1, TOTAL_WEEKS)
    }
}

/**
 * 从 school.ics 计算当前教学周。逻辑与旧 BFF 的 CalendarService 保持一致：
 * · 9 月—次年 1 月为秋季学期，2 月—8 月为春季学期；
 * · 以「秋季/春季学期开始上课」事件为第 1 周起点；
 * · 找不到事件时回退到 9 月 1 日 / 3 月 1 日；
 * · 总周数固定 20，周次夹在 1..20。
 */
object SchoolCalendar {
    private val china: ZoneId = ZoneId.of("Asia/Shanghai")

    fun currentWeek(
        events: List<IcsEvent>,
        now: ZonedDateTime = ZonedDateTime.now(china)
    ): SchoolWeek {
        val today = now.withZoneSameInstant(china).toLocalDate()
        val isSpring = today.monthValue in 2..8
        val semester = if (isSpring) "春季学期" else "秋季学期"
        val startYear = if (today.monthValue < 9) today.year - 1 else today.year

        val start = findTermStart(events, today, semester, startYear)
            ?: if (isSpring) LocalDate.of(startYear + 1, 3, 1)
            else LocalDate.of(startYear, 9, 1)

        val days = ChronoUnit.DAYS.between(start, today)
        val current = (days / 7 + 1).toInt().coerceIn(1, SchoolWeek.TOTAL_WEEKS)
        return SchoolWeek(current, SchoolWeek.TOTAL_WEEKS)
    }

    private fun findTermStart(
        events: List<IcsEvent>,
        today: LocalDate,
        semester: String,
        startYear: Int
    ): LocalDate? {
        val wanted = if (semester == "春季学期") "春季学期开始上课" else "秋季学期开始上课"
        val rangeStart = LocalDate.of(startYear, 9, 1)
        val rangeEnd = rangeStart.plusYears(1)
        return events.asSequence()
            .filter { it.title.trim() == wanted }
            .mapNotNull { it.date }
            .filter { date ->
                date >= rangeStart && date < rangeEnd && !date.isAfter(today)
            }
            .maxOrNull()
    }
}
