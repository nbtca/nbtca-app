package space.nbtca.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.nbtca.app.core.card.CardHome
import space.nbtca.app.ui.components.FrostedCard

internal fun LazyListScope.guestCardHome(home: CardHome.Guest, navigate: (String) -> Unit) {
    item {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(home.title, style = MaterialTheme.typography.headlineSmall)
            if (home.description.isNotBlank()) Text(home.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
    for (shelf in home.shelves) {
        item {
            Text(shelf.title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium)
        }
        if (shelf.tickets.isEmpty()) {
            shelf.emptyText?.let { empty -> item {
                FrostedCard { Text(empty, style = MaterialTheme.typography.bodyMedium) }
            } }
        }
        items(shelf.tickets) { ticket ->
            FrostedCard(onClick = ticket.eventPath?.let { path -> { navigate(path) } }) {
                TicketVisual(ticket.preview, Modifier.fillMaxWidth(), compact = true)
                if (ticket.title.isNotBlank()) Text(ticket.title, style = MaterialTheme.typography.titleMedium)
                if (ticket.note.isNotBlank()) Text(ticket.note, style = MaterialTheme.typography.bodyMedium)
                if (ticket.serial.isNotBlank()) Text(ticket.serial, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
