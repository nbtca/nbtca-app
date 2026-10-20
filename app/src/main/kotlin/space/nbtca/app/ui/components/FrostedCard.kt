package space.nbtca.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.nbtca.app.ui.theme.AppThemeColors

/**
 * 对齐 Flutter FrostedCard：18dp 圆角、0.6dp 描边、淡阴影、可点击水波纹。
 */
@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    margin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val card = @Composable {
        if (onClick != null) {
            Card(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = AppThemeColors.surface),
                border = BorderStroke(0.6.dp, AppThemeColors.divider),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(padding), content = content)
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = AppThemeColors.surface),
                border = BorderStroke(0.6.dp, AppThemeColors.divider),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(padding), content = content)
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth().padding(margin)) {
        card()
    }
}
