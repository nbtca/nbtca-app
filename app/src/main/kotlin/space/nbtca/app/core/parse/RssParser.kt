package space.nbtca.app.core.parse

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.w3c.dom.Element
import space.nbtca.app.core.model.NewsArticle
import space.nbtca.app.core.network.NbtcaEndpoints
import java.io.StringReader
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/** RSS 解析后的完整快照；all 用于文章详情按 id 查找。 */
data class RssFeed(
    val featured: List<NewsArticle>,
    val recent: List<NewsArticle>,
    val categories: List<String>,
    val all: List<NewsArticle>
)

/**
 * 直接解析 nbtca.space/rss.xml，替代旧 BFF 的 /newsroom/feed。
 * RSS 本身不带封面，移动端不再像服务端那样预抓 14 篇文章；封面在文章详情页再取。
 */
object RssParser {
    private val china = ZoneId.of("Asia/Shanghai")
    private val publishedFormat = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA)

    private val dateFormats = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
    )

    private data class Item(val article: NewsArticle, val published: Instant?)

    fun parse(xml: String, siteBase: String = NbtcaEndpoints.SPACE_BASE): RssFeed {
        val document = parseXml(xml)
        val channel = document.getElementsByTagName("channel").item(0) as? Element
            ?: throw IllegalArgumentException("RSS 缺少 channel")
        val itemNodes = channel.getElementsByTagName("item")
        val items = buildList {
            for (index in 0 until itemNodes.length) {
                val element = itemNodes.item(index) as? Element ?: continue
                parseItem(element, siteBase)?.let { add(it) }
            }
        }.sortedWith(
            Comparator { left, right ->
                when {
                    left.published == null && right.published == null -> 0
                    left.published == null -> 1
                    right.published == null -> -1
                    else -> right.published.compareTo(left.published)
                }
            }
        )

        val articles = items.map { it.article }
        val categories = linkedSetOf<String>()
        articles.forEach { article ->
            if (article.category.isNotBlank()) categories.add(article.category)
        }

        val featuredCount = minOf(2, articles.size)
        val recentEnd = minOf(articles.size, featuredCount + 12)
        return RssFeed(
            featured = articles.take(featuredCount),
            recent = articles.subList(featuredCount, recentEnd),
            categories = categories.toList(),
            all = articles
        )
    }

    private fun parseItem(item: Element, siteBase: String): Item? {
        val link = directText(item, "link").ifBlank { directText(item, "guid") }
        val absoluteLink = absoluteUrl(siteBase, link)
        val id = extractArticleId(absoluteLink)
            .ifBlank { extractArticleId(directText(item, "guid")) }
        if (id.isBlank()) return null

        val categories = directChildren(item, "category").mapNotNull { clean(it.textContent) }
            .filter { it.isNotBlank() }
        val published = parseDate(directText(item, "pubDate"))
        val author = clean(directText(item, "author"))
            .ifBlank { clean(directText(item, "creator")) }
            .ifBlank { "NBTCA" }
        val title = clean(Parser.unescapeEntities(directText(item, "title"), false))
        val summary = clean(Jsoup.parse(directText(item, "description")).text())
        val category = categories.firstOrNull() ?: "资讯"

        val article = NewsArticle(
            id = id,
            title = title,
            category = category,
            published = published?.atZone(china)?.format(publishedFormat)
                ?: clean(directText(item, "pubDate")),
            author = author,
            summary = summary,
            coverUrl = "",
            articleUrl = absoluteLink,
            contentMarkdown = null,
            tags = categories
        )
        return Item(article, published)
    }

    private fun parseXml(xml: String) = run {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
        }
        runCatching {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }

    private fun directChildren(parent: Element, name: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = parent.childNodes
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            if (element.localName.equals(name, true) || element.tagName.equals(name, true)) {
                result.add(element)
            }
        }
        return result
    }

    private fun directText(parent: Element, name: String): String =
        directChildren(parent, name).firstOrNull()?.textContent?.trim().orEmpty()

    private fun extractArticleId(link: String): String {
        if (link.isBlank()) return ""
        val path = runCatching { URI(link).path }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: link
        val marker = "/posts/"
        val index = path.indexOf(marker)
        val tail = if (index >= 0) path.substring(index + marker.length) else path.trimStart('/')
        return tail.trim('/')
    }

    private fun absoluteUrl(base: String, ref: String): String {
        val value = ref.trim()
        if (value.isEmpty()) return ""
        val parsed = runCatching { URI(value) }.getOrNull()
        if (parsed != null && parsed.isAbsolute) return value
        return runCatching { URI(base.trimEnd('/') + "/").resolve(value).toString() }
            .getOrElse { value }
    }

    private fun parseDate(raw: String): Instant? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        dateFormats.forEach { format ->
            runCatching { ZonedDateTime.parse(value, format).toInstant() }.getOrNull()?.let { return it }
            runCatching { OffsetDateTime.parse(value, format).toInstant() }.getOrNull()?.let { return it }
            runCatching {
                LocalDateTime.parse(value, format).atZone(china).toInstant()
            }.getOrNull()?.let { return it }
            runCatching {
                LocalDate.parse(value, format).atStartOfDay(china).toInstant()
            }.getOrNull()?.let { return it }
        }
        return runCatching { Instant.parse(value) }.getOrNull()
    }

    private fun clean(raw: String): String =
        Parser.unescapeEntities(raw, false)
            .replace(Regex("[\t\r\n]+"), " ")
            .replace(Regex(" {2,}"), " ")
            .trim()
}
