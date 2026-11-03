package space.nbtca.app.core.card

import org.jsoup.nodes.Element

data class MemberCardData(
    val name: String, val alias: String, val word: String, val section: String, val role: String,
    val joined: String, val github: String, val motto: String, val hue: Float,
    val background: String?, val portrait: String?, val path: String
) {
    companion object {
        fun parse(card: Element): MemberCardData {
            val details = card.select(".back dl > div").associate { it.selectFirst("dt")?.text().orEmpty() to it.selectFirst("dd")?.text().orEmpty() }
            val sub = card.selectFirst(".sub")?.text().orEmpty().split(" · ")
            val word = card.selectFirst(".word")?.text().orEmpty()
            return MemberCardData(card.selectFirst(".name")?.text().orEmpty(), details["代号"] ?: word,
                word, details["部门"] ?: sub.firstOrNull().orEmpty(), details["身份"] ?: sub.drop(1).joinToString(" · "),
                details["入会"] ?: card.selectFirst(".topline")?.text().orEmpty().substringAfter("Since ", ""),
                details["GitHub"].orEmpty().removePrefix("@"), card.selectFirst(".motto")?.text().orEmpty(),
                Regex("--hue:\\s*([\\d.]+)").find(card.attr("style"))?.groupValues?.get(1)?.toFloatOrNull() ?: 210f,
                card.selectFirst(".sky img")?.attr("src"), card.selectFirst(".subject img")?.attr("src"), card.attr("href"))
        }
    }
}
