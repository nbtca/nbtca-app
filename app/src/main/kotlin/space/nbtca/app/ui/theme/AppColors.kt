package space.nbtca.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 与 Flutter 旧版 app_colors.dart 完全对齐的 Apple 风格品牌色板。
 * 关闭了 Material You 动态取色，保证双端视觉一致。
 */
object AppColors {
    // 品牌科技蓝 (Apple Technology Blue)
    val Primary = Color(0xFF0071E3)
    val PrimaryDark = Color(0xFF0058B6)
    val PrimaryLight = Color(0xFF479BF5)

    // 浅色模式
    val BackgroundLight = Color(0xFFF5F5F7)
    val SurfaceLight = Color(0xFFFFFFFF)
    val TextPrimaryLight = Color(0xFF1D1D1F)
    val TextSecondaryLight = Color(0xFF86868B)
    val DividerLight = Color(0xFFE5E5EA)

    // 深色模式
    val BackgroundDark = Color(0xFF000000)
    val SurfaceDark = Color(0xFF1C1C1E)
    val SurfaceDarkElevated = Color(0xFF2C2C2E)
    val TextPrimaryDark = Color(0xFFF5F5F7)
    val TextSecondaryDark = Color(0xFFA1A1A6)
    val DividerDark = Color(0xFF38383A)

    // 状态色
    val StatusPending = Color(0xFFFF9500)
    val StatusAccepted = Color(0xFF0071E3)
    val StatusRepairing = Color(0xFF5856D6)
    val StatusCompleted = Color(0xFF34C759)
    val StatusCancelled = Color(0xFF8E8E93)
    val StatusAlert = Color(0xFFFF3B30)

    // 类别标签色
    val TagTech = Color(0xFF0071E3)
    val TagActivity = Color(0xFFFF9500)
    val TagNotice = Color(0xFF34C759)
}
