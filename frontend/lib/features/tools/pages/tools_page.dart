import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../app/theme/app_colors.dart';
import '../../../core/network/api_client.dart';
import '../../../core/utils/time_utils.dart';
import '../models/service_status.dart';
import 'association_calendar_page.dart';

class ToolsPage extends StatefulWidget {
  const ToolsPage({super.key});

  @override
  State<ToolsPage> createState() => _ToolsPageState();
}

class _ToolsPageState extends State<ToolsPage> {
  final ApiClient _apiClient = ApiClient();
  ServiceStatus? _status;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    try {
      final status = await _apiClient.getServiceStatus();
      if (!mounted) return;
      setState(() {
        _status = status;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _status = ServiceStatus.offline(e.toString());
        _isLoading = false;
      });
    }
  }

  Future<void> _openExternal(String url) async {
    final uri = Uri.tryParse(url);
    if (uri == null) return;
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } else if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('无法打开该链接')),
      );
    }
  }

  Color _stateColor(String state) {
    switch (state) {
      case 'operational':
        return AppColors.statusCompleted;
      case 'degraded':
      case 'partial':
        return AppColors.statusPending;
      case 'major':
      case 'down':
      case 'offline':
        return AppColors.statusAlert;
      default:
        return AppColors.textSecondaryLight;
    }
  }

  String _stateLabel(String state) {
    switch (state) {
      case 'operational':
        return '正常';
      case 'degraded':
        return '降级';
      case 'partial':
        return '部分可用';
      case 'major':
      case 'down':
        return '故障';
      case 'offline':
        return '离线';
      default:
        return '未知';
    }
  }

  String get _overallTitle {
    final state = _status?.state ?? 'unknown';
    switch (state) {
      case 'operational':
        return '所有核心服务正常运行';
      case 'degraded':
      case 'partial':
        return '部分服务出现降级';
      case 'major':
      case 'down':
        return '核心服务存在故障';
      default:
        return _status?.isOffline == true ? '暂时无法获取服务状态' : '服务状态未知';
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final status = _status;
    final overallColor = _stateColor(status?.state ?? 'unknown');
    final updated = status != null && status.updatedEpoch > 0
        ? chinaTimeFromEpoch(status.updatedEpoch)
        : null;

    return Scaffold(
      appBar: AppBar(
        title: const Text('极客工具',
            style: TextStyle(fontWeight: FontWeight.bold, fontSize: 22)),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            tooltip: '刷新服务状态',
            onPressed: _loadData,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator.adaptive())
          : RefreshIndicator(
              onRefresh: _loadData,
              child: ListView(
                padding: const EdgeInsets.symmetric(vertical: 12),
                children: [
                  _buildStatusHeader(
                    isDark: isDark,
                    status: status,
                    overallColor: overallColor,
                    updated: updated,
                  ),
                  if (status?.isOffline == true)
                    _buildErrorBanner(status?.message ?? '服务状态暂时不可用'),
                  const SizedBox(height: 18),
                  _buildSectionTitle('校园与协会工具', isDark),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: LayoutBuilder(
                      builder: (context, constraints) {
                        final width = (constraints.maxWidth - 10) / 2;
                        return Wrap(
                          spacing: 10,
                          runSpacing: 10,
                          children: [
                            _buildToolEntry(
                              width: width,
                              isDark: isDark,
                              icon: Icons.groups_rounded,
                              color: Colors.teal,
                              title: '协会活动日历',
                              subtitle: 'events.ics 公开活动',
                              onTap: () => Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) =>
                                      const AssociationCalendarPage(),
                                ),
                              ),
                            ),
                            _buildToolEntry(
                              width: width,
                              isDark: isDark,
                              icon: Icons.menu_book_rounded,
                              color: Colors.orange,
                              title: '协会文档',
                              subtitle: 'docs.nbtca.space',
                              onTap: () =>
                                  _openExternal('https://docs.nbtca.space'),
                            ),
                          ],
                        );
                      },
                    ),
                  ),
                  const SizedBox(height: 24),
                  _buildSectionTitle('服务节点监测', isDark),
                  if (status == null || status.groups.isEmpty)
                    _buildEmptyState(isDark)
                  else
                    ..._buildGroupSections(status, isDark),
                  if (status?.incidents.isNotEmpty == true) ...[
                    const SizedBox(height: 20),
                    _buildSectionTitle('近期事件', isDark),
                    ...status!.incidents.map(
                      (incident) => _buildIncidentCard(incident, isDark),
                    ),
                  ],
                  const SizedBox(height: 36),
                ],
              ),
            ),
    );
  }

  Widget _buildStatusHeader({
    required bool isDark,
    required ServiceStatus? status,
    required Color overallColor,
    required DateTime? updated,
  }) {
    final total = status?.totalComponents ?? 0;
    final healthy = status?.healthyComponents ?? 0;
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: isDark
              ? const [Color(0xFF111A24), Color(0xFF22384D)]
              : const [Color(0xFF1B2838), Color(0xFF2A475E)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.4 : 0.15),
            blurRadius: 16,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(
                  color: overallColor,
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: overallColor.withValues(alpha: 0.6),
                      blurRadius: 8,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  _overallTitle,
                  style: TextStyle(
                    color: overallColor,
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              if (total > 0)
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    '$healthy / $total 正常',
                    style: const TextStyle(
                      color: Colors.white70,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 14),
          const Text(
            'NBTCA Infrastructure',
            style: TextStyle(
              color: Colors.white,
              fontSize: 22,
              fontWeight: FontWeight.bold,
              letterSpacing: -0.3,
            ),
          ),
          const SizedBox(height: 6),
          const Text(
            '来自 status.nbtca.space 的实时监控数据，覆盖官网、维修、身份、校历与游戏服。',
            style: TextStyle(color: Colors.white70, fontSize: 13, height: 1.4),
          ),
          if (updated != null) ...[
            const SizedBox(height: 10),
            Text(
              '更新于 ${DateFormat('MM-dd HH:mm').format(updated)}',
              style: const TextStyle(color: Colors.white54, fontSize: 11),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildErrorBanner(String message) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.statusPending.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: AppColors.statusPending.withValues(alpha: 0.35),
        ),
      ),
      child: Row(
        children: [
          const Icon(Icons.cloud_off_rounded,
              size: 18, color: AppColors.statusPending),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: const TextStyle(fontSize: 12, height: 1.4),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title, bool isDark) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 0, 16, 10),
      child: Text(
        title,
        style: TextStyle(
          fontSize: 15,
          fontWeight: FontWeight.bold,
          color:
              isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
        ),
      ),
    );
  }

  Widget _buildToolEntry({
    required double width,
    required bool isDark,
    required IconData icon,
    required Color color,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return SizedBox(
      width: width,
      child: Material(
        color: isDark ? AppColors.surfaceDark : Colors.white,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(16),
          child: Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: isDark ? AppColors.dividerDark : AppColors.dividerLight,
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(icon, color: color, size: 22),
                ),
                const SizedBox(height: 10),
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  subtitle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 11,
                    color: isDark
                        ? AppColors.textSecondaryDark
                        : AppColors.textSecondaryLight,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  List<Widget> _buildGroupSections(ServiceStatus status, bool isDark) {
    final widgets = <Widget>[];
    for (final group in status.groups) {
      widgets.add(
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 12, 16, 8),
          child: Row(
            children: [
              Container(
                width: 3,
                height: 14,
                decoration: BoxDecoration(
                  color: group.infra
                      ? AppColors.textSecondaryLight
                      : AppColors.primary,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(width: 8),
              Text(
                group.name,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: isDark
                      ? AppColors.textSecondaryDark
                      : AppColors.textSecondaryLight,
                ),
              ),
            ],
          ),
        ),
      );
      widgets.addAll(
        group.components.map(
          (component) => _buildComponentCard(component, isDark),
        ),
      );
    }
    return widgets;
  }

  Widget _buildComponentCard(ServiceComponent component, bool isDark) {
    final color = _stateColor(component.state);
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isDark ? AppColors.surfaceDark : Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: isDark ? AppColors.dividerDark : AppColors.dividerLight,
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 9,
            height: 9,
            margin: const EdgeInsets.only(top: 4),
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        component.name,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 14,
                        ),
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 7, vertical: 2),
                      decoration: BoxDecoration(
                        color: color.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        _stateLabel(component.state),
                        style: TextStyle(
                          color: color,
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
                if (component.failed.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(
                    '异常区域：${component.failed.join('、')}',
                    style: TextStyle(fontSize: 11, color: color),
                  ),
                ],
                if (component.error != null && component.error!.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(
                    component.error!,
                    style: TextStyle(
                      fontSize: 11,
                      height: 1.35,
                      color: isDark
                          ? AppColors.textSecondaryDark
                          : AppColors.textSecondaryLight,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildIncidentCard(ServiceIncident incident, bool isDark) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.statusPending.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: AppColors.statusPending.withValues(alpha: 0.3),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            incident.title,
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
          ),
          const SizedBox(height: 4),
          Text(
            '影响级别：${incident.impact}',
            style: TextStyle(
              fontSize: 11,
              color: isDark
                  ? AppColors.textSecondaryDark
                  : AppColors.textSecondaryLight,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState(bool isDark) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 24),
      child: Center(
        child: Column(
          children: [
            Icon(Icons.monitor_heart_outlined,
                size: 52, color: Colors.grey[400]),
            const SizedBox(height: 12),
            Text(
              _status?.message ?? '暂无服务状态数据',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 14,
                color: isDark
                    ? AppColors.textSecondaryDark
                    : AppColors.textSecondaryLight,
              ),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _loadData,
              icon: const Icon(Icons.refresh_rounded, size: 18),
              label: const Text('重新加载'),
            ),
          ],
        ),
      ),
    );
  }
}
