import 'package:flutter/material.dart';
import '../../../app/theme/app_colors.dart';
import '../../../core/auth/auth_service.dart';
import '../../../core/network/api_client.dart';
import '../../../core/utils/time_utils.dart';
import '../../../core/widgets/frosted_card.dart';
import '../../../core/widgets/status_badge.dart';
import '../../profile/pages/login_page.dart';
import '../../tools/models/association_calendar.dart';
import '../../tools/pages/association_calendar_page.dart';
import '../models/repair_ticket.dart';
import 'create_ticket_page.dart';
import 'ticket_detail_page.dart';

class RepairHomePage extends StatefulWidget {
  const RepairHomePage({super.key});

  @override
  State<RepairHomePage> createState() => _RepairHomePageState();
}

class _RepairHomePageState extends State<RepairHomePage>
    with TickerProviderStateMixin {
  final ApiClient _apiClient = ApiClient();
  final AuthService _authService = AuthService();
  late TabController _tabController;
  List<RepairTicket> _tickets = [];
  List<RepairTicket> _queueTickets = [];
  bool _isLoading = true;
  bool _isStaff = false;
  String _ticketStatusFilter = 'all';
  AssociationEvent? _repairDayEvent;

  @override
  void initState() {
    super.initState();
    _authService.addListener(_onAuthChanged);
    _isStaff = _authService.isRepairStaff;
    _tabController = TabController(
      length: _isStaff ? 3 : 2,
      vsync: this,
    );
    _loadTickets();
    _loadRepairDay();
  }

  Future<void> _loadRepairDay() async {
    try {
      final calendar = await _apiClient.getAssociationCalendar();
      final match = calendar.events
          .where((event) => event.title.contains('维修日'))
          .toList();
      if (!mounted) return;
      setState(() {
        _repairDayEvent = match.isEmpty ? null : match.first;
      });
    } catch (_) {
      // 日历不可用时保持“暂无安排”，不影响报修主流程。
    }
  }

  @override
  void dispose() {
    _authService.removeListener(_onAuthChanged);
    _tabController.dispose();
    super.dispose();
  }

  void _onAuthChanged() {
    if (!mounted) return;
    final nextIsStaff = _authService.isRepairStaff;
    final targetLength = nextIsStaff ? 3 : 2;
    final oldController = _tabController;
    var controllerChanged = false;
    if (_tabController.length != targetLength) {
      // 先替换 controller，再 setState；TabBar 与 TabBarView 必须使用同一个长度，
      // 否则会出现 "length 2 vs children 3" 的布局异常和巨大 overflow。
      _tabController = TabController(length: targetLength, vsync: this);
      controllerChanged = true;
    }
    _isStaff = nextIsStaff;
    setState(() {});
    if (controllerChanged) {
      // 等这一帧完成 TabBar/TabBarView 的切换后再释放旧 controller。
      WidgetsBinding.instance.addPostFrameCallback((_) {
        oldController.dispose();
      });
    }
    _loadTickets();
  }

  Future<void> _loadTickets() async {
    setState(() => _isLoading = true);
    try {
      List<RepairTicket> tickets = [];
      List<RepairTicket> queue = [];
      if (!_authService.isLoggedIn) {
        // 未登录不展示任何人的公开工单，避免泄露其他同学的问题描述。
      } else if (_authService.isRepairAdmin) {
        queue = await _apiClient.getPublicTickets(limit: 100);
        tickets = queue;
      } else if (_authService.isRepairStaff) {
        final results = await Future.wait([
          _apiClient.getMemberTickets(limit: 100),
          _apiClient.getPublicTickets(limit: 100),
        ]);
        tickets = results[0];
        queue = results[1];
      } else {
        tickets = await _apiClient.getClientTickets(limit: 100);
      }
      if (!mounted) return;
      setState(() {
        _tickets = tickets;
        _queueTickets = queue;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _isLoading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
            content: Text(e.toString()), backgroundColor: Colors.redAccent),
      );
    }
  }

  List<RepairTicket> get _filteredTickets {
    if (_ticketStatusFilter == 'all') return _tickets;
    if (_ticketStatusFilter == 'open') {
      return _tickets.where((t) => t.isOpen).toList();
    }
    if (_ticketStatusFilter == 'ongoing') {
      return _tickets.where((t) => t.isAccepted || t.isCommitted).toList();
    }
    if (_ticketStatusFilter == 'completed') {
      return _tickets.where((t) => t.isClosed).toList();
    }
    return _tickets;
  }

  void _navigateToCreate() async {
    final auth = AuthService();
    if (!auth.isLoggedIn) {
      final shouldLogin = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          shape:
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
          title: const Row(
            children: [
              Icon(Icons.lock_outline_rounded, color: Color(0xFF004B86)),
              SizedBox(width: 8),
              Expanded(
                child: Text(
                  '需要登录 NBTCA 账号',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
              ),
            ],
          ),
          content: const Text(
            '电脑维修服务需登录/注册 Logto 统一身份账号后方可预约，以便技术员确认维修时间并向您的邮箱同步进度。',
            style: TextStyle(fontSize: 14, height: 1.45),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('稍后再说'),
            ),
            FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFF004B86),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10)),
              ),
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('去登录 / 注册'),
            ),
          ],
        ),
      );

      if (shouldLogin == true && mounted) {
        final loginSuccess = await Navigator.push<bool>(
          context,
          MaterialPageRoute(builder: (_) => const LoginPage()),
        );
        if (loginSuccess != true) return;
      } else {
        return;
      }
    }

    if (!mounted) return;
    final created = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const CreateTicketPage()),
    );
    if (created == true) {
      _loadTickets();
      _tabController.animateTo(1);
    }
  }

  // 管理员/技术员快速接单操作
  Future<void> _handleAccept(RepairTicket ticket) async {
    try {
      await _apiClient.acceptEvent(ticket.id);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('成功接单 #${ticket.id}！'),
            backgroundColor: AppColors.statusAccepted,
          ),
        );
        _loadTickets();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text('接单失败: $e'), backgroundColor: Colors.redAccent),
        );
      }
    }
  }

  // 管理员审核关闭工单
  Future<void> _handleClose(RepairTicket ticket) async {
    try {
      await _apiClient.closeEvent(ticket.id);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('工单 #${ticket.id} 审核完毕，已正式结单归档！'),
            backgroundColor: AppColors.statusCompleted,
          ),
        );
        _loadTickets();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text('结单操作失败: $e'), backgroundColor: Colors.redAccent),
        );
      }
    }
  }

  // 管理员退回检修
  Future<void> _handleReject(RepairTicket ticket) async {
    try {
      await _apiClient.rejectCommitEvent(ticket.id);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('工单 #${ticket.id} 已退回补充'),
            backgroundColor: Colors.orange,
          ),
        );
        _loadTickets();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text('退回失败: $e'), backgroundColor: Colors.redAccent),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final isStaff = _isStaff;
    final isAdmin = _authService.isRepairAdmin;

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            const Text('维修',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 22)),
            const SizedBox(width: 8),
            if (isStaff)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: (isAdmin ? Colors.deepPurple : AppColors.primary)
                      .withOpacity(0.15),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: (isAdmin ? Colors.deepPurple : AppColors.primary)
                        .withOpacity(0.4),
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      isAdmin
                          ? Icons.admin_panel_settings_rounded
                          : Icons.engineering_rounded,
                      size: 14,
                      color: isAdmin ? Colors.deepPurple : AppColors.primary,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      _authService.effectiveRoleName,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: isAdmin ? Colors.deepPurple : AppColors.primary,
                      ),
                    ),
                  ],
                ),
              ),
          ],
        ),
        actions: [
          // 仅切换界面视角，不改账号、token 或个人资料。
          if (_authService.canViewMember)
            PopupMenuButton<String>(
              tooltip: '切换界面视角（不改变账号）',
              icon: const Icon(Icons.switch_account_rounded),
              onSelected: (role) {
                _authService.setViewRole(role == 'auto' ? null : role);
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(
                      '界面视角: ${_authService.effectiveRoleName}'
                      '（账号: ${_authService.currentUser?.name ?? '未登录'}）',
                    ),
                  ),
                );
              },
              itemBuilder: (ctx) => [
                const PopupMenuItem(
                  value: 'auto',
                  child: Text('自动（最高权限）'),
                ),
                if (_authService.canViewAdmin)
                  const PopupMenuItem(
                    value: 'admin',
                    child: Text('管理员视角'),
                  ),
                if (_authService.canViewMember)
                  const PopupMenuItem(
                    value: 'member',
                    child: Text('技术员视角'),
                  ),
                const PopupMenuItem(
                  value: 'client',
                  child: Text('报修用户视角'),
                ),
              ],
            ),
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            tooltip: '刷新工单',
            onPressed: _loadTickets,
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: AppColors.primary,
          indicatorWeight: 3,
          labelColor: AppColors.primary,
          unselectedLabelColor: isDark
              ? AppColors.textSecondaryDark
              : AppColors.textSecondaryLight,
          labelStyle:
              const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
          tabs: [
            const Tab(text: '服务概览'),
            Tab(
                text: isStaff
                    ? '工单总览 (${_tickets.length})'
                    : '维修记录 (${_tickets.length})'),
            if (isStaff)
              Tab(
                text: isAdmin ? '管理台 ($_openCount)' : '待处理 ($_openCount)',
              ),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          // Tab 1: 服务概览
          _buildOverviewTab(isDark),

          // Tab 2: 工单总览
          _buildRecordsTab(isDark),

          // Tab 3: 管理员专属维修管理大厅 (仅工作人员/管理员可见)
          if (isStaff) _buildAdminManagementTab(isDark),
        ],
      ),
    );
  }

  int get _openCount => _authService.isRepairAdmin
      ? _queueTickets.where((t) => t.isOpen || t.isCommitted).length
      : _queueTickets.where((t) => t.isOpen).length;

  String _repairDayTimeText() {
    final event = _repairDayEvent;
    if (event == null || event.start == null) {
      return '近期暂无维修日安排，请关注协会公共日历';
    }
    final start = toChinaTime(event.start!);
    final date = '${start.month} 月 ${start.day} 日';
    if (event.allDay) return '$date · 全天';
    final startTime =
        '${start.hour.toString().padLeft(2, '0')}:${start.minute.toString().padLeft(2, '0')}';
    final end = event.end;
    if (end == null) return '$date · $startTime';
    final endTime = toChinaTime(end);
    return '$date · $startTime - ${endTime.hour.toString().padLeft(2, '0')}:${endTime.minute.toString().padLeft(2, '0')}';
  }

  Widget _buildOverviewTab(bool isDark) {
    return RefreshIndicator(
      onRefresh: _loadTickets,
      child: ListView(
        padding: const EdgeInsets.symmetric(vertical: 6),
        children: [
          // Hero Banner
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [
                  Color(0xFF0F2027),
                  Color(0xFF203A43),
                  Color(0xFF2C5364)
                ],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(24),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(isDark ? 0.4 : 0.15),
                  blurRadius: 18,
                  offset: const Offset(0, 8),
                ),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.18),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: Colors.white24, width: 0.8),
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.volunteer_activism_rounded,
                              color: Colors.white, size: 14),
                          SizedBox(width: 6),
                          Text(
                            '公益 · 免费服务',
                            style: TextStyle(
                                color: Colors.white,
                                fontSize: 12,
                                fontWeight: FontWeight.w600),
                          ),
                        ],
                      ),
                    ),
                    const Spacer(),
                    Container(
                      padding: const EdgeInsets.all(6),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.12),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.build_circle_rounded,
                          color: Colors.white, size: 22),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                const Text(
                  '我们提供免费的电脑维修服务',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 22,
                    fontWeight: FontWeight.bold,
                    letterSpacing: -0.4,
                    height: 1.2,
                  ),
                ),
                const SizedBox(height: 6),
                const Text(
                  '从清理磁盘到加装硬件再到环境配置，我们都帮你搞定。',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: Colors.white70,
                    fontSize: 12.5,
                    height: 1.35,
                  ),
                ),
                const SizedBox(height: 14),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.primary,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 11),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                      elevation: 0,
                    ),
                    icon: const Icon(Icons.calendar_month_rounded, size: 18),
                    label: const Text(
                      '立即预约维修',
                      style:
                          TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                    ),
                    onPressed: _navigateToCreate,
                  ),
                ),
              ],
            ),
          ),

          // 三大核心服务能力
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 8, 16, 6),
            child: Text(
              '服务范围涵盖',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: isDark
                    ? AppColors.textPrimaryDark
                    : AppColors.textPrimaryLight,
              ),
            ),
          ),

          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                _buildServicePill(
                  isDark: isDark,
                  icon: Icons.cleaning_services_rounded,
                  title: '清理磁盘',
                  subtitle: '系统除尘/空间释放',
                  color: Colors.orange,
                ),
                const SizedBox(width: 10),
                _buildServicePill(
                  isDark: isDark,
                  icon: Icons.memory_rounded,
                  title: '加装硬件',
                  subtitle: '内存固态/硅脂重涂',
                  color: AppColors.primary,
                ),
                const SizedBox(width: 10),
                _buildServicePill(
                  isDark: isDark,
                  icon: Icons.terminal_rounded,
                  title: '环境配置',
                  subtitle: '系统重装/开发部署',
                  color: Colors.teal,
                ),
              ],
            ),
          ),

          // 线下活动专区：理工维修日
          Container(
            margin: const EdgeInsets.fromLTRB(16, 10, 16, 4),
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: isDark ? AppColors.surfaceDark : Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: isDark ? AppColors.dividerDark : AppColors.dividerLight,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(isDark ? 0.3 : 0.04),
                  blurRadius: 14,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(6),
                      decoration: BoxDecoration(
                        color: Colors.redAccent.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(9),
                      ),
                      child: const Icon(Icons.event_available_rounded,
                          color: Colors.redAccent, size: 19),
                    ),
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '理工维修日',
                            style: TextStyle(
                                fontSize: 15, fontWeight: FontWeight.bold),
                          ),
                          SizedBox(height: 1),
                          Text(
                            '协会线下定期定点义诊专场',
                            style: TextStyle(fontSize: 11, color: Colors.grey),
                          ),
                        ],
                      ),
                    ),
                    OutlinedButton(
                      style: OutlinedButton.styleFrom(
                        visualDensity: VisualDensity.compact,
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10)),
                        side: BorderSide(
                            color: AppColors.primary.withOpacity(0.5)),
                      ),
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                              builder: (_) => const AssociationCalendarPage()),
                        );
                      },
                      child: const Text('协会公共日历 →',
                          style: TextStyle(
                              fontSize: 11, color: AppColors.primary)),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 9),
                  decoration: BoxDecoration(
                    color: isDark
                        ? AppColors.surfaceDarkElevated
                        : const Color(0xFFF7F7F9),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.schedule_rounded,
                          size: 16, color: AppColors.primary),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          _repairDayTimeText(),
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w500,
                            color: isDark
                                ? AppColors.textPrimaryDark
                                : AppColors.textPrimaryLight,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 14),
        ],
      ),
    );
  }

  // Tab 2: 维修记录 (支持横向滑动避免溢出)
  Widget _buildRecordsTab(bool isDark) {
    final tickets = _filteredTickets;

    return RefreshIndicator(
      onRefresh: _loadTickets,
      child: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          // 状态过滤药丸（可横向滑动，防止窄屏下右侧溢出）
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _buildFilterChip('全部 (${_tickets.length})', 'all', isDark),
                  const SizedBox(width: 8),
                  _buildFilterChip(
                      '待受理 (${_tickets.where((t) => t.isOpen).length})',
                      'open',
                      isDark),
                  const SizedBox(width: 8),
                  _buildFilterChip('维修中', 'ongoing', isDark),
                  const SizedBox(width: 8),
                  _buildFilterChip('已完工', 'completed', isDark),
                  const SizedBox(width: 12),
                  FilledButton.tonalIcon(
                    style: FilledButton.styleFrom(
                      visualDensity: VisualDensity.compact,
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10)),
                    ),
                    icon: const Icon(Icons.add, size: 18),
                    label: const Text('新预约'),
                    onPressed: _navigateToCreate,
                  ),
                ],
              ),
            ),
          ),

          if (_isLoading)
            const Center(
              child: Padding(
                padding: EdgeInsets.all(48),
                child: CircularProgressIndicator.adaptive(),
              ),
            )
          else if (tickets.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 60, horizontal: 24),
              child: Center(
                child: Column(
                  children: [
                    Icon(
                      _authService.isLoggedIn
                          ? Icons.assignment_outlined
                          : Icons.lock_outline_rounded,
                      size: 64,
                      color: Colors.grey[400],
                    ),
                    const SizedBox(height: 16),
                    Text(
                      _authService.isLoggedIn ? '暂无相关维修记录' : '登录后查看你的报修记录',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: isDark
                            ? AppColors.textSecondaryDark
                            : AppColors.textSecondaryLight,
                      ),
                    ),
                    if (!_authService.isLoggedIn) ...[
                      const SizedBox(height: 12),
                      FilledButton.tonalIcon(
                        onPressed: () async {
                          final ok = await Navigator.push<bool>(
                            context,
                            MaterialPageRoute(
                              builder: (_) => const LoginPage(),
                            ),
                          );
                          if (ok == true) _loadTickets();
                        },
                        icon: const Icon(Icons.login_rounded, size: 18),
                        label: const Text('登录 NBTCA 账号'),
                      ),
                    ],
                  ],
                ),
              ),
            )
          else
            ...tickets.map(
              (ticket) => _buildTicketCard(ticket, isDark),
            ),

          const SizedBox(height: 36),
        ],
      ),
    );
  }

  // Tab 3: 管理员维修管理大厅 (对齐网页端维修管理：接受、审核、处理)
  Widget _buildAdminManagementTab(bool isDark) {
    final pendingTickets = _queueTickets.where((t) => t.isOpen).toList();
    final committedTickets = _queueTickets.where((t) => t.isCommitted).toList();
    final inProgressTickets = _authService.isRepairAdmin
        ? _queueTickets.where((t) => t.isAccepted).toList()
        : _tickets.where((t) => t.isAccepted).toList();

    return RefreshIndicator(
      onRefresh: _loadTickets,
      child: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // 顶部管理权限提醒
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: isDark
                    ? [const Color(0xFF2E1B4E), const Color(0xFF1B2838)]
                    : [const Color(0xFFF3E8FF), const Color(0xFFE0E7FF)],
              ),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.deepPurple.withOpacity(0.3)),
            ),
            child: Row(
              children: [
                const Icon(Icons.admin_panel_settings_rounded,
                    color: Colors.deepPurple, size: 28),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Saturday 维修管理中台',
                        style: TextStyle(
                            fontSize: 15, fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '当前身份: ${_authService.currentUser?.repairRoleName} · 拥有所有工单调度与管理权限',
                        style: TextStyle(
                            fontSize: 12,
                            color: isDark ? Colors.white70 : Colors.black87),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // 待受理工单 (Open - 支持一键“接单”)
          _buildAdminSectionTitle(
              '待受理工单 (${pendingTickets.length})', Colors.orange, isDark),
          if (pendingTickets.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text('暂无待受理工单，所有预约已被处理',
                  style: TextStyle(color: Colors.grey[500], fontSize: 13)),
            )
          else
            ...pendingTickets.map(
                (t) => _buildAdminTicketCard(t, isDark, actionType: 'accept')),

          const SizedBox(height: 20),

          // 待审核完工单 (Committed - 管理员专享“审核结单”与“退回”)
          if (_authService.isRepairAdmin) ...[
            _buildAdminSectionTitle(
                '待审核完工工单 (${committedTickets.length})', Colors.purple, isDark),
            if (committedTickets.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Text('暂无待审核完工单',
                    style: TextStyle(color: Colors.grey[500], fontSize: 13)),
              )
            else
              ...committedTickets.map(
                  (t) => _buildAdminTicketCard(t, isDark, actionType: 'audit')),
            const SizedBox(height: 20),
          ],

          // 检修中工单 (Accepted - 进行中)
          _buildAdminSectionTitle(
              '正在检修中 (${inProgressTickets.length})', AppColors.primary, isDark),
          if (inProgressTickets.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text('暂无正在检修的工单',
                  style: TextStyle(color: Colors.grey[500], fontSize: 13)),
            )
          else
            ...inProgressTickets.map((t) =>
                _buildAdminTicketCard(t, isDark, actionType: 'in_progress')),

          const SizedBox(height: 36),
        ],
      ),
    );
  }

  Widget _buildAdminSectionTitle(String title, Color color, bool isDark) {
    return Row(
      children: [
        Container(
          width: 4,
          height: 16,
          decoration: BoxDecoration(
              color: color, borderRadius: BorderRadius.circular(2)),
        ),
        const SizedBox(width: 8),
        Text(
          title,
          style: TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.bold,
            color:
                isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight,
          ),
        ),
      ],
    );
  }

  // 管理员专用操作卡片
  Widget _buildAdminTicketCard(RepairTicket ticket, bool isDark,
      {required String actionType}) {
    return FrostedCard(
      margin: const EdgeInsets.only(top: 8, bottom: 4),
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => TicketDetailPage(ticket: ticket)),
        ).then((_) => _loadTickets());
      },
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.primary.withOpacity(0.12),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  '#${ticket.id}',
                  style: const TextStyle(
                      color: AppColors.primary,
                      fontWeight: FontWeight.bold,
                      fontSize: 13),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  ticket.deviceType,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                      fontWeight: FontWeight.bold, fontSize: 15),
                ),
              ),
              StatusBadge(status: ticket.status, label: ticket.statusName),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            ticket.description,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 13,
              color: isDark
                  ? AppColors.textSecondaryDark
                  : AppColors.textSecondaryLight,
              height: 1.4,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Icon(Icons.person_outline,
                  size: 14,
                  color: isDark
                      ? AppColors.textSecondaryDark
                      : AppColors.textSecondaryLight),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  ticket.phone.isNotEmpty
                      ? '机主电话: ${ticket.phone}'
                      : ticket.qq.isNotEmpty
                          ? '机主 QQ: ${ticket.qq}'
                          : '公开工单未提供联系方式',
                  style: TextStyle(
                      fontSize: 12,
                      color: isDark
                          ? AppColors.textSecondaryDark
                          : AppColors.textSecondaryLight),
                ),
              ),
            ],
          ),
          const Divider(height: 20),
          // 专属管理动作按钮
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              if (actionType == 'accept') ...[
                OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                    foregroundColor: AppColors.primary,
                  ),
                  icon: const Icon(Icons.handyman_rounded, size: 16),
                  label: const Text('接受工单 (接单)'),
                  onPressed: () => _handleAccept(ticket),
                ),
              ] else if (actionType == 'audit') ...[
                OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                    foregroundColor: Colors.orange,
                  ),
                  onPressed: () => _handleReject(ticket),
                  child: const Text('退回补充'),
                ),
                const SizedBox(width: 8),
                FilledButton.icon(
                  style: FilledButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                    backgroundColor: Colors.green,
                  ),
                  icon: const Icon(Icons.check_circle_outline, size: 16),
                  label: const Text('审核通过结单'),
                  onPressed: () => _handleClose(ticket),
                ),
              ] else ...[
                TextButton(
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                          builder: (_) => TicketDetailPage(ticket: ticket)),
                    ).then((_) => _loadTickets());
                  },
                  child: const Text('查看与处理进度 →'),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildTicketCard(RepairTicket ticket, bool isDark) {
    return FrostedCard(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => TicketDetailPage(ticket: ticket),
          ),
        ).then((_) => _loadTickets());
      },
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: AppColors.primary.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Icon(Icons.laptop_chromebook_rounded,
                    color: AppColors.primary, size: 20),
              ),
              const SizedBox(width: 8),
              Text(
                '#${ticket.id}',
                style:
                    const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
              ),
              const Spacer(),
              StatusBadge(status: ticket.status, label: ticket.statusName),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            ticket.title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
          ),
          if (ticket.description.trim() != ticket.title.trim()) ...[
            const SizedBox(height: 6),
            Text(
              ticket.description,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 13,
                color: isDark
                    ? AppColors.textSecondaryDark
                    : AppColors.textSecondaryLight,
              ),
            ),
          ],
          const Divider(height: 24),
          Row(
            children: [
              Icon(Icons.laptop_mac_rounded,
                  size: 14,
                  color: isDark
                      ? AppColors.textSecondaryDark
                      : AppColors.textSecondaryLight),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  '型号: ${ticket.deviceType}',
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 12,
                    color: isDark
                        ? AppColors.textSecondaryDark
                        : AppColors.textSecondaryLight,
                  ),
                ),
              ),
              if (ticket.isOpen && _authService.isRepairStaff)
                FilledButton.tonal(
                  style: FilledButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8)),
                  ),
                  onPressed: () => _handleAccept(ticket),
                  child: const Text('接单'),
                )
              else
                const Text(
                  '查看详情 →',
                  style: TextStyle(
                      color: AppColors.primary,
                      fontSize: 13,
                      fontWeight: FontWeight.w600),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String label, String value, bool isDark) {
    final isSelected = _ticketStatusFilter == value;
    return ChoiceChip(
      label: Text(label),
      selected: isSelected,
      onSelected: (_) => setState(() => _ticketStatusFilter = value),
      selectedColor: AppColors.primary,
      backgroundColor: isDark ? AppColors.surfaceDark : Colors.white,
      labelStyle: TextStyle(
        fontSize: 12,
        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
        color: isSelected
            ? Colors.white
            : (isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight),
      ),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      side: BorderSide(
        color: isSelected
            ? Colors.transparent
            : (isDark ? AppColors.dividerDark : AppColors.dividerLight),
      ),
    );
  }

  Widget _buildServicePill({
    required bool isDark,
    required IconData icon,
    required String title,
    required String subtitle,
    required Color color,
  }) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
        decoration: BoxDecoration(
          color: isDark ? AppColors.surfaceDark : Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
              color: isDark ? AppColors.dividerDark : AppColors.dividerLight),
        ),
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(7),
              decoration: BoxDecoration(
                color: color.withOpacity(0.12),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, color: color, size: 19),
            ),
            const SizedBox(height: 6),
            Text(
              title,
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
            ),
            const SizedBox(height: 2),
            Text(
              subtitle,
              textAlign: TextAlign.center,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                  fontSize: 9,
                  color: isDark
                      ? AppColors.textSecondaryDark
                      : AppColors.textSecondaryLight),
            ),
          ],
        ),
      ),
    );
  }
}
