package space.nbtca.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NewsArticle(
    val id: String = "",
    val title: String = "",
    val category: String = "资讯",
    val published: String = "",
    val author: String = "NBTCA",
    val summary: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    @SerialName("article_url") val articleUrl: String = "",
    @SerialName("content_markdown") val contentMarkdown: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class NewsFeedResponse(
    val featured: List<NewsArticle> = emptyList(),
    val recent: List<NewsArticle> = emptyList(),
    val categories: List<String> = emptyList()
)

/** BFF 返回体（含离线兜底标记）。 */
data class NewsFeedResult(
    val featured: List<NewsArticle> = emptyList(),
    val recent: List<NewsArticle> = emptyList(),
    val categories: List<String> = emptyList(),
    val isOffline: Boolean = false
) {
    val isEmpty: Boolean get() = featured.isEmpty() && recent.isEmpty()
}
