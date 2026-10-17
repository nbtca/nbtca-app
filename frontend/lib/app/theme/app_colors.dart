import 'package:flutter/material.dart';

class AppColors {
  // 品牌科技蓝 (Apple Technology Blue)
  static const Color primary = Color(0xFF0071E3);
  static const Color primaryDark = Color(0xFF0058B6);
  static const Color primaryLight = Color(0xFF479BF5);

  // 浅色模式背景与文本
  static const Color backgroundLight = Color(0xFFF5F5F7);
  static const Color surfaceLight = Color(0xFFFFFFFF);
  static const Color textPrimaryLight = Color(0xFF1D1D1F);
  static const Color textSecondaryLight = Color(0xFF86868B);
  static const Color dividerLight = Color(0xFFE5E5EA);

  // 深色模式背景与文本
  static const Color backgroundDark = Color(0xFF000000);
  static const Color surfaceDark = Color(0xFF1C1C1E);
  static const Color surfaceDarkElevated = Color(0xFF2C2C2E);
  static const Color textPrimaryDark = Color(0xFFF5F5F7);
  static const Color textSecondaryDark = Color(0xFFA1A1A6);
  static const Color dividerDark = Color(0xFF38383A);

  // 状态颜色
  static const Color statusPending = Color(0xFFFF9500); // 待受理 橙色
  static const Color statusAccepted = Color(0xFF0071E3); // 已接单 蓝色
  static const Color statusRepairing = Color(0xFF5856D6); // 维修中 紫色
  static const Color statusCompleted = Color(0xFF34C759); // 已完成 绿色
  static const Color statusCancelled = Color(0xFF8E8E93); // 已取消 灰色
  static const Color statusAlert = Color(0xFFFF3B30); // 警示 红色

  // 类别标签色
  static const Color tagTech = Color(0xFF0071E3);
  static const Color tagActivity = Color(0xFFFF9500);
  static const Color tagNotice = Color(0xFF34C759);
}
