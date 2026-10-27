package space.nbtca.app.core.parse

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI

data class ParsedArticle(
    val coverUrl: String,
    val markdown: String
)

/**
 * 把文章页的 <article> 转成 Markdown，替代旧 BFF 的 /newsroom/articles/{id}。
 * 只保留正文语义，丢弃脚本、导航、表单等；页面结构变化时尽量退化为纯文本。
 */
object ArticleParser {
    fun parse(html: String, baseUrl: String): ParsedArticle {
        val document = Jsoup.parse(html, baseUrl)
        val container = document.selectFirst("article")
            ?: document.selectFirst("main")
            ?: document.body()
            ?: return ParsedArticle("", "")

        var cover = findCover(document, baseUrl)
        if (cover.isBlank()) cover = findFirstImage(container, baseUrl)

        val renderer = MarkdownRenderer(baseUrl)
        renderer.renderChildren(container)
        return ParsedArticle(cover, cleanup(renderer.toString()))
    }

    /**
     * 只取封面，用于列表页补全。文章页正文可能 200KB 且内联 base64 封面，
     * 这里只解析 <head>，避免为列表构建整篇 DOM。
     */
    fun parseCover(html: String, baseUrl: String): String {
        if (html.isBlank()) return ""
        val headEnd = html.indexOf("</head>", ignoreCase = true)
        val head = if (headEnd >= 0) html.substring(0, headEnd) else html.substring(0, minOf(html.length, 96 * 1024))
        return findCover(Jsoup.parse(head, baseUrl), baseUrl)
    }

    private fun findCover(document: Document, baseUrl: String): String {
        val selectors = listOf(
            "meta[property=og:image]",
            "meta[name=og:image]",
            "meta[name=twitter:image]",
            "link[rel=image_src]"
        )
        selectors.forEach { selector ->
            val element = document.selectFirst(selector) ?: return@forEach
            val raw = if (element.tagName().equals("link", true)) {
                element.attr("href")
            } else {
                element.attr("content")
            }
            if (raw.isNotBlank()) return resolve(baseUrl, raw)
        }
        return ""
    }

    private fun findFirstImage(container: Element, baseUrl: String): String {
        val image = container.selectFirst("img") ?: return ""
        val src = image.attr("src").ifBlank { image.attr("data-src") }
        return resolve(baseUrl, src)
    }

    private fun resolve(baseUrl: String, raw: String): String {
        val ref = raw.trim()
        if (ref.isEmpty()) return ""
        if (ref.startsWith("data:", ignoreCase = true)) return ref
        val resolved = runCatching { URI(baseUrl).resolve(ref).toString() }.getOrDefault(ref)
        return encodeNonAscii(resolved)
    }

    /**
     * Go 的 url.URL.String() 会把路径/查询/片段里的非 ASCII 字符转成 UTF-8 百分号编码；
     * Java 的 URI.toString() 则原样保留，这里补齐差异，保证与旧 BFF 的 Markdown 一致。
     */
    private fun encodeNonAscii(value: String): String {
        var index = 0
        val builder = StringBuilder(value.length)
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val count = Character.charCount(codePoint)
            if (codePoint in 0x20..0x7E) {
                builder.append(value, index, index + count)
            } else {
                val bytes = String(value.toCharArray(), index, count).toByteArray(Charsets.UTF_8)
                for (byte in bytes) {
                    builder.append('%').append("%02X".format(byte.toInt() and 0xFF))
                }
            }
            index += count
        }
        return builder.toString()
    }

    private fun cleanup(markdown: String): String =
        markdown.replace(Regex("[ \t]+\n"), "\n").trim()

    private class MarkdownRenderer(private val baseUrl: String) {
        private val builder = StringBuilder()
        private var lastChar: Char? = null
        private var trailingNewlines = 0

        override fun toString(): String = builder.toString()

        fun renderChildren(node: Node) {
            node.childNodes().forEach(::renderNode)
        }

        private fun renderNode(node: Node) {
            when (node) {
                is TextNode -> renderText(node.wholeText)
                is Element -> renderElement(node)
                else -> Unit
            }
        }

        private fun renderElement(element: Element) {
            val tag = element.tagName().lowercase()
            when (tag) {
                "script", "style", "nav", "header", "footer", "noscript",
                "svg", "form", "button" -> return
                "h1", "h2", "h3", "h4" -> {
                    val level = tag[1].digitToInt()
                    ensureNewlines(2)
                    write("#".repeat(level))
                    write(" ")
                    renderChildren(element)
                    ensureNewlines(2)
                }
                "p" -> {
                    ensureNewlines(2)
                    renderChildren(element)
                    ensureNewlines(2)
                }
                "br" -> write("  \n")
                "strong", "b" -> {
                    write("**")
                    renderChildren(element)
                    write("**")
                }
                "em", "i" -> {
                    write("*")
                    renderChildren(element)
                    write("*")
                }
                "code" -> renderInlineCode(element)
                "pre" -> renderCodeBlock(element)
                "blockquote" -> renderBlockquote(element)
                "ul", "ol" -> renderList(element, ordered = tag == "ol", depth = 0)
                "img" -> renderImage(element)
                "a" -> renderLink(element)
                "hr" -> {
                    ensureNewlines(2)
                    write("---")
                    ensureNewlines(2)
                }
                "figure", "figcaption", "table", "thead", "tbody", "tr", "td", "th" -> {
                    ensureNewlines(2)
                    renderChildren(element)
                    ensureNewlines(2)
                }
                else -> renderChildren(element)
            }
        }

        private fun renderText(raw: String) {
            if (raw.isBlank()) return
            val core = raw.trim().replace(Regex("\\s+"), " ")
            if (core.isEmpty()) return
            if (raw.first().isWhitespace()) ensureSpace()
            write(core)
            if (raw.last().isWhitespace()) write(" ")
        }

        private fun renderInlineCode(element: Element) {
            val text = element.text().trim()
            if (text.isEmpty()) return
            write("`")
            write(text)
            write("`")
        }

        private fun renderCodeBlock(element: Element) {
            ensureNewlines(2)
            val code = element.selectFirst("code")
            val language = code?.className()?.split(Regex("\\s+"))
                ?.firstOrNull { it.startsWith("language-") || it.startsWith("lang-") }
                ?.substringAfter('-')
                .orEmpty()
            val text = extractText(element).trim('\n')
            write("```" + language + "\n")
            write(text)
            ensureNewlines(1)
            write("```")
            ensureNewlines(2)
        }

        private fun renderBlockquote(element: Element) {
            val inner = MarkdownRenderer(baseUrl)
            inner.renderChildren(element)
            val text = inner.toString().trim()
            if (text.isEmpty()) return
            ensureNewlines(2)
            text.split('\n').forEach { line ->
                if (line.isBlank()) {
                    write(">\n")
                } else {
                    write("> ")
                    write(line)
                    write("\n")
                }
            }
            ensureNewlines(2)
        }

        private fun renderList(element: Element, ordered: Boolean, depth: Int) {
            ensureNewlines(2)
            var index = 0
            element.children().forEach { child ->
                if (!child.tagName().equals("li", true)) return@forEach
                index += 1
                ensureNewlines(1)
                write("  ".repeat(depth))
                if (ordered) {
                    write("$index. ")
                } else {
                    write("- ")
                }
                child.childNodes().forEach { item ->
                    if (item is Element && isListTag(item.tagName())) return@forEach
                    renderNode(item)
                }
                ensureNewlines(1)
                child.childNodes().forEach { item ->
                    if (item is Element && isListTag(item.tagName())) {
                        renderList(item, item.tagName().equals("ol", true), depth + 1)
                    }
                }
            }
            ensureNewlines(2)
        }

        private fun renderImage(element: Element) {
            val src = element.absUrl("src").ifBlank { element.attr("src") }
                .ifBlank { element.attr("data-src") }
            if (src.isBlank()) return
            val alt = element.attr("alt").trim()
            write("![")
            write(alt)
            write("](")
            write(resolve(baseUrl, src))
            write(")")
        }

        private fun renderLink(element: Element) {
            val href = element.absUrl("href").ifBlank { element.attr("href") }.trim()
            if (href.isEmpty() || href.startsWith("javascript:", ignoreCase = true)) {
                renderChildren(element)
                return
            }
            write("[")
            renderChildren(element)
            write("](")
            write(resolve(baseUrl, href))
            write(")")
        }

        private fun extractText(node: Node): String {
            val result = StringBuilder()
            fun walk(current: Node) {
                when (current) {
                    is TextNode -> result.append(current.wholeText)
                    is Element -> {
                        if (current.tagName().equals("br", true)) {
                            result.append('\n')
                        } else {
                            current.childNodes().forEach { walk(it) }
                        }
                    }
                }
            }
            walk(node)
            return result.toString()
        }

        private fun write(value: String) {
            if (value.isEmpty()) return
            builder.append(value)
            lastChar = value.last()
            if (lastChar == '\n') {
                var count = 0
                for (index in value.indices.reversed()) {
                    if (value[index] == '\n') count++ else break
                }
                trailingNewlines = if (count == value.length) trailingNewlines + count else count
            } else {
                trailingNewlines = 0
            }
        }

        private fun ensureNewlines(count: Int) {
            while (trailingNewlines < count) write("\n")
        }

        private fun ensureSpace() {
            if (builder.isEmpty()) return
            if (lastChar == ' ' || lastChar == '\n' || lastChar == '\t') return
            write(" ")
        }

        private fun isListTag(tag: String) = tag.equals("ul", true) || tag.equals("ol", true)
    }
}
