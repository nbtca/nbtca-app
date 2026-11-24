package space.nbtca.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import space.nbtca.app.core.card.CardEventPage
import space.nbtca.app.ui.components.FrostedCard
import space.nbtca.app.ui.theme.AppThemeColors

internal fun LazyListScope.cardEventRows(rows: List<CardEventPage.Row>, navigate: (String) -> Unit) {
    items(rows) { event ->
        FrostedCard(onClick = event.eventPath?.let { path -> { navigate(path) } }) {
            event.preview?.let { TicketVisual(it, Modifier.fillMaxWidth(), compact = true) }
            Text(event.status, color = MaterialTheme.colorScheme.primary)
            Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp))
            Text(event.whenAndWhere, color = AppThemeColors.textSecondary)
        }
    }
}
