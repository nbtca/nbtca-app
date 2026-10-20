package space.nbtca.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.nbtca.app.ui.theme.AppColors

fun statusColor(status: String): Color {
    return when (status.lowercase()) {
        "open", "pending" -> AppColors.StatusPending
        "accepted", "repairing" -> AppColors.StatusAccepted
        "committed" -> AppColors.StatusRepairing
        "closed", "completed" -> AppColors.StatusCompleted
        "cancelled", "canceled" -> AppColors.StatusCancelled
        else -> AppColors.Primary
    }
}

@Composable
fun StatusBadge(
    status: String,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val color = statusColor(status)
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(0.8.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label ?: status,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
