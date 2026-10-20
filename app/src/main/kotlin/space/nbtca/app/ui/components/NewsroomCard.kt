package space.nbtca.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import space.nbtca.app.core.model.NewsArticle
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors
import space.nbtca.app.ui.theme.LocalIsDark

@Composable
fun NewsroomCard(
    article: NewsArticle,
    isLargeFeatured: Boolean,
    onClick: () -> Unit
) {
    val isDark = LocalIsDark.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppThemeColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            CoverImage(
                url = article.coverUrl,
                category = article.category,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (isLargeFeatured) 16f / 10f else 16f / 9f),
                labelSize = if (isLargeFeatured) 22 else 18
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = article.category.uppercase(),
                        color = AppColors.Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = AppThemeColors.textSecondary
                        )
                        Text(
                            text = "  ${article.published}",
                            color = AppThemeColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Text(
                    text = article.title,
                    modifier = Modifier.padding(top = 8.dp),
                    color = AppThemeColors.textPrimary,
                    fontSize = if (isLargeFeatured) 20.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (if (isLargeFeatured) 26 else 22).sp,
                    letterSpacing = (-0.3).sp
                )

                if (article.summary.isNotEmpty()) {
                    Text(
                        text = article.summary,
                        modifier = Modifier.padding(top = 8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = AppThemeColors.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
