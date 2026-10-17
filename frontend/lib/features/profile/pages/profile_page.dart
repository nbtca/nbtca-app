import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../app/theme/app_colors.dart';
import '../../../core/auth/auth_service.dart';
import '../../../core/network/api_client.dart';
import '../../../core/widgets/frosted_card.dart';
import 'login_page.dart';

class ProfilePage extends StatefulWidget {
  final VoidCallback onToggleTheme;
  final ThemeMode currentThemeMode;

  const ProfilePage({
    super.key,
    required this.onToggleTheme,
    required this.currentThemeMode,
  });

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  final ApiClient _apiClient = ApiClient();
  final AuthService _authService = AuthService();

  @override
  void initState() {
    super.initState();
    _authService.addListener(_onAuthChanged);
  }

  @override
  void dispose() {
    _authService.removeListener(_onAuthChanged);
    super.dispose();
  }

  void _onAuthChanged() {
    if (mounted) setState(() {});
  }

  void _showRoleViewDialog() {
    if (!_authService.isLoggedIn) return;
    final current = _authService.viewRole;
    final rolesText = _authService.currentUser?.roles.isEmpty ?? true
        ? '无'
        : _authService.currentUser!.roles.join(' / ');
    final saturdayRoleText = _authService.currentUser?.saturdayRole ?? '未绑定';
    Widget option({
      required String? value,
      required String title,
      required String subtitle,
    }) {
      final selected = current == value;
      return SimpleDialogOption(
        onPressed: () {
          _authService.setViewRole(value);
          Navigator.pop(context);
          setState(() {});
        },
        child: Row(
          children: [
            Icon(
              selected ? Icons.radio_button_checked : Icons.radio_button_off,
              size: 20,
              color: selected ? AppColors.primary : Colors.grey,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      fontWeight:
                          selected ? FontWeight.bold : FontWeight.normal,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: const TextStyle(fontSize: 11, color: Colors.grey),
                  ),
                ],
              ),
            ),
          ],
        ),
      );
    }

    showDialog<void>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('切换界面视角'),
            const SizedBox(height: 6),
            const Text(
              '只改变页面展示，不会切换账号或修改个人资料。',
              style: TextStyle(fontSize: 12, fontWeight: FontWeight.normal),
            ),
            const SizedBox(height: 8),
            Text(
              'Logto 角色: $rolesText',
              style: const TextStyle(fontSize: 11, color: Colors.grey),
            ),
            Text(
              'Saturday 会员角色: $saturdayRoleText',
              style: const TextStyle(fontSize: 11, color: Colors.grey),
            ),
          ],
        ),
        children: [
          option(
            value: null,
            title: '自动（最高权限）',
            subtitle: '按账号实际权限自动选择',
          ),
          if (_authService.canViewAdmin)
            option(
              value: 'admin',
              title: '管理员视角',
              subtitle: '工单总览、管理台与审核操作',
            ),
          if (_authService.canViewMember)
            option(
              value: 'member',
              title: '技术员视角',
              subtitle: '已接工单、接单与维修报告',
            ),
          option(
            value: 'client',
            title: '报修用户视角',
            subtitle: '只看自己的报修记录',
          ),
        ],
      ),
    );
  }

  void _showEndpointConfig() {
    final controller = TextEditingController(text: _apiClient.baseUrl);
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('配置后端 API 地址'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '默认使用模拟器地址 (10.0.2.2:8080)。真机请填电脑局域网 IP；'
              '该地址会持久保存。正式包建议使用 HTTPS 地址。',
              style: TextStyle(fontSize: 13, height: 1.4),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              decoration: const InputDecoration(
                labelText: 'Base URL',
                border: OutlineInputBorder(),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
          FilledButton(
            onPressed: () {
              _apiClient.updateBaseUrl(controller.text.trim());
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('API 地址已保存为: ${_apiClient.baseUrl}')),
              );
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
  }

  void _showAboutDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('关于 拔电关机 (NBTCA)'),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('NBTCA Android App v1.0.0',
                style: TextStyle(fontWeight: FontWeight.bold)),
            SizedBox(height: 8),
            Text('“To be at the intersection of technology and liberal arts.”',
                style: TextStyle(fontStyle: FontStyle.italic)),
            SizedBox(height: 12),
            Text(
                '浙大宁波理工学院 计算机协会 官方开源移动客户端。\n整合 Newsroom 资讯、Saturday 维修系统、基础设施监测等服务。'),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('关闭')),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final isLoggedIn = _authService.isLoggedIn;
    final user = _authService.currentUser;

    return Scaffold(
      appBar: AppBar(
        title:
            const Text('个人中心', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        children: [
          // 用户资料头部卡片
          FrostedCard(
            margin: const EdgeInsets.all(16),
            padding: const EdgeInsets.all(20),
            child: isLoggedIn
                ? Row(
                    children: [
                      CircleAvatar(
                        radius: 34,
                        backgroundColor:
                            const Color(0xFF004B86).withOpacity(0.15),
                        backgroundImage:
                            (user?.avatar != null && user!.avatar!.isNotEmpty)
                                ? NetworkImage(user.avatar!)
                                : null,
                        child: (user?.avatar == null || user!.avatar!.isEmpty)
                            ? const Icon(Icons.person_rounded,
                                size: 40, color: Color(0xFF004B86))
                            : null,
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Wrap(
                              crossAxisAlignment: WrapCrossAlignment.center,
                              spacing: 8,
                              runSpacing: 4,
                              children: [
                                Text(
                                  user?.name ?? "极客同学",
                                  style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 18),
                                ),
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                      horizontal: 6, vertical: 2),
                                  decoration: BoxDecoration(
                                    color: Colors.green.withOpacity(0.15),
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                  child: const Text(
                                    'Logto 已授权',
                                    style: TextStyle(
                                        color: Colors.green,
                                        fontSize: 11,
                                        fontWeight: FontWeight.bold),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 4),
                            Text('账号: ${user?.email}',
                                style: TextStyle(
                                    fontSize: 13,
                                    color: isDark
                                        ? AppColors.textSecondaryDark
                                        : AppColors.textSecondaryLight)),
                            const SizedBox(height: 6),
                            Wrap(
                              spacing: 6,
                              children: (user?.roles ?? ['认证用户'])
                                  .map((r) => Container(
                                        padding: const EdgeInsets.symmetric(
                                            horizontal: 8, vertical: 2),
                                        decoration: BoxDecoration(
                                          color: AppColors.primary
                                              .withOpacity(0.12),
                                          borderRadius:
                                              BorderRadius.circular(8),
                                        ),
                                        child: Text(r,
                                            style: const TextStyle(
                                                color: AppColors.primary,
                                                fontSize: 11)),
                                      ))
                                  .toList(),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.logout_rounded,
                            color: Colors.grey),
                        tooltip: '退出登录',
                        onPressed: () {
                          _authService.logout();
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('已退出当前 Logto 账号')),
                          );
                        },
                      ),
                    ],
                  )
                : Row(
                    children: [
                      CircleAvatar(
                        radius: 34,
                        backgroundColor: isDark
                            ? Colors.white10
                            : Colors.black.withOpacity(0.06),
                        child: Icon(Icons.account_circle_outlined,
                            size: 44,
                            color: isDark ? Colors.white54 : Colors.grey),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('未登录 NBTCA 账号',
                                style: TextStyle(
                                    fontWeight: FontWeight.bold, fontSize: 18)),
                            const SizedBox(height: 4),
                            Text('注册/登录后方可使用预约维修功能',
                                style: TextStyle(
                                    fontSize: 13,
                                    color: isDark
                                        ? AppColors.textSecondaryDark
                                        : AppColors.textSecondaryLight)),
                          ],
                        ),
                      ),
                      FilledButton(
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFF004B86),
                          visualDensity: VisualDensity.compact,
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(10)),
                        ),
                        onPressed: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                                builder: (_) => const LoginPage()),
                          );
                        },
                        child: const Text('登录 / 注册'),
                      ),
                    ],
                  ),
          ),

          // 设置与配置列表
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 10, 16, 4),
            child: Text(
              '首选项与网络',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: isDark
                    ? AppColors.textSecondaryDark
                    : AppColors.textSecondaryLight,
              ),
            ),
          ),

          FrostedCard(
            margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            padding: EdgeInsets.zero,
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.brightness_6_rounded,
                      color: AppColors.primary),
                  title: const Text('外观深浅主题切换'),
                  subtitle: Text(isDark ? '当前：深色模式' : '当前：浅色模式'),
                  trailing: Switch(
                    value: isDark,
                    onChanged: (_) => widget.onToggleTheme(),
                  ),
                ),
                Divider(
                    height: 1,
                    indent: 56,
                    color: isDark
                        ? AppColors.dividerDark
                        : AppColors.dividerLight),
                ListTile(
                  leading: const Icon(Icons.visibility_rounded,
                      color: AppColors.primary),
                  title: const Text('界面视角'),
                  subtitle: Text(
                    '账号角色: ${_authService.actualRoleName} · 当前: ${_authService.effectiveRoleName}',
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: _showRoleViewDialog,
                ),
                Divider(
                    height: 1,
                    indent: 56,
                    color: isDark
                        ? AppColors.dividerDark
                        : AppColors.dividerLight),
                ListTile(
                  leading:
                      const Icon(Icons.dns_rounded, color: AppColors.primary),
                  title: const Text('后端服务器配置'),
                  subtitle: Text(
                    '当前: ${_apiClient.baseUrl}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: _showEndpointConfig,
                ),
              ],
            ),
          ),

          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 16, 4),
            child: Text(
              '社团开源生态',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: isDark
                    ? AppColors.textSecondaryDark
                    : AppColors.textSecondaryLight,
              ),
            ),
          ),

          FrostedCard(
            margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            padding: EdgeInsets.zero,
            child: Column(
              children: [
                ListTile(
                  leading:
                      const Icon(Icons.code_rounded, color: Colors.blueAccent),
                  title: const Text('GitHub @nbtca 组织主页'),
                  subtitle: const Text('探索 Saturday, Sunday, Home 等开源代码仓库'),
                  trailing: const Icon(Icons.open_in_new_rounded, size: 18),
                  onTap: () async {
                    final url = Uri.parse("https://github.com/nbtca");
                    if (await canLaunchUrl(url)) {
                      launchUrl(url, mode: LaunchMode.externalApplication);
                    }
                  },
                ),
                Divider(
                    height: 1,
                    indent: 56,
                    color: isDark
                        ? AppColors.dividerDark
                        : AppColors.dividerLight),
                ListTile(
                  leading:
                      const Icon(Icons.language_rounded, color: Colors.teal),
                  title: const Text('拔电关机官方网站 (nbtca.space)'),
                  subtitle: const Text('浏览网页端完整版图文与活动展示'),
                  trailing: const Icon(Icons.open_in_new_rounded, size: 18),
                  onTap: () async {
                    final url = Uri.parse("https://nbtca.space");
                    if (await canLaunchUrl(url)) {
                      launchUrl(url, mode: LaunchMode.externalApplication);
                    }
                  },
                ),
                Divider(
                    height: 1,
                    indent: 56,
                    color: isDark
                        ? AppColors.dividerDark
                        : AppColors.dividerLight),
                ListTile(
                  leading: const Icon(Icons.info_outline_rounded,
                      color: Colors.amber),
                  title: const Text('关于 NBTCA'),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: _showAboutDialog,
                ),
              ],
            ),
          ),

          const SizedBox(height: 36),
          Center(
            child: Text(
              '拔电关机 · NBTCA Computer Association\nVersion 1.0.0 (Build 100)',
              textAlign: TextAlign.center,
              style: TextStyle(
                  fontSize: 12,
                  color: isDark ? Colors.grey[600] : Colors.grey[500]),
            ),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }
}
