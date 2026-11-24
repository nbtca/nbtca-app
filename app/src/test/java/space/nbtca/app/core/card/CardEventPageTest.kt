package space.nbtca.app.core.card

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class CardEventPageTest {
    private fun list(status: String, href: String) = """
        <ul><li class="rise">
          <a class="pass-card thumb" href="$href"><div class="who"></div></a>
          <div class="caption"><span class="chip">$status</span><a href="$href"><h3>活动样例</h3></a>
            <p>
              11月28日<br>09:30–17:00
              <br>活动地点
            </p>
          </div>
        </li></ul>
    """
    private fun page(upcoming: String, past: String = "") = Jsoup.parse("""
        <main><header><h1>活动</h1></header>
          <section class="shelf"><header><h2>我的券</h2></header><ul><li>not an event row</li></ul></section>
          $upcoming
          ${if (past.isNotEmpty()) "<section><header><p>Past</p><h2>往期活动</h2></header>$past</section>" else ""}
        </main>
    """)

    @Test fun groupsUpcomingAndPastAndPreservesDateVenueAndDetailsLink() {
        val doc = page(list("正在领取", "/e/next-event"), list("已结束", "/e/past-event"))
        CardRepository.validatePage("/e", doc)
        val page = requireNotNull(CardEventPage.parse(doc))
        val upcoming = page.upcoming.single()
        assertEquals("活动样例", upcoming.title)
        assertEquals("正在领取", upcoming.status)
        assertEquals("11月28日\n09:30–17:00\n活动地点", upcoming.whenAndWhere)
        assertEquals("/e/next-event", upcoming.eventPath)
        assertNotNull(upcoming.preview)
        assertEquals("往期活动", page.pastTitle)
        assertEquals("已结束", page.past.single().status)
        assertEquals("/e/past-event", page.past.single().eventPath)
    }

    @Test fun pastOnlyKeepsUpcomingEmptyAndIgnoresTicketShelf() {
        val emptyUpcoming = "<p class='muted'>近期没有活动。</p>"
        val pastOnly = requireNotNull(CardEventPage.parse(page(emptyUpcoming, list("已结束", "/e/archive"))))
        assertTrue(pastOnly.upcoming.isEmpty())
        assertEquals(1, pastOnly.past.size)
        val empty = requireNotNull(CardEventPage.parse(page(emptyUpcoming)))
        assertTrue(empty.upcoming.isEmpty())
        assertTrue(empty.past.isEmpty())
        assertNull(empty.pastTitle)
    }

    @Test fun doesNotTreatNonDetailLinksOrLoginAsEvents() {
        val page = requireNotNull(CardEventPage.parse(page(list("正在领取", "/e/new"), list("已结束", "https://example.invalid/e/past"))))
        assertNull(page.upcoming.single().eventPath)
        assertNull(page.past.single().eventPath)
        assertNull(CardEventPage.parse(Jsoup.parse("<main><h1>登录</h1></main>")))
    }

    @Test fun hostPassCannotExposeAttendeeCheckInCode() {
        val doc = Jsoup.parse("""
            <section class="info"><h1>活动</h1><span class="chip">主办</span></section>
            <div class="pass-card full" data-state="host">
              <div class="stub"><div class="serial"><small>来宾入场后计入</small></div></div>
              <div class="ticket" data-ticket="example" data-check-in="open"><div class="under"><svg class="qr"></svg></div></div>
            </div>
            <div class="act"><p class="notice">发起人不用领券</p></div>
        """.trimIndent())
        CardRepository.validatePage("/e/fixture", doc)
        val card = requireNotNull(doc.selectFirst(".pass-card"))
        assertNull(CardRepository.checkInCode(card))
        assertEquals("发起人不用领券", CardRepository.claimError(doc))
        assertNotNull(CardRepository.checkInCode(card.attr("data-state", "valid")))
        assertNull(CardRepository.checkInCode(card.removeClass("full").addClass("thumb")))
    }
}
