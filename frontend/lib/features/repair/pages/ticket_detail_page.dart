import 'dart:io';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../../app/theme/app_colors.dart';
import '../../../core/auth/auth_service.dart';
import '../../../core/network/api_client.dart';
import '../../../core/utils/time_utils.dart';
import '../../../core/widgets/status_badge.dart';
import '../models/repair_ticket.dart';

class TicketDetailPage extends StatefulWidget {
  final RepairTicket ticket;

  const TicketDetailPage({super.key, required this.ticket});

  @override
  State<TicketDetailPage> createState() => _TicketDetailPageState();
}

class _TicketDetailPageState extends State<TicketDetailPage> {
  late RepairTicket _ticket;
  final ApiClient _apiClient = ApiClient();
  final AuthService _authService = AuthService();
  bool _isLoading = true;
  bool _isProcessing = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _ticket = widget.ticket;
    _loadDetail();
  }

  bool get _canViewContact {
    if (!_authService.isLoggedIn) return false;
    if (_authService.isRepairAdmin) return true;
    if (_authService.isRepairStaff) {
      final memberId = _authService.currentUser?.memberId;
      return _ticket.isAccepted ||
          _ticket.isCommitted ||
          _ticket.isClosed ||
          (memberId != null &&
              memberId.isNotEmpty &&
              _ticket.memberId == memberId);
    }
    return true;
  }

  Future<void> _loadDetail() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final ticket = await _apiClient.getTicketDetail(
        _ticket.id,
        fallback: _ticket,
      );
      if (!mounted) return;
      setState(() {
        _ticket = ticket;
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

  Future<void> _runAction(
    Future<RepairTicket> Function() action,
    String successMessage,
  ) async {
    setState(() => _isProcessing = true);
    try {
      final updated = await action();
      if (!mounted) return;
      setState(() => _ticket = updated);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(successMessage),
          backgroundColor: AppColors.statusCompleted,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
            content: Text(e.toString()), backgroundColor: Colors.redAccent),
      );
    } finally {
      if (mounted) setState(() => _isProcessing = false);
    }
  }

  Future<void> _acceptTicket() => _runAction(
        () => _apiClient.acceptEvent(_ticket.id),
        '已成功接受该工单',
      );

  Future<void> _dropTicket() => _runAction(
        () => _apiClient.dropEvent(_ticket.id),
        '已放弃该工单受理',
      );

  Future<void> _closeTicket() => _runAction(
        () => _apiClient.closeEvent(_ticket.id),
        '工单审核通过，已正式完成并归档',
      );

  Future<void> _rejectCommit() => _runAction(
        () => _apiClient.rejectCommitEvent(_ticket.id),
        '已退回检修，通知技术员补充处理',
      );

  Future<void> _showCommitDialog() async {
    final descriptionController = TextEditingController();
    final modelController = TextEditingController(text: _ticket.deviceType);
    final phoneController = TextEditingController(text: _ticket.phone);
    final qqController = TextEditingController(text: _ticket.qq);
    final selectedPhotos = <XFile>[];
    const sizes = <String, String>{
      'xs': 'XS · 极小',
      's': 'S · 小',
      'm': 'M · 中',
      'l': 'L · 大',
      'xl': 'XL · 极大',
    };
    String? selectedSize =
        sizes.containsKey(_ticket.size) ? _ticket.size : null;
    String? errorText;

    final result = await showModalBottomSheet<_CommitReportData>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setModalState) {
            Future<void> pickPhotos() async {
              final picked = await ImagePicker().pickMultiImage(
                imageQuality: 85,
                maxWidth: 2000,
              );
              if (picked.isEmpty) return;
              if (selectedPhotos.length + picked.length > 6) {
                setModalState(() => errorText = '最多上传 6 张现场照片');
                return;
              }
              setModalState(() {
                selectedPhotos.addAll(picked);
                errorText = null;
              });
            }

            return Padding(
              padding: EdgeInsets.only(
                left: 20,
                right: 20,
                bottom: MediaQuery.of(ctx).viewInsets.bottom + 16,
              ),
              child: SizedBox(
                height: MediaQuery.of(ctx).size.height * 0.86,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Expanded(
                          child: Text(
                            '提交维修报告',
                            style: TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        IconButton(
                          onPressed: () => Navigator.pop(ctx),
                          icon: const Icon(Icons.close_rounded),
                        ),
                      ],
                    ),
                    Expanded(
                      child: SingleChildScrollView(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _buildReportLabel(ctx, '型号'),
                            TextField(
                              controller: modelController,
                              readOnly: true,
                              decoration: const InputDecoration(
                                border: OutlineInputBorder(),
                                isDense: true,
                              ),
                            ),
                            const SizedBox(height: 10),
                            Row(
                              children: [
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      _buildReportLabel(ctx, '联系电话'),
                                      TextField(
                                        controller: phoneController,
                                        readOnly: true,
                                        decoration: const InputDecoration(
                                          border: OutlineInputBorder(),
                                          isDense: true,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(width: 10),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      _buildReportLabel(ctx, 'QQ'),
                                      TextField(
                                        controller: qqController,
                                        readOnly: true,
                                        decoration: const InputDecoration(
                                          border: OutlineInputBorder(),
                                          isDense: true,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 12),
                            Row(
                              children: [
                                _buildReportLabel(ctx, '现场照片'),
                                const Spacer(),
                                TextButton.icon(
                                  onPressed: pickPhotos,
                                  icon: const Icon(Icons.add_a_photo_outlined,
                                      size: 18),
                                  label: const Text('添加照片'),
                                ),
                              ],
                            ),
                            if (_ticket.photos.isNotEmpty ||
                                selectedPhotos.isNotEmpty)
                              SizedBox(
                                height: 84,
                                child: ListView(
                                  scrollDirection: Axis.horizontal,
                                  children: [
                                    for (final url in _ticket.photos)
                                      _buildReportPhoto(
                                        image: CachedNetworkImage(
                                          imageUrl: url,
                                          fit: BoxFit.cover,
                                        ),
                                      ),
                                    for (var i = 0;
                                        i < selectedPhotos.length;
                                        i++)
                                      _buildReportPhoto(
                                        image: Image.file(
                                          File(selectedPhotos[i].path),
                                          fit: BoxFit.cover,
                                        ),
                                        onRemove: () => setModalState(
                                          () => selectedPhotos.removeAt(i),
                                        ),
                                      ),
                                  ],
                                ),
                              )
                            else
                              Container(
                                width: double.infinity,
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  color: Theme.of(ctx)
                                      .colorScheme
                                      .surfaceContainerHighest,
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: const Text(
                                  '还没有照片，可添加维修后的现场照片',
                                  style: TextStyle(fontSize: 12),
                                ),
                              ),
                            const SizedBox(height: 12),
                            _buildReportLabel(ctx, '维修记录'),
                            if (_ticket.timeline.isEmpty)
                              const Text('暂无维修记录',
                                  style: TextStyle(fontSize: 12))
                            else
                              ..._ticket.timeline.reversed.take(3).map(
                                    (log) => Padding(
                                      padding: const EdgeInsets.only(bottom: 4),
                                      child: Text(
                                        '${log.title} · ${formatChinaDateTime(log.timestamp)}${log.operator.isEmpty ? '' : ' · ${log.operator}'}',
                                        style: const TextStyle(fontSize: 12),
                                      ),
                                    ),
                                  ),
                            const SizedBox(height: 12),
                            _buildReportLabel(ctx, '维修难度'),
                            DropdownButtonFormField<String>(
                              initialValue: selectedSize,
                              decoration: const InputDecoration(
                                hintText: '请选择维修难度',
                                border: OutlineInputBorder(),
                                isDense: true,
                              ),
                              items: sizes.entries
                                  .map(
                                    (entry) => DropdownMenuItem(
                                      value: entry.key,
                                      child: Text(entry.value),
                                    ),
                                  )
                                  .toList(),
                              onChanged: (value) => setModalState(
                                () => selectedSize = value,
                              ),
                            ),
                            const SizedBox(height: 12),
                            _buildReportLabel(ctx, '维修描述'),
                            TextField(
                              controller: descriptionController,
                              maxLines: 5,
                              decoration: const InputDecoration(
                                hintText: '请填写维修处理方案、更换部件与测试结果',
                                border: OutlineInputBorder(),
                              ),
                            ),
                            if (errorText != null) ...[
                              const SizedBox(height: 8),
                              Text(
                                errorText!,
                                style: const TextStyle(
                                  color: Colors.redAccent,
                                  fontSize: 12,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton(
                        onPressed: () {
                          final description = descriptionController.text.trim();
                          if (description.isEmpty) {
                            setModalState(() => errorText = '请填写维修描述');
                            return;
                          }
                          if (selectedSize == null) {
                            setModalState(() => errorText = '请选择维修难度');
                            return;
                          }
                          Navigator.pop(
                            ctx,
                            _CommitReportData(
                              description: description,
                              size: selectedSize!,
                              photos: List<XFile>.from(selectedPhotos),
                            ),
                          );
                        },
                        child: const Text('提交'),
                      ),
                    ),
                    const SizedBox(height: 6),
                    SizedBox(
                      width: double.infinity,
                      child: TextButton(
                        onPressed: () => Navigator.pop(ctx),
                        child: const Text('放弃'),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );

    if (result == null) return;
    await _runAction(
      () async {
        if (result.photos.isNotEmpty) {
          await _apiClient.uploadEventPhotos(_ticket.id, result.photos);
        }
        return _apiClient.commitEvent(
          _ticket.id,
          result.description,
          size: result.size,
        );
      },
      '维修报告已提交，等待管理员审核',
    );
  }

  Widget _buildReportLabel(BuildContext context, String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 5),
      child: Text(
        text,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: Theme.of(context).colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }

  Widget _buildReportPhoto({required Widget image, VoidCallback? onRemove}) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: Stack(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: SizedBox(width: 96, height: 84, child: image),
          ),
          if (onRemove != null)
            Positioned(
              top: 2,
              right: 2,
              child: GestureDetector(
                onTap: onRemove,
                child: Container(
                  padding: const EdgeInsets.all(2),
                  decoration: const BoxDecoration(
                    color: Colors.black54,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.close, size: 14, color: Colors.white),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Future<void> _showEditDialog() async {
    final problemController = TextEditingController(text: _ticket.description);
    final modelController = TextEditingController(text: _ticket.deviceType);
    final phoneController = TextEditingController(text: _ticket.phone);
    final qqController = TextEditingController(text: _ticket.qq);

    final data = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('修改报修信息'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: problemController,
                maxLines: 3,
                decoration: const InputDecoration(
                  labelText: '问题描述',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: modelController,
                decoration: const InputDecoration(
                  labelText: '设备型号',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: phoneController,
                keyboardType: TextInputType.phone,
                maxLength: 11,
                decoration: const InputDecoration(
                  labelText: '电话号码',
                  counterText: '',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: qqController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'QQ',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () {
              if (problemController.text.trim().isEmpty) return;
              Navigator.pop(ctx, {
                'problem': problemController.text.trim(),
                'model': modelController.text.trim(),
                'phone': phoneController.text.trim(),
                'qq': qqController.text.trim(),
              });
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );

    if (data != null) {
      await _runAction(
        () => _apiClient.updateClientEvent(_ticket.id, data),
        '报修信息已更新',
      );
    }
  }

  Future<void> _cancelTicket() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('取消维修预约'),
        content: const Text('确定要取消这个维修预约吗？此操作无法撤销。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('保留预约'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.redAccent),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('确认取消'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    setState(() => _isProcessing = true);
    try {
      await _apiClient.cancelClientEvent(_ticket.id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('维修预约已取消')),
      );
      Navigator.pop(context, true);
    } catch (e) {
      if (!mounted) return;
      setState(() => _isProcessing = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
            content: Text(e.toString()), backgroundColor: Colors.redAccent),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final isStaff = _authService.isRepairStaff;
    final isAdmin = _authService.isRepairAdmin;
    final canClientEdit = !isStaff && _ticket.isOpen && _authService.isLoggedIn;

    return Scaffold(
      appBar: AppBar(
        title: Text('工单 #${_ticket.id}'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            tooltip: '刷新工单',
            onPressed: _isLoading ? null : _loadDetail,
          ),
        ],
        bottom: _isLoading
            ? const PreferredSize(
                preferredSize: Size.fromHeight(2),
                child: LinearProgressIndicator(minHeight: 2),
              )
            : null,
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (_error != null) ...[
            _buildErrorBanner(_error!),
            const SizedBox(height: 12),
          ],
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: isDark ? AppColors.surfaceDark : Colors.white,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: isDark ? AppColors.dividerDark : AppColors.dividerLight,
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Text(
                        _ticket.deviceTypeName.isEmpty
                            ? '设备'
                            : _ticket.deviceTypeName,
                        style: const TextStyle(
                          color: AppColors.primary,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    StatusBadge(
                      status: _ticket.status,
                      label: _ticket.statusName,
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Text(
                  _ticket.title,
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                if (_ticket.description.trim() != _ticket.title.trim()) ...[
                  const SizedBox(height: 8),
                  Text(
                    _ticket.description,
                    style: TextStyle(
                      fontSize: 14,
                      height: 1.5,
                      color: isDark
                          ? AppColors.textSecondaryDark
                          : AppColors.textSecondaryLight,
                    ),
                  ),
                ],
                const Divider(height: 24),
                if (_canViewContact) ...[
                  _buildInfoRow('联系电话', _ticket.phone),
                  _buildInfoRow('QQ', _ticket.qq),
                  if (_ticket.phone.isEmpty && _ticket.qq.isEmpty)
                    _buildInfoRow('联系方式', '未提供'),
                ] else
                  _buildInfoRow('联系方式', '接单后可见'),
                _buildInfoRow('设备型号', _ticket.deviceType),
                if (_ticket.size.isNotEmpty)
                  _buildInfoRow('工单大小', _ticket.size),
                if (_ticket.memberName != null &&
                    _ticket.memberName!.isNotEmpty)
                  _buildInfoRow('经办技术员', _ticket.memberName!),
                if (_ticket.closedByName != null &&
                    _ticket.closedByName!.isNotEmpty)
                  _buildInfoRow('结单管理员', _ticket.closedByName!),
                if (_ticket.clientId.isNotEmpty)
                  _buildInfoRow('报修人编号', _ticket.clientId),
              ],
            ),
          ),
          if (_ticket.photos.isNotEmpty) ...[
            const SizedBox(height: 16),
            Text(
              '故障照片',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: isDark
                    ? AppColors.textPrimaryDark
                    : AppColors.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 8),
            SizedBox(
              height: 100,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: _ticket.photos.length,
                separatorBuilder: (_, __) => const SizedBox(width: 8),
                itemBuilder: (context, index) => ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: CachedNetworkImage(
                    imageUrl: _ticket.photos[index],
                    width: 130,
                    height: 100,
                    fit: BoxFit.cover,
                    errorWidget: (_, __, ___) => Container(
                      width: 130,
                      color: isDark
                          ? AppColors.surfaceDarkElevated
                          : Colors.grey[200],
                      child: const Icon(Icons.image_not_supported_outlined),
                    ),
                  ),
                ),
              ),
            ),
          ],
          const SizedBox(height: 16),
          if (isStaff) _buildStaffActions(isAdmin, isDark),
          if (canClientEdit) _buildClientActions(isDark),
          if (!isStaff && _ticket.isClosed) ...[
            const SizedBox(height: 8),
            const Row(
              children: [
                Icon(Icons.check_circle, color: Colors.green, size: 18),
                SizedBox(width: 6),
                Text(
                  '该工单已全流程闭环归档',
                  style: TextStyle(
                    fontSize: 13,
                    color: Colors.green,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ],
          if (!isStaff && _ticket.isCancelled) ...[
            const SizedBox(height: 8),
            const Row(
              children: [
                Icon(Icons.cancel_outlined, color: Colors.grey, size: 18),
                SizedBox(width: 6),
                Text('该工单已取消', style: TextStyle(fontSize: 13)),
              ],
            ),
          ],
          const SizedBox(height: 22),
          Text(
            '维修处理进度时间轴',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.bold,
              color: isDark
                  ? AppColors.textPrimaryDark
                  : AppColors.textPrimaryLight,
            ),
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: isDark ? AppColors.surfaceDark : Colors.white,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: isDark ? AppColors.dividerDark : AppColors.dividerLight,
              ),
            ),
            child: _ticket.timeline.isEmpty
                ? Text(
                    '暂无进度记录',
                    style: TextStyle(
                      color: isDark
                          ? AppColors.textSecondaryDark
                          : AppColors.textSecondaryLight,
                    ),
                  )
                : Column(
                    children: List.generate(_ticket.timeline.length, (index) {
                      final item = _ticket.timeline[index];
                      final isLast = index == _ticket.timeline.length - 1;
                      return Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Column(
                            children: [
                              Container(
                                width: 14,
                                height: 14,
                                decoration: BoxDecoration(
                                  color:
                                      isLast ? AppColors.primary : Colors.grey,
                                  shape: BoxShape.circle,
                                ),
                              ),
                              if (!isLast)
                                Container(
                                  width: 2,
                                  height: 56,
                                  color: isDark
                                      ? AppColors.dividerDark
                                      : AppColors.dividerLight,
                                ),
                            ],
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Padding(
                              padding: const EdgeInsets.only(bottom: 16),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    mainAxisAlignment:
                                        MainAxisAlignment.spaceBetween,
                                    children: [
                                      Expanded(
                                        child: Text(
                                          item.title,
                                          style: const TextStyle(
                                            fontWeight: FontWeight.bold,
                                            fontSize: 14,
                                          ),
                                        ),
                                      ),
                                      Text(
                                        formatChinaDateTime(item.timestamp),
                                        style: TextStyle(
                                          fontSize: 11,
                                          color: isDark
                                              ? AppColors.textSecondaryDark
                                              : AppColors.textSecondaryLight,
                                        ),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    item.detail.isEmpty ? '无补充说明' : item.detail,
                                    style: TextStyle(
                                      fontSize: 13,
                                      color: isDark
                                          ? AppColors.textSecondaryDark
                                          : AppColors.textSecondaryLight,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    '经办人: ${item.operator.isEmpty ? '系统' : item.operator}',
                                    style: const TextStyle(
                                      fontSize: 11,
                                      color: AppColors.primary,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      );
                    }),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    if (value.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 86,
            child: Text(
              label,
              style: const TextStyle(fontSize: 12, color: Colors.grey),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStaffActions(bool isAdmin, bool isDark) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E2633) : const Color(0xFFF0F6FF),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.primary.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                isAdmin
                    ? Icons.admin_panel_settings_rounded
                    : Icons.handyman_rounded,
                color: AppColors.primary,
                size: 20,
              ),
              const SizedBox(width: 8),
              Text(
                isAdmin ? '管理员操作控制台' : '技术员工作台',
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 15,
                  color: AppColors.primary,
                ),
              ),
              const Spacer(),
              if (_isProcessing)
                const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator.adaptive(strokeWidth: 2),
                ),
            ],
          ),
          const SizedBox(height: 12),
          if (_ticket.isOpen)
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                style: FilledButton.styleFrom(
                  backgroundColor: AppColors.primary,
                ),
                icon: const Icon(Icons.check_circle_outline),
                label: const Text(
                  '接受工单并排期 (接单)',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
                onPressed: _isProcessing ? null : _acceptTicket,
              ),
            ),
          if (_ticket.isAccepted)
            Row(
              children: [
                Expanded(
                  child: FilledButton.icon(
                    style: FilledButton.styleFrom(
                      backgroundColor: Colors.indigo,
                    ),
                    icon: const Icon(Icons.assignment_turned_in_outlined),
                    label: const Text('提交维修报告'),
                    onPressed: _isProcessing ? null : _showCommitDialog,
                  ),
                ),
                const SizedBox(width: 8),
                OutlinedButton(
                  onPressed: _isProcessing ? null : _dropTicket,
                  child: const Text('放弃工单'),
                ),
              ],
            ),
          if (_ticket.isCommitted && isAdmin)
            Row(
              children: [
                Expanded(
                  child: FilledButton.icon(
                    style: FilledButton.styleFrom(
                      backgroundColor: Colors.green,
                    ),
                    icon: const Icon(Icons.verified_outlined),
                    label: const Text('审核通过结单'),
                    onPressed: _isProcessing ? null : _closeTicket,
                  ),
                ),
                const SizedBox(width: 8),
                OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.orange,
                  ),
                  onPressed: _isProcessing ? null : _rejectCommit,
                  child: const Text('退回重测'),
                ),
              ],
            ),
          if (_ticket.isClosed)
            const Row(
              children: [
                Icon(Icons.check_circle, color: Colors.green, size: 18),
                SizedBox(width: 6),
                Text(
                  '该工单已全流程闭环归档',
                  style: TextStyle(
                    fontSize: 13,
                    color: Colors.green,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }

  Widget _buildClientActions(bool isDark) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E2633) : const Color(0xFFF0F6FF),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.primary.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          Expanded(
            child: OutlinedButton.icon(
              onPressed: _isProcessing ? null : _showEditDialog,
              icon: const Icon(Icons.edit_outlined, size: 18),
              label: const Text('修改信息'),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: OutlinedButton.icon(
              style: OutlinedButton.styleFrom(
                foregroundColor: Colors.redAccent,
              ),
              onPressed: _isProcessing ? null : _cancelTicket,
              icon: const Icon(Icons.cancel_outlined, size: 18),
              label: const Text('取消预约'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildErrorBanner(String message) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.statusPending.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: AppColors.statusPending.withValues(alpha: 0.3),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.cloud_off_rounded,
              size: 18, color: AppColors.statusPending),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              '完整工单加载失败，当前显示列表中的摘要：$message',
              style: const TextStyle(fontSize: 12, height: 1.4),
            ),
          ),
        ],
      ),
    );
  }
}

class _CommitReportData {
  final String description;
  final String size;
  final List<XFile> photos;

  const _CommitReportData({
    required this.description,
    required this.size,
    required this.photos,
  });
}
