package space.nbtca.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.nbtca.app.core.model.NewsArticle
import space.nbtca.app.core.network.ApiClient
import space.nbtca.app.ui.components.CoverImage
import space.nbtca.app.ui.components.MarkdownText
import space.nbtca.app.ui.components.OfflineBanner
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    initialArticle: NewsArticle,
    apiClient: ApiClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var article by remember(initialArticle.id) { mutableStateOf(initialArticle) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadArticle() {
        if (article.id.isEmpty()) return
        isLoading = true
        error = null
        scope.launch {
            apiClient.getArticle(article.id).fold(
                onSuccess = { article = it },
                onFailure = { error = it.message }
            )
            isLoading = false
        }
    }

    LaunchedEffect(initialArticle.id) { loadArticle() }
    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(article.category, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { loadArticle() }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新正文")
                    }
                    IconButton(onClick = { openUrl(context, article.articleUrl) }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "在浏览器打开原文")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp)) }
            }
            if (article.coverUrl.isNotEmpty()) {
                item {
                    CoverImage(
                        url = article.coverUrl,
                        category = article.category,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        labelSize = 24
                    )
                }
            }
            item {
                // SelectionContainer：长按即可选中/复制正文与标题。
                androidx.compose.foundation.text.selection.SelectionContainer {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = AppColors.Primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = article.category,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = AppColors.Primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "  ${article.published}",
                            fontSize = 13.sp,
                            color = AppThemeColors.textSecondary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "作者: ${article.author}",
                            maxLines = 1,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppThemeColors.textSecondary
                        )
                    }

                    Text(
                        text = article.title,
                        modifier = Modifier.padding(top = 14.dp),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp,
                        color = AppThemeColors.textPrimary
                    )

                    if (article.tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            article.tags.forEach { tag ->
                                Surface(
                                    color = AppThemeColors.surfaceElevated,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "#$tag",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 11.sp,
                                        color = AppThemeColors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    error?.let { message ->
                        OfflineBanner(message = message, modifier = Modifier.padding(top = 14.dp))
                    }

                    Box(
                        modifier = Modifier
                            .padding(vertical = 18.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AppThemeColors.divider)
                    )

                    val body = article.contentMarkdown
                        ?: "## 摘要内容\n\n${article.summary}\n\n*正文暂时无法从 nbtca.space 获取，点击右上角按钮阅读完整图文。*"
                    MarkdownText(body, skipLeadingTitle = article.title)
                    Spacer(modifier = Modifier.height(48.dp))
                }
                }
            }
        }
    }
}

fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
