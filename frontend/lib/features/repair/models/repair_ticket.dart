class RepairTicket {
  final String id;
  final String title;
  final String deviceType;
  final String description;
  final String status;
  final String clientId;
  final String phone;
  final String qq;
  final String contactPreference;
  final String size;
  final List<String> photos;
  final List<TicketTimeline> timeline;
  final DateTime createdAt;
  final DateTime updatedAt;
  final String? memberId;
  final String? memberName;
  final String? memberAvatar;
  final String? closedByName;

  RepairTicket({
    required this.id,
    required this.title,
    required this.deviceType,
    required this.description,
    required this.status,
    this.clientId = '',
    this.phone = '',
    this.qq = '',
    this.contactPreference = '',
    this.size = '',
    this.photos = const [],
    this.timeline = const [],
    required this.createdAt,
    DateTime? updatedAt,
    this.memberId,
    this.memberName,
    this.memberAvatar,
    this.closedByName,
  }) : updatedAt = updatedAt ?? createdAt;

  factory RepairTicket.fromJson(Map<String, dynamic> json) =>
      RepairTicket.fromSaturdayEvent(json);

  /// 映射 Saturday v2 的 Event / PublicEvent 结构。
  /// 公开事件没有 phone/qq，只有本人或工作人员通过对应接口才能拿到。
  factory RepairTicket.fromSaturdayEvent(Map<String, dynamic> json) {
    final eventId = json['eventId']?.toString() ?? json['id']?.toString() ?? '';
    final model = json['model']?.toString() ?? '设备';
    final problem =
        json['problem']?.toString() ?? json['description']?.toString() ?? '';
    final rawStatus = json['status']?.toString() ?? 'open';
    final st = rawStatus.toLowerCase();
    final member = json['member'] is Map
        ? Map<String, dynamic>.from(json['member'] as Map)
        : null;
    final closedBy = json['closedBy'] is Map
        ? Map<String, dynamic>.from(json['closedBy'] as Map)
        : null;
    final logs = json['logs'] as List?;
    final memId =
        member?['memberId']?.toString() ?? json['memberId']?.toString();
    final memName = member?['alias']?.toString() ??
        member?['name']?.toString() ??
        json['memberName']?.toString();
    final memAvatar = member?['avatar']?.toString();

    List<TicketTimeline> tLine = [];
    if (logs != null && logs.isNotEmpty) {
      tLine = logs
          .whereType<Map>()
          .map((l) => TicketTimeline.fromSaturdayLog(
                Map<String, dynamic>.from(l),
              ))
          .toList();
    } else {
      tLine = [
        TicketTimeline(
          status: st,
          title: _statusToTitle(st),
          detail: problem,
          operator: memName ?? '系统',
          timestamp: _parseDate(json['gmtCreate']) ?? DateTime.now(),
        ),
      ];
    }

    final photos = <String>[];
    final rawPhotos = json['photos'];
    if (rawPhotos is List) {
      for (final p in rawPhotos) {
        if (p is String && p.isNotEmpty) {
          photos.add(p);
        } else if (p is Map && p['url'] != null) {
          photos.add(p['url'].toString());
        }
      }
    }

    final title = problem.split('\n').first.trim();
    return RepairTicket(
      id: eventId,
      title: title.isNotEmpty ? title : model,
      deviceType: model,
      description: problem,
      status: st,
      clientId: json['clientId']?.toString() ??
          json['client_id']?.toString() ??
          json['student_id']?.toString() ??
          '',
      phone: json['phone']?.toString() ?? json['contact']?.toString() ?? '',
      qq: json['qq']?.toString() ?? '',
      contactPreference: json['contactPreference']?.toString() ?? '',
      size: json['size']?.toString() ?? '',
      photos: photos,
      timeline: tLine,
      createdAt: _parseDate(json['gmtCreate']) ??
          _parseDate(json['created_at']) ??
          DateTime.now(),
      updatedAt: _parseDate(json['gmtModified']) ??
          _parseDate(json['updated_at']) ??
          _parseDate(json['gmtCreate']) ??
          _parseDate(json['created_at']),
      memberId: memId,
      memberName: memName,
      memberAvatar: memAvatar,
      closedByName:
          closedBy?['alias']?.toString() ?? closedBy?['name']?.toString(),
    );
  }

  static DateTime? _parseDate(dynamic value) {
    if (value == null) return null;
    if (value is DateTime) return value;
    return DateTime.tryParse(value.toString());
  }

  RepairTicket copyWith({
    String? id,
    String? title,
    String? deviceType,
    String? description,
    String? status,
    String? clientId,
    String? phone,
    String? qq,
    String? contactPreference,
    String? size,
    List<String>? photos,
    List<TicketTimeline>? timeline,
    DateTime? createdAt,
    DateTime? updatedAt,
    String? memberId,
    String? memberName,
    String? memberAvatar,
    String? closedByName,
  }) {
    return RepairTicket(
      id: id ?? this.id,
      title: title ?? this.title,
      deviceType: deviceType ?? this.deviceType,
      description: description ?? this.description,
      status: status ?? this.status,
      clientId: clientId ?? this.clientId,
      phone: phone ?? this.phone,
      qq: qq ?? this.qq,
      contactPreference: contactPreference ?? this.contactPreference,
      size: size ?? this.size,
      photos: photos ?? this.photos,
      timeline: timeline ?? this.timeline,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      memberId: memberId ?? this.memberId,
      memberName: memberName ?? this.memberName,
      memberAvatar: memberAvatar ?? this.memberAvatar,
      closedByName: closedByName ?? this.closedByName,
    );
  }

  /// 兼容旧 UI 的只读别名。
  String get studentId => clientId;
  String get contact {
    if (phone.isNotEmpty) return phone;
    if (qq.isNotEmpty) return 'QQ: $qq';
    return '';
  }

  String get deviceTypeName => deviceType;

  String get statusName => _statusToTitle(status);

  bool get isOpen => status == 'open';
  bool get isAccepted => status == 'accepted' || status == 'repairing';
  bool get isCommitted => status == 'committed';
  bool get isClosed => status == 'closed' || status == 'completed';
  bool get isCancelled => status == 'cancelled' || status == 'canceled';
  bool get isActive => isOpen || isAccepted || isCommitted;

  static String _statusToTitle(String status) {
    switch (status) {
      case 'open':
      case 'pending':
        return '待受理';
      case 'accepted':
      case 'repairing':
        return '维修中';
      case 'committed':
        return '待审核';
      case 'closed':
      case 'completed':
        return '已完成';
      case 'cancelled':
      case 'canceled':
        return '已取消';
      default:
        return '处理中';
    }
  }
}

class TicketTimeline {
  final String status;
  final String title;
  final String detail;
  final String operator;
  final DateTime timestamp;

  TicketTimeline({
    required this.status,
    required this.title,
    required this.detail,
    required this.operator,
    required this.timestamp,
  });

  factory TicketTimeline.fromSaturdayLog(Map<String, dynamic> json) {
    final action = json['action']?.toString() ?? '';
    return TicketTimeline(
      status: action,
      title: _actionToTitle(action),
      detail: json['description']?.toString() ?? '',
      operator: json['memberId']?.toString().trim() ?? '技术员',
      timestamp: (json['gmtCreate'] != null
              ? DateTime.tryParse(json['gmtCreate'].toString())
              : null) ??
          DateTime.now(),
    );
  }

  factory TicketTimeline.fromJson(Map<String, dynamic> json) {
    return TicketTimeline(
      status: json['status']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      detail: json['detail']?.toString() ?? '',
      operator: json['operator']?.toString() ?? '',
      timestamp: json['timestamp'] != null
          ? DateTime.tryParse(json['timestamp'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }

  static String _actionToTitle(String action) {
    switch (action) {
      case 'create':
        return '工单已提交';
      case 'accept':
        return '维修部已接单';
      case 'commit':
      case 'alterCommit':
        return '检修处理完毕';
      case 'close':
        return '工单已审核关闭';
      case 'reject':
        return '工单审核退回';
      case 'drop':
        return '工单已放弃受理';
      case 'cancel':
        return '工单已取消';
      case 'update':
        return '工单信息已更新';
      default:
        return '处理更新';
    }
  }
}
