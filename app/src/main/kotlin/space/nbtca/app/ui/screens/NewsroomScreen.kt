package space.nbtca.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.nbtca.app.core.model.NewsArticle
import space.nbtca.app.core.model.NewsFeedResult
import space.nbtca.app.core.network.ApiClient
import space.nbtca.app.ui.components.NewsroomCard
import space.nbtca.app.ui.components.OfflineBanner
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsroomScreen(apiClient: ApiClient) {
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf(NewsFeedResult()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf("全部") }
    var categories by remember { mutableStateOf(listOf("全部")) }
    var openedArticle by remember { mutableStateOf<NewsArticle?>(null) }
    // 保留列表滚动位置：进入文章再返回时停在原处，而不是跳回顶部。
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    fun load() {
        isLoading = true
        scope.launch {
            val data = apiClient.getNewsFeed()
            val list = mutableListOf("全部")
            data.categories.filter { it.isNotEmpty() }.forEach { if (!list.contains(it)) list.add(it) }
            (data.featured + data.recent).forEach { article ->
                if (article.category.isNotEmpty() && !list.contains(article.category)) list.add(article.category)
            }
            feed = data
            categories = list
            if (!list.contains(selectedCategory)) selectedCategory = "全部"
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val opened = openedArticle

    fun filter(list: List<NewsArticle>): List<NewsArticle> =
        if (selectedCategory == "全部") list
        else list.filter { it.category == selectedCategory || it.tags.contains(selectedCategory) }

    val featured = filter(feed.featured).distinctBy { it.id }
    val recent = filter(feed.recent)
        .filterNot { article -> featured.any { it.id == article.id } }
        .distinctBy { it.id }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("拔电关机", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "To be at the intersection of technology and liberal arts.",
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            color = AppThemeColors.textSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新资讯")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            state = listState
        ) {
            if (feed.isOffline) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OfflineBanner("当前显示离线缓存内容。启动 Go BFF 或检查网络后下拉刷新。")
                    }
                }
            }

            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        val selected = category == selectedCategory
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = category },
                            label = {
                                Text(
                                    text = category,
                                    color = if (selected) Color.White else AppThemeColors.textPrimary,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = AppThemeColors.surface,
                                selectedContainerColor = AppColors.Primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = AppThemeColors.divider,
                                selectedBorderColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            if (featured.isEmpty() && recent.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Article,
                            contentDescription = null,
                            modifier = Modifier.height(56.dp),
                            tint = Color.Gray.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "暂无「$selectedCategory」分类相关文章",
                            modifier = Modifier.padding(top = 12.dp),
                            color = AppThemeColors.textSecondary,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            if (featured.isNotEmpty()) {
                item { SectionLabel("精选焦点") }
                itemsIndexed(featured, key = { index, a -> "f-${a.id}#$index" }) { _, article ->
                    NewsroomCard(article, isLargeFeatured = true) { openedArticle = article }
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionLabel("更多文章") }
                itemsIndexed(recent, key = { index, a -> "r-${a.id}#$index" }) { _, article ->
                    NewsroomCard(article, isLargeFeatured = false) { openedArticle = article }
                }
            }
        }
    }

    // 文章详情以覆盖层展示：底层列表不销毁，返回时自然停在原位置。
    if (opened != null) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ArticleDetailScreen(
                initialArticle = opened,
                apiClient = apiClient,
                onBack = { openedArticle = null }
            )
        }
    }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = AppThemeColors.textSecondary
    )
}
