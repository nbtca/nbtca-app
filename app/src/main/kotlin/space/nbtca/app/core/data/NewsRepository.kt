package space.nbtca.app.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import space.nbtca.app.core.model.NewsArticle
import space.nbtca.app.core.model.NewsFeedResult
import space.nbtca.app.core.network.ApiException
import space.nbtca.app.core.network.NbtcaEndpoints
import space.nbtca.app.core.network.NbtcaHttp
import space.nbtca.app.core.parse.ArticleParser
import space.nbtca.app.core.parse.RssFeed
import space.nbtca.app.core.parse.RssParser
import java.io.IOException

@Serializable
private data class FeedSnapshot(
    val featured: List<NewsArticle> = emptyList(),
    val recent: List<NewsArticle> = emptyList(),
    val categories: List<String> = emptyList(),
    val all: List<NewsArticle> = emptyList()
)

/**
 * 资讯直连：RSS 列表 + 文章页 HTML 转 Markdown。
 *
 * RSS 不带封面，BFF 当年是在服务端预抓每篇文章的 og:image 再内联进 feed；
 * 现在由客户端补这一步：只为缺少封面的文章并发抓一次文章页 <head>，
 * 结果按文章 id 落缓存（data URI 解码成 file://，Coil 只能加载后者），30 天内不再重复抓。
 */
class NewsRepository(
    private val http: OkHttpClient,
    private val cache: DiskCache,
    private val coverStore: CoverStore
) {
    private val mutex = Mutex()

    @Volatile
    private var memory: FeedSnapshot? = null

    @Volatile
    private var memoryAt = 0L

    suspend fun getFeed(force: Boolean = false): NewsFeedResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!force) {
                memory?.let { snapshot ->
                    if (now - memoryAt < FEED_TTL_MS) return@withLock snapshot.toResult(isOffline = false)
                }
                cache.read(FEED_CACHE_KEY, FeedSnapshot.serializer(), FEED_TTL_MS)?.let { snapshot ->
                    memory = snapshot
                    memoryAt = now
                    return@withLock snapshot.toResult(isOffline = false)
                }
            }

            try {
                val xml = NbtcaHttp.getText(http, NbtcaEndpoints.RSS, "application/rss+xml,text/xml,*/*")
                val feed = RssParser.parse(xml)
                val snapshot = feed.toSnapshot(enrichCovers(feed.all))
                cache.write(FEED_CACHE_KEY, FeedSnapshot.serializer(), snapshot)
                memory = snapshot
                memoryAt = now
                snapshot.toResult(isOffline = false)
            } catch (_: Exception) {
                val stale = cache.readStale(FEED_CACHE_KEY, FeedSnapshot.serializer())
                if (stale != null) {
                    memory = stale
                    memoryAt = 0L
                    stale.toResult(isOffline = true)
                } else {
                    offlineNewsFeed()
                }
            }
        }
    }

    suspend fun getArticle(id: String): Result<NewsArticle> = withContext(Dispatchers.IO) {
        if (id.isBlank()) return@withContext Result.failure(ApiException("文章 id 为空", null))

        cache.read("article:$id", NewsArticle.serializer(), ARTICLE_TTL_MS)
            ?.takeIf { !it.contentMarkdown.isNullOrBlank() }
            ?.let { return@withContext Result.success(it) }

        val feed = memory ?: getFeed().let { result ->
            if (result.isOffline && result.featured.isEmpty() && result.recent.isEmpty()) null else result.toSnapshot()
        }
        val known = feed?.all?.firstOrNull { it.id == id }
        val articleUrl = known?.articleUrl?.takeIf { it.isNotBlank() }
            ?: NbtcaEndpoints.articleUrl(id)

        try {
            val html = NbtcaHttp.getText(http, articleUrl, ARTICLE_ACCEPT)
            val parsed = ArticleParser.parse(html, articleUrl)
            val markdown = parsed.markdown.ifBlank {
                fallbackMarkdown(known ?: NewsArticle(id = id, articleUrl = articleUrl))
            }
            val rawCover = parsed.coverUrl.ifBlank { known?.coverUrl.orEmpty() }
            val article = (known ?: NewsArticle(id = id, articleUrl = articleUrl)).copy(
                articleUrl = articleUrl,
                coverUrl = coverStore.materialize(id, rawCover),
                contentMarkdown = markdown
            )
            cache.write("article:$id", NewsArticle.serializer(), article)
            Result.success(article)
        } catch (error: Exception) {
            val stale = cache.readStale("article:$id", NewsArticle.serializer())
            if (stale != null) {
                Result.success(stale)
            } else {
                val message = when (error) {
                    is IOException -> "文章加载失败，请检查网络后重试"
                    else -> error.message ?: "文章加载失败"
                }
                Result.failure(ApiException(message, null))
            }
        }
    }

    /**
     * 为列表补封面。RSS 的 14 条里只有部分带 og:image，逐条抓取否则不行；
     * OkHttp 的 Dispatcher 本身按 host 限流（默认 5 并发），这里直接全部 async 即可。
     */
    private suspend fun enrichCovers(articles: List<NewsArticle>): List<NewsArticle> = coroutineScope {
        articles.map { article ->
            async(Dispatchers.IO) {
                if (coverStore.isUsable(article.coverUrl)) return@async article
                val cached = cache.read(coverKey(article.id), String.serializer(), COVER_TTL_MS)
                if (cached != null && coverStore.isUsable(cached)) {
                    return@async article.copy(coverUrl = cached)
                }
                val cover = coverStore.materialize(article.id, fetchCover(article))
                if (cover.isNotBlank()) {
                    cache.write(coverKey(article.id), String.serializer(), cover)
                }
                article.copy(coverUrl = cover)
            }
        }.awaitAll()
    }

    private suspend fun fetchCover(article: NewsArticle): String {
        val url = article.articleUrl.takeIf { it.isNotBlank() } ?: NbtcaEndpoints.articleUrl(article.id)
        return runCatching {
            val html = NbtcaHttp.getText(http, url, ARTICLE_ACCEPT)
            ArticleParser.parseCover(html, url)
        }.getOrDefault("")
    }

    private fun coverKey(id: String) = "cover:$id"

    private fun RssFeed.toSnapshot(enriched: List<NewsArticle>): FeedSnapshot {
        val byId = enriched.associateBy { it.id }
        return FeedSnapshot(
            featured = featured.map { byId[it.id] ?: it },
            recent = recent.map { byId[it.id] ?: it },
            categories = categories,
            all = enriched
        )
    }

    private fun FeedSnapshot.toResult(isOffline: Boolean) = NewsFeedResult(
        featured = featured,
        recent = recent,
        categories = categories,
        isOffline = isOffline
    )

    private fun NewsFeedResult.toSnapshot(): FeedSnapshot? {
        if (featured.isEmpty() && recent.isEmpty()) return null
        val all = (featured + recent).distinctBy { it.id }
        return FeedSnapshot(featured, recent, categories, all)
    }

    private fun fallbackMarkdown(article: NewsArticle): String = buildString {
        if (article.title.isNotBlank()) {
            append("## ").append(article.title).append("\n\n")
        }
        if (article.summary.isNotBlank()) {
            append(article.summary).append("\n\n")
        }
        if (article.articleUrl.isNotBlank()) {
            append("> 正文抓取失败，请访问 [原文链接](").append(article.articleUrl).append(") 阅读完整内容。\n")
        }
        if (isEmpty()) append("正文暂时无法获取，请稍后重试。")
    }.trim()

    private fun offlineNewsFeed(): NewsFeedResult = NewsFeedResult(
        isOffline = true,
        categories = listOf("技术", "活动"),
        featured = listOf(
            NewsArticle(
                id = "PhoNBT2024",
                title = "影留宁理 - 2024年度摄影展与毕业季活动回顾",
                category = "活动",
                published = "2024 年 6 月 18 日",
                author = "NBTCA 媒体部",
                summary = "光影定格青春，计算机协会联合摄影爱好者呈现宁理校园夏日记忆纪实。",
                coverUrl = "https://oss.nbtca.space/blog/clas/YQL05614-5xNq3t-mid-ulLkGV.jpeg",
                articleUrl = "https://nbtca.space/posts/PhoNBT2024",
                tags = listOf("摄影展", "毕业季", "校园生活")
            )
        ),
        recent = listOf(
            NewsArticle(
                id = "go-struct-craft-1",
                title = "Go 结构体精要 1：空结构体设计",
                category = "技术",
                published = "2026 年 3 月 18 日",
                author = "Dev Team",
                summary = "深入解析 struct{} 的零内存开销特性在信号通道、集合 Set 以及锁字段中的实战用法。",
                coverUrl = "https://oss.nbtca.space/blog/clas/image-20220419101405456-wHMqwe.jpeg",
                articleUrl = "https://nbtca.space/posts/blogs/Tech/GO/go-struct-craft-1-empty-struct",
                tags = listOf("Golang", "性能优化")
            )
        )
    )

    companion object {
        private const val FEED_CACHE_KEY = "news-feed"
        private const val FEED_TTL_MS = 10 * 60 * 1000L
        private const val ARTICLE_TTL_MS = 7 * 24 * 60 * 60 * 1000L
        private const val COVER_TTL_MS = 30L * 24 * 60 * 60 * 1000L
        private const val ARTICLE_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    }
}
