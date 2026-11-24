package space.nbtca.app.core.card

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class CardHomeTest {
    private fun guest(tickets: String = "", upcoming: String = "") = Jsoup.parse("""
        <header class="wrap guest"><p>Tickets</p><h1>我的票夹</h1><p class="lede">活动券和入场记录</p></header>
        <section class="wrap passes">
          ${if (upcoming.isNotBlank()) "<section class='shelf'><header><h2>待入场</h2></header><ul>$upcoming</ul></section>" else ""}
          <section class="shelf"><header><h2>票夹</h2><p class="muted">票根</p></header>
            ${if (tickets.isBlank()) "<p class='muted'>还没有入场过的活动。</p>" else ""}
            <ul>$tickets</ul>
          </section>
        </section>
    """.trimIndent())

    private fun row(state: String, href: String) = """
        <li class="rise"><a class="pass-card thumb" data-state="$state" href="$href"></a>
          <p><b>活动</b><span>入场记录</span><small>№ 4</small></p></li>
    """

    @Test fun acceptsEmptyGuestWalletWithoutGrantingMemberActions() {
        val doc = guest()
        CardRepository.validatePage("/me", doc)
        val home = CardHome.parse(doc) as CardHome.Guest
        assertEquals("我的票夹", home.title)
        assertEquals("还没有入场过的活动。", home.shelves.single().emptyText)
        assertTrue(home.shelves.single().tickets.isEmpty())
        rejects("/members/example", doc)
    }

    @Test fun keepsUpcomingAndPastTicketsSeparateWithNativeNavigation() {
        val doc = guest(tickets = row("redeemed", "/e/past"), upcoming = row("valid", "/e/upcoming-event"))
        CardRepository.validatePage("/me", doc)
        val home = CardHome.parse(doc) as CardHome.Guest
        assertEquals(listOf("待入场", "票夹"), home.shelves.map { it.title })
        val (upcoming, past) = home.shelves.map { it.tickets.single() }
        assertEquals("/e/upcoming-event", upcoming.eventPath)
        assertEquals("valid", upcoming.preview.attr("data-state"))
        assertEquals("/e/past", past.eventPath)
        assertEquals("redeemed", past.preview.attr("data-state"))
        assertEquals("活动", past.title)
        assertEquals("入场记录", past.note)
    }

    @Test fun doesNotUseUntrustedTicketDestinations() {
        val doc = guest(tickets = listOf("https://example.invalid/e/other", "/e/../me", "/e/", "/me").joinToString("") { row("valid", it) })
        val home = CardHome.parse(doc) as CardHome.Guest
        assertEquals(4, home.shelves.single().tickets.size)
        assertTrue(home.shelves.single().tickets.all { it.eventPath == null })
    }

    @Test fun retainsExistingMemberCardContract() {
        val doc = Jsoup.parse("""<main><article class="card full"></article></main>""")
        CardRepository.validatePage("/me", doc)
        CardRepository.validatePage("/members/example", doc)
        assertTrue(CardHome.parse(doc) is CardHome.Member)
    }

    @Test fun rejectsLoginForbiddenAndIncompleteGuestMarkup() {
        listOf(
            "<main><h1>登录</h1><form action='/login'></form></main>",
            "<main><h1>无权访问</h1></main>",
            "<header class='wrap guest'><h1>我的票夹</h1></header>",
            "<header class='wrap guest'><h1>我的票夹</h1></header><section class='wrap passes'></section>"
        ).forEach { html -> rejects("/me", Jsoup.parse(html)) }
    }

    private fun rejects(path: String, doc: org.jsoup.nodes.Document) {
        try {
            CardRepository.validatePage(path, doc)
            fail("Expected incompatible Card page for $path")
        } catch (_: IllegalStateException) {
            // Expected: the guest page is not a member profile, nor is a login page a wallet.
        }
    }
}
