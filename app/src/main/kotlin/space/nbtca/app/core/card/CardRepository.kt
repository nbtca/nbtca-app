package space.nbtca.app.core.card

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class CardClaimField(val name: String, val label: String, val value: String, val required: Boolean, val maxLength: Int, val hidden: Boolean)

/** Compatibility adapter for the maintainer's existing website/HTTP contract. */
object CardRepository {
    private val labels = mapOf("name" to "姓名", "student_id" to "学号", "class_name" to "班级", "phone" to "手机号")
    suspend fun load(path: String): Document = CardClient.page(path).also { validatePage(path,it) }
    suspend fun saveProfile(fields: Map<String,String>): Document = CardClient.page("/me",fields).also {
        check(CardHome.parse(it) is CardHome.Member) { "只有社员卡可以编辑" }
        check(it.select(".notice").any { node -> node.text().contains("已保存") }) { "服务端未确认保存，请刷新查看" }
    }
    suspend fun claim(path: String, fields: Map<String,String>): Document = CardClient.page(path,fields).also {
        validatePage(path,it)
        claimError(it)?.let { message -> throw IllegalStateException(message) }
    }
    fun validatePage(path: String, doc: Document) {
        val supported = when {
            path == "/me" -> CardHome.parse(doc) != null
            path.startsWith("/members/") -> doc.selectFirst(".card.full") != null
            path == "/members" -> doc.selectFirst("main ul") != null
            path == "/e" -> CardEventPage.parse(doc) != null
            path.startsWith("/e/") -> doc.selectFirst(".info h1") != null && doc.selectFirst(".pass-card") != null
            else -> false
        }
        check(supported) { "Card 页面结构已变化，请更新 App 后重试" }
    }
    fun checkInCode(card: Element): Element? {
        if (!card.hasClass("full") || card.attr("data-state") != "valid") return null
        return card.selectFirst(".ticket[data-ticket][data-check-in=open] > .under > svg.qr")
    }

    fun claimFields(doc: Document): List<CardClaimField> = doc.select("form.claim input[name]").map { input ->
        val name=input.attr("name")
        val hidden=input.attr("type")=="hidden"
        check(hidden || name in labels) { "活动增加了未支持的报名字段，请在官方网页领取" }
        CardClaimField(name,labels[name] ?: name,input.`val`(),input.hasAttr("required"),input.attr("maxlength").toIntOrNull()?.coerceIn(1,200) ?: 200,hidden)
    }
    fun claimError(doc: Document): String? {
        doc.selectFirst("form.claim .error")?.text()?.takeIf { it.isNotBlank() }?.let {return it}
        val state=doc.selectFirst(".pass-card.full")?.attr("data-state")
        return if (state in listOf("valid","redeemed","expired")) null
            else doc.selectFirst(".act .notice")?.text()?.takeIf {it.isNotBlank()} ?: "活动券尚未领取成功，请检查信息或名额后重试"
    }
}
