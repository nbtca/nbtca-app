package space.nbtca.app.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class IcsParserTest {
    private val china = ZoneId.of("Asia/Shanghai")

    @Test
    fun parsesSchoolCalendarAndComputesWeek() {
        val events = IcsParser.parse(fixture("school.ics"))
        assertTrue(events.isNotEmpty())

        val termStart = events.filter { it.title == "秋季学期开始上课" && it.date?.year == 2026 }
            .maxByOrNull { it.date!! }
        assertNotNull(termStart)
        assertEquals("2026-09-14", termStart!!.date.toString())

        val week = SchoolCalendar.currentWeek(
            events,
            ZonedDateTime.of(2026, 10, 21, 12, 0, 0, 0, china)
        )
        assertEquals(6, week.current)
        assertEquals(20, week.total)
    }

    @Test
    fun parsesAssociationEvents() {
        val events = IcsParser.parse(fixture("events.ics"))
        assertTrue(events.isNotEmpty())
        val meeting = events.firstOrNull { it.title.contains("NWDC") }
        assertNotNull(meeting)
        assertFalse(meeting!!.allDay)
        assertEquals("+08:00", meeting.start!!.offset.id)
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }
            .readText(Charsets.UTF_8)
}
