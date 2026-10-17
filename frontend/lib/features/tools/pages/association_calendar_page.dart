import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../app/theme/app_colors.dart';
import '../../../core/network/api_client.dart';
import '../../../core/utils/time_utils.dart';
import '../models/association_calendar.dart';

class AssociationCalendarPage extends StatefulWidget {
  const AssociationCalendarPage({super.key});

  @override
  State<AssociationCalendarPage> createState() =>
      _AssociationCalendarPageState();
}

class _AssociationCalendarPageState extends State<AssociationCalendarPage> {
  final ApiClient _apiClient = ApiClient();
  AssociationCalendar? _calendar;
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final calendar = await _apiClient.getAssociationCalendar();
      if (!mounted) return;
      setState(() {
        _calendar = calendar;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _error = e.toString();
      });
    }
  }

  Future<void> _openUrl(String raw) async {
    final uri = Uri.tryParse(raw);
    if (uri == null) return;
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } else if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('无法打开该链接')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final calendar = _calendar;

    return Scaffold(
      appBar: AppBar(
        title: const Text('协会公共日历'),
        actions: [
          IconButton(
            icon: const Icon(Icons.event_available_rounded),
            tooltip: '订阅协会日历',
            onPressed: () => _openUrl('https://ical.nbtca.space/events.ics'),
          ),
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            tooltip: '刷新协会日历',
            onPressed: _load,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator.adaptive())
          : calendar == null
              ? _buildError(isDark)
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
                    children: [
                      _buildHeader(calendar, isDark),
                      const SizedBox(height: 18),
                      Row(
                        children: [
                          const Text(
                            '近期活动',
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            '${calendar.events.length} 场',
                            style: TextStyle(
                              fontSize: 12,
                              color: isDark
                                  ? AppColors.textSecondaryDark
                                  : AppColors.textSecondaryLight,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      if (calendar.events.isEmpty)
                        _buildEmpty(isDark)
                      else
                        ..._buildGroupedEvents(calendar.events, isDark),
                    ],
                  ),
                ),
    );
  }

  Widget _buildHeader(AssociationCalendar calendar, bool isDark) {
    final updated = DateTime.tryParse(calendar.updated);
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF0F4C5C), Color(0xFF1F7A8C), Color(0xFF4EA8B8)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(Icons.groups_rounded, color: Colors.white70, size: 20),
              SizedBox(width: 8),
              Text(
                'NBTCA 计算机协会日历',
                style: TextStyle(
                  color: Colors.white70,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          const Text(
            '协会活动与线下聚会',
            style: TextStyle(
              color: Colors.white,
              fontSize: 24,
              fontWeight: FontWeight.bold,
              letterSpacing: -0.4,
            ),
          ),
          const SizedBox(height: 6),
          const Text(
            '来自 ical.nbtca.space/events.ics 的公开日历，包含 NWDC、迎新、维修日与学习会。',
            style: TextStyle(color: Colors.white70, fontSize: 12, height: 1.4),
          ),
          if (updated != null) ...[
            const SizedBox(height: 10),
            Text(
              '更新于 ${formatChinaDateTime(updated)}',
              style: const TextStyle(color: Colors.white54, fontSize: 11),
            ),
          ],
        ],
      ),
    );
  }

  List<Widget> _buildGroupedEvents(List<AssociationEvent> events, bool isDark) {
    final groups = <String, List<AssociationEvent>>{};
    for (final event in events) {
      final key = _dateKey(event.start);
      groups.putIfAbsent(key, () => []).add(event);
    }
    final widgets = <Widget>[];
    for (final entry in groups.entries) {
      final first = entry.value.first;
      widgets.add(
        Padding(
          padding: const EdgeInsets.fromLTRB(4, 14, 4, 6),
          child: Row(
            children: [
              Container(
                width: 4,
                height: 16,
                decoration: BoxDecoration(
                  color: AppColors.primary,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(width: 8),
              Text(
                _dateTitle(first.start),
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
      widgets.addAll(entry.value.map((e) => _buildEventCard(e, isDark)));
    }
    return widgets;
  }

  Widget _buildEventCard(AssociationEvent event, bool isDark) {
    return Material(
      color: isDark ? AppColors.surfaceDark : Colors.white,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: () => _showDetail(event),
        child: Container(
          margin: const EdgeInsets.only(bottom: 8),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: isDark ? AppColors.dividerDark : AppColors.dividerLight,
            ),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: 68,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _timeLabel(event),
                      style: const TextStyle(
                        color: AppColors.primary,
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    if (event.allDay)
                      const Padding(
                        padding: EdgeInsets.only(top: 3),
                        child: Text(
                          '全天',
                          style: TextStyle(fontSize: 10, color: Colors.grey),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      event.title,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        height: 1.3,
                      ),
                    ),
                    if (event.description.isNotEmpty) ...[
                      const SizedBox(height: 5),
                      Text(
                        _displayDescription(event.description)
                            .replaceAll(RegExp(r'\s+'), ' '),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 12,
                          height: 1.4,
                          color: isDark
                              ? AppColors.textSecondaryDark
                              : AppColors.textSecondaryLight,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              const Icon(Icons.chevron_right_rounded,
                  size: 18, color: Colors.grey),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _showDetail(AssociationEvent event) async {
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 4, 20, 24),
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  event.title,
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  _dateTitle(event.start) +
                      (event.allDay ? ' · 全天' : ' · ${_timeLabel(event)}'),
                  style: const TextStyle(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                if (event.description.isNotEmpty) ...[
                  const SizedBox(height: 14),
                  Text(
                    _displayDescription(event.description),
                    style: const TextStyle(fontSize: 14, height: 1.55),
                  ),
                ],
                if (event.url.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  FilledButton.tonalIcon(
                    onPressed: () {
                      Navigator.pop(ctx);
                      _openUrl(event.url);
                    },
                    icon: const Icon(Icons.open_in_new_rounded, size: 18),
                    label: const Text('打开活动链接'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildError(bool isDark) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.event_busy_rounded, size: 56, color: Colors.grey[400]),
            const SizedBox(height: 12),
            Text(
              _error ?? '协会日历加载失败',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 14,
                height: 1.5,
                color: isDark
                    ? AppColors.textSecondaryDark
                    : AppColors.textSecondaryLight,
              ),
            ),
            const SizedBox(height: 20),
            FilledButton.icon(
              onPressed: _load,
              icon: const Icon(Icons.refresh_rounded),
              label: const Text('重新加载'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEmpty(bool isDark) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 50),
      child: Center(
        child: Column(
          children: [
            Icon(Icons.event_note_outlined, size: 52, color: Colors.grey[400]),
            const SizedBox(height: 10),
            Text(
              '近期暂无协会活动',
              style: TextStyle(
                color: isDark
                    ? AppColors.textSecondaryDark
                    : AppColors.textSecondaryLight,
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _displayDescription(String text) {
    final kept = <String>[];
    for (final rawLine in text.split(RegExp(r'\r?\n'))) {
      var line = rawLine.trim();
      if (line.isEmpty) continue;
      if (line.startsWith('活动地点') ||
          line.startsWith('地点') ||
          line.toLowerCase().startsWith('location')) {
        continue;
      }
      line = line.replaceAll(RegExp(r'活动地点[：:]\s*[^\n]*'), '').trim();
      if (line.isNotEmpty) kept.add(line);
    }
    return kept.join('\n');
  }

  String _dateKey(DateTime? raw) {
    final t = raw == null ? DateTime.now() : toChinaTime(raw);
    return '${t.year}-${t.month.toString().padLeft(2, '0')}-${t.day.toString().padLeft(2, '0')}';
  }

  String _dateTitle(DateTime? raw) {
    final t = raw == null ? DateTime.now() : toChinaTime(raw);
    const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
    return '${t.month} 月 ${t.day} 日 · ${weekdays[t.weekday - 1]}';
  }

  String _timeLabel(AssociationEvent event) {
    if (event.allDay) return '全天';
    final start = event.start;
    if (start == null) return '';
    final s = toChinaTime(start);
    final end = event.end;
    final startText =
        '${s.hour.toString().padLeft(2, '0')}:${s.minute.toString().padLeft(2, '0')}';
    if (end == null) return startText;
    final e = toChinaTime(end);
    final endText =
        '${e.hour.toString().padLeft(2, '0')}:${e.minute.toString().padLeft(2, '0')}';
    return '$startText - $endText';
  }
}
