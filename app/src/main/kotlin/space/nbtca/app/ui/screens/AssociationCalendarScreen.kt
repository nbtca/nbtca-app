package space.nbtca.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.nbtca.app.core.model.AssociationCalendar
import space.nbtca.app.core.model.AssociationEvent
import space.nbtca.app.core.network.ApiClient
import space.nbtca.app.core.util.formatChinaDateTime
import space.nbtca.app.core.util.htmlUnescape
import space.nbtca.app.core.util.withoutPlaceholders
import space.nbtca.app.ui.components.FrostedCard
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssociationCalendarScreen(
    calendar: AssociationCalendar?,
    apiClient: ApiClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var data by remember { mutableStateOf(calendar) }
    var isLoading by remember { mutableStateOf(calendar == null) }

    LaunchedEffect(calendar) {
        if (calendar == null) {
            isLoading = true
            data = apiClient.getAssociationCalendar().getOrNull()
            isLoading = false
        }
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("协会活动日历", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            data?.events.isNullOrEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Event,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = AppThemeColors.textSecondary
                )
                Text(
                    "近期暂无公开活动安排",
                    modifier = Modifier.padding(top = 12.dp),
                    color = AppThemeColors.textSecondary
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                data?.name?.takeIf { it.isNotBlank() }?.let { name ->
                    item {
                        Text(
                            text = name,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            fontSize = 13.sp,
                            color = AppThemeColors.textSecondary
                        )
                    }
                }
                // ics 的周期性活动会产生多条相同 UID，key 必须带下标，否则 Compose 会
                // 抛 "Key ... was already used" 直接崩溃；占位日程（「新建日程」）直接隐藏。
                itemsIndexed(
                    data?.events.orEmpty().withoutPlaceholders(),
                    key = { index, event -> "${event.id}#$index" }
                ) { _, event ->
                    EventCard(event) {
                        if (event.url.isNotBlank()) openUrl(context, event.url)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: AssociationEvent, onClick: () -> Unit) {
    FrostedCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = htmlUnescape(event.title),
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppThemeColors.textPrimary
            )
            if (event.url.isNotBlank()) {
                Icon(
                    Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (event.description.isNotBlank()) {
            Text(
                text = htmlUnescape(event.description),
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = AppThemeColors.textSecondary
            )
        }
        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(AppThemeColors.divider)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = AppColors.Primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = buildString {
                    append(formatChinaDateTime(event.start))
                    if (event.end != null) append(" — ").append(formatChinaDateTime(event.end))
                },
                fontSize = 12.sp,
                color = AppThemeColors.textSecondary
            )
        }
    }
}
