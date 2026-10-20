package space.nbtca.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 当前是否深色，供组件读取色板（与 Flutter Theme.of(context).brightness 等价）。 */
val LocalIsDark = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.Primary.copy(alpha = 0.12f),
    onPrimaryContainer = AppColors.Primary,
    secondary = AppColors.PrimaryDark,
    background = AppColors.BackgroundLight,
    onBackground = AppColors.TextPrimaryLight,
    surface = AppColors.SurfaceLight,
    onSurface = AppColors.TextPrimaryLight,
    surfaceVariant = AppColors.BackgroundLight,
    onSurfaceVariant = AppColors.TextSecondaryLight,
    outline = AppColors.DividerLight,
    outlineVariant = AppColors.DividerLight,
    error = AppColors.StatusAlert
)

private val DarkColors = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.Primary.copy(alpha = 0.22f),
    onPrimaryContainer = AppColors.PrimaryLight,
    secondary = AppColors.PrimaryLight,
    background = AppColors.BackgroundDark,
    onBackground = AppColors.TextPrimaryDark,
    surface = AppColors.SurfaceDark,
    onSurface = AppColors.TextPrimaryDark,
    surfaceVariant = AppColors.SurfaceDarkElevated,
    onSurfaceVariant = AppColors.TextSecondaryDark,
    outline = AppColors.DividerDark,
    outlineVariant = AppColors.DividerDark,
    error = AppColors.StatusAlert
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp)
)

@Composable
fun NbtcaTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content
        )
    }
}

/** 常用语义色，随深浅色切换。 */
object AppThemeColors {
    val textPrimary: Color
        @Composable get() = if (LocalIsDark.current) AppColors.TextPrimaryDark else AppColors.TextPrimaryLight

    val textSecondary: Color
        @Composable get() = if (LocalIsDark.current) AppColors.TextSecondaryDark else AppColors.TextSecondaryLight

    val divider: Color
        @Composable get() = if (LocalIsDark.current) AppColors.DividerDark else AppColors.DividerLight

    val surface: Color
        @Composable get() = if (LocalIsDark.current) AppColors.SurfaceDark else AppColors.SurfaceLight

    val surfaceElevated: Color
        @Composable get() = if (LocalIsDark.current) AppColors.SurfaceDarkElevated else AppColors.BackgroundLight
}
