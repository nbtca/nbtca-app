import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../../app/theme/app_colors.dart';
import '../../../core/auth/auth_service.dart';
import 'logto_webview_page.dart';

class LoginPage extends StatefulWidget {
  final VoidCallback? onLoginSuccess;

  const LoginPage({super.key, this.onLoginSuccess});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  Future<void> _openLogto() async {
    // 退出登录后重新进入时清掉上一次 Logto 会话，避免 WebView 自动回调旧账号。
    try {
      await WebViewCookieManager().clearCookies();
    } catch (_) {}
    if (!mounted) return;
    final success = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => const LogtoWebViewPage(),
      ),
    );

    if (success == true && mounted) {
      if (widget.onLoginSuccess != null) {
        widget.onLoginSuccess!();
      } else {
        Navigator.pop(context, true);
      }
    }
  }

  void _completeDevLogin(String role) {
    final auth = AuthService();
    auth.switchDevRole(role);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('已切换调试视角：${auth.currentUser?.repairRoleName}'),
        backgroundColor: AppColors.statusCompleted,
      ),
    );
    if (widget.onLoginSuccess != null) {
      widget.onLoginSuccess!();
    } else {
      Navigator.pop(context, true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final subtextColor =
        isDark ? const Color(0xFF8E8E93) : const Color(0xFF6E6E73);

    return Scaffold(
      appBar: AppBar(
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.close_rounded),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
        children: [
          const SizedBox(height: 12),
          Center(
            child: Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                color: const Color(0xFF004B86).withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: const Center(
                child: Icon(Icons.lock_person_rounded,
                    size: 44, color: Color(0xFF004B86)),
              ),
            ),
          ),
          const SizedBox(height: 20),
          const Center(
            child: Text(
              '登录你的账号',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.w800,
                letterSpacing: -0.5,
              ),
            ),
          ),
          const SizedBox(height: 8),
          Center(
            child: Text(
              '计算机协会统一身份认证中心 (Logto)\n'
              '登录后可预约维修、查看自己的维修记录',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 13, color: subtextColor, height: 1.45),
            ),
          ),
          const SizedBox(height: 32),
          SizedBox(
            width: double.infinity,
            height: 52,
            child: ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF004B86),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
                elevation: 0,
              ),
              icon: const Icon(Icons.login_rounded, size: 22),
              label: const Text(
                '使用 NBTCA 账号登录',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
              onPressed: _openLogto,
            ),
          ),
          const SizedBox(height: 12),
          Center(
            child: Text(
              '支持用户名/邮箱、GitHub 与 Passkey 登录\n登录凭证仅保存在本机安全存储',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12, color: subtextColor, height: 1.5),
            ),
          ),
          const SizedBox(height: 40),
          if (kDebugMode) ...[
            Row(
              children: [
                Expanded(
                  child: Divider(
                    color: isDark ? Colors.white24 : Colors.black12,
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: Text('调试工具（仅 Debug）',
                      style: TextStyle(fontSize: 12, color: subtextColor)),
                ),
                Expanded(
                  child: Divider(
                    color: isDark ? Colors.white24 : Colors.black12,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color:
                    isDark ? const Color(0xFF1C1C1E) : const Color(0xFFF5F5F7),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    '仅用于本地调试；选择后会替换当前登录账号：',
                    style: TextStyle(fontSize: 12, color: subtextColor),
                  ),
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      OutlinedButton(
                        onPressed: () => _completeDevLogin('Repair Admin'),
                        child: const Text('管理员 Mock'),
                      ),
                      OutlinedButton(
                        onPressed: () => _completeDevLogin('Repair Member'),
                        child: const Text('技术员 Mock'),
                      ),
                      OutlinedButton(
                        onPressed: () => _completeDevLogin('Client'),
                        child: const Text('报修用户 Mock'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 32),
        ],
      ),
    );
  }
}
