package space.nbtca.app.core.card

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal data class CardEventPage(val upcoming: List<Row>, val pastTitle: String?, val past: List<Row>) {
    data class Row(val preview: Element?, val eventPath: String?, val status: String, val title: String, val whenAndWhere: String)

    companion object {
        fun parse(doc: Document): CardEventPage? {
            val main = doc.selectFirst("main") ?: return null
            if (main.selectFirst("h1")?.text() != "活动") return null
            val pastSection = main.children().firstOrNull {
                it.tagName() == "section" && it.selectFirst("header > h2")?.text() == "往期活动"
            }
            return CardEventPage(
                upcoming = rows(main.children().firstOrNull { it.tagName() == "ul" }),
                pastTitle = pastSection?.selectFirst("h2")?.text(),
                past = rows(pastSection?.children()?.firstOrNull { it.tagName() == "ul" })
            )
        }

        private fun rows(list: Element?): List<Row> = list?.children().orEmpty().mapNotNull { row ->
            val caption = row.selectFirst(".caption") ?: return@mapNotNull null
            val title = caption.selectFirst("h3")?.text() ?: return@mapNotNull null
            Row(
                preview = row.selectFirst(".pass-card.thumb"),
                eventPath = caption.selectFirst("a[href]")?.attr("href")?.let(::cardEventPath),
                status = caption.selectFirst(".chip")?.text().orEmpty(),
                title = title,
                whenAndWhere = caption.selectFirst("p")?.wholeText()
                    ?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString("\n").orEmpty()
            )
        }
    }
}

private val eventDetailPath = Regex("/e/[A-Za-z0-9_-]+")
internal fun cardEventPath(href: String): String? =
    href.takeIf { it != "/e/new" && eventDetailPath.matches(it) }
