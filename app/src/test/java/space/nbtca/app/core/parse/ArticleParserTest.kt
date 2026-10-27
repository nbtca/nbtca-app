package space.nbtca.app.core.parse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleParserTest {
    @Test
    fun convertsArticleHtmlToMarkdown() {
        val parsed = ArticleParser.parse(
            fixture("article.html"),
            "https://nbtca.space/posts/prompt-strategies/"
        )

        assertTrue("cover should be extracted", parsed.coverUrl.isNotBlank())
        assertTrue(parsed.markdown.isNotBlank())
        assertTrue(parsed.markdown.contains("提示词的艺术"))
        assertTrue(parsed.markdown.contains("七个策略") || parsed.markdown.contains("策略"))
        assertTrue(parsed.markdown.contains("```"))
        assertTrue(parsed.markdown.contains("]("))
        assertFalse(parsed.markdown.contains("<script"))
        assertFalse(parsed.markdown.contains("<p>"))
    }

    @Test
    fun parseCoverReadsOnlyTheHead() {
        val cover = ArticleParser.parseCover(
            fixture("article.html"),
            "https://nbtca.space/posts/prompt-strategies/"
        )

        assertTrue("og:image data URI should be kept as-is", cover.startsWith("data:image/png;base64,"))
    }

    @Test
    fun parseCoverResolvesRelativeUrls() {
        val html = "<html><head><meta property=\"og:image\" content=\"/img/cover.png\"></head>" +
            "<body><article><img src=\"/body-only.png\"></article></body></html>"

        assertEquals(
            "https://nbtca.space/img/cover.png",
            ArticleParser.parseCover(html, "https://nbtca.space/posts/demo/")
        )
    }

    @Test
    fun parseCoverReturnsEmptyWhenHeadHasNoImage() {
        val html = "<html><head><title>t</title></head><body><article><img src=\"/x.png\"></article></body></html>"

        assertTrue(ArticleParser.parseCover(html, "https://nbtca.space/posts/demo/").isEmpty())
    }

    @Test
    fun matchesBffGoldenMarkdown() {
        val expected = goldenMarkdown()
        val actual = ArticleParser.parse(
            fixture("article.html"),
            "https://nbtca.space/posts/prompt-strategies/"
        ).markdown

        if (actual != expected) {
            val index = firstDifference(actual, expected)
            val from = (index - 60).coerceAtLeast(0)
            val expectedSlice = expected.substring(from, (index + 120).coerceAtMost(expected.length))
            val actualSlice = actual.substring(from, (index + 120).coerceAtMost(actual.length))
            throw AssertionError(
                "markdown differs at index $index\nexpected: $expectedSlice\nactual:   $actualSlice"
            )
        }
    }

    private fun goldenMarkdown(): String =
        Json.parseToJsonElement(fixture("bff-article.json"))
            .jsonObject["data"]!!.jsonObject["content_markdown"]!!.jsonPrimitive.content

    private fun firstDifference(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        for (i in 0 until limit) if (a[i] != b[i]) return i
        return limit
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }
            .readText(Charsets.UTF_8)
}
