package space.nbtca.app.core.card

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal sealed interface CardHome {
    data class Member(val card: Element) : CardHome
    data class Guest(val title: String, val description: String, val shelves: List<Shelf>) : CardHome {
        data class Shelf(val title: String, val emptyText: String?, val tickets: List<Ticket>)
        data class Ticket(val preview: Element, val title: String, val note: String, val serial: String, val eventPath: String?)
    }

    companion object {
        private val eventPath = Regex("/e/[A-Za-z0-9_-]+")

        fun parse(doc: Document): CardHome? {
            doc.selectFirst(".card.full")?.let { return Member(it) }
            val header = doc.selectFirst("header.wrap.guest") ?: return null
            val title = header.selectFirst("h1")?.text()?.takeIf { it == "我的票夹" } ?: return null
            val wallet = header.nextElementSibling()?.takeIf {
                it.tagName() == "section" && it.hasClass("wrap") && it.hasClass("passes")
            } ?: return null
            val sections = wallet.select("section.shelf")
            if (sections.isEmpty()) return null
            val shelves = sections.map { section ->
                val heading = section.selectFirst("header h2")?.text()?.takeIf { it.isNotBlank() } ?: return null
                val list = section.selectFirst("ul") ?: return null
                val tickets = list.children().filter { it.tagName() == "li" }.map { row ->
                    val preview = row.selectFirst(".pass-card.thumb") ?: return null
                    val caption = row.selectFirst("p")
                    Guest.Ticket(
                        preview = preview,
                        title = caption?.selectFirst("b")?.text().orEmpty(),
                        note = caption?.selectFirst("span")?.text().orEmpty(),
                        serial = caption?.selectFirst("small")?.text().orEmpty(),
                        eventPath = preview.attr("href").takeIf(eventPath::matches)
                    )
                }
                Guest.Shelf(heading, section.children().firstOrNull { it.tagName() == "p" && it.hasClass("muted") }?.text(), tickets)
            }
            return Guest(title, header.selectFirst(".lede")?.text().orEmpty(), shelves)
        }
    }
}
