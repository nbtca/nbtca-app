import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';

import '../../features/newsroom/models/news_article.dart';
import '../../features/repair/models/repair_ticket.dart';
import '../../features/tools/models/association_calendar.dart';
import '../../features/tools/models/service_status.dart';
import '../auth/auth_service.dart';
import '../config/app_settings.dart';
import 'api_endpoints.dart';

class ApiException implements Exception {
  final String message;
  final int? statusCode;

  const ApiException(this.message, {this.statusCode});

  @override
  String toString() => message;
}

class ApiClient {
  static final ApiClient _instance = ApiClient._internal();
  factory ApiClient() => _instance;

  ApiClient._internal() {
    _baseUrl = AppSettings.instance.apiBaseUrl;
    _bffDio = Dio(BaseOptions(
      baseUrl: _baseUrl,
      connectTimeout: const Duration(seconds: 8),
      receiveTimeout: const Duration(seconds: 12),
      headers: {
        "Accept": "application/json",
        "Content-Type": "application/json",
      },
    ));
    _saturdayDio = Dio(BaseOptions(
      baseUrl: AuthService.saturdayEndpoint,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 12),
      headers: {
        "Accept": "application/json",
        "Content-Type": "application/json",
      },
    ));
  }

  @visibleForTesting
  ApiClient.forTesting({Dio? bffClient, Dio? saturdayClient}) {
    _baseUrl = bffClient?.options.baseUrl ?? 'http://test.local/api/v1';
    _bffDio = bffClient ??
        Dio(BaseOptions(
            baseUrl: _baseUrl, connectTimeout: const Duration(seconds: 2)));
    _saturdayDio = saturdayClient ??
        Dio(BaseOptions(
          baseUrl: AuthService.saturdayEndpoint,
          connectTimeout: const Duration(seconds: 2),
        ));
  }

  late Dio _bffDio;
  late Dio _saturdayDio;
  late String _baseUrl;

  String get baseUrl => _baseUrl;

  void updateBaseUrl(String newUrl) {
    final normalized = _normalizeBaseUrl(newUrl);
    _baseUrl = normalized;
    _bffDio.options.baseUrl = normalized;
    unawaited(AppSettings.instance.setApiBaseUrl(normalized));
  }

  static String _normalizeBaseUrl(String url) {
    var normalized = url.trim();
    if (normalized.isEmpty) return ApiEndpoints.defaultBaseUrl;
    if (!normalized.startsWith('http://') &&
        !normalized.startsWith('https://')) {
      normalized = 'http://$normalized';
    }
    while (normalized.endsWith('/')) {
      normalized = normalized.substring(0, normalized.length - 1);
    }
    return normalized;
  }

  Future<Options> _authOptions({bool required = false}) async {
    final token = await AuthService().getValidAccessToken();
    if (token == null || token.isEmpty) {
      if (required) {
        throw const ApiException('请先登录 NBTCA 账号', statusCode: 401);
      }
      return Options();
    }
    return Options(headers: {"Authorization": "Bearer $token"});
  }

  Map<String, dynamic> _query({
    String? status,
    int limit = 50,
    int offset = 0,
  }) {
    final query = <String, dynamic>{
      "limit": limit,
      "offset": offset,
      "order": "DESC",
    };
    if (status != null && status.isNotEmpty && status != 'all') {
      query["status"] = status;
    }
    return query;
  }

  List<RepairTicket> _parseTickets(dynamic data) {
    if (data is List) {
      return data
          .whereType<Map>()
          .map((e) => RepairTicket.fromSaturdayEvent(
                Map<String, dynamic>.from(e),
              ))
          .toList();
    }
    return <RepairTicket>[];
  }

  RepairTicket _parseTicket(dynamic data) {
    if (data is Map) {
      return RepairTicket.fromSaturdayEvent(Map<String, dynamic>.from(data));
    }
    throw const ApiException('维修系统返回了无效的工单数据');
  }

  Never _throwDio(DioException e, String action) {
    final status = e.response?.statusCode;
    final detail = _errorDetail(e.response?.data);
    if (status == 401 || status == 403) {
      throw ApiException('登录凭证无效或无权限，请重新登录后重试', statusCode: status);
    }
    throw ApiException(
      detail.isNotEmpty ? '$action失败：$detail' : '$action失败，请检查网络后重试',
      statusCode: status,
    );
  }

  String _errorDetail(dynamic data) {
    if (data is Map) {
      for (final key in ['detail', 'message', 'title', 'error']) {
        final value = data[key];
        if (value != null && value.toString().trim().isNotEmpty) {
          return value.toString();
        }
      }
    }
    if (data is String && data.trim().isNotEmpty) return data.trim();
    return '';
  }

  dynamic _unwrapBff(Response<dynamic> response) {
    final body = response.data;
    if (body is Map && body['code'] is num) {
      final code = (body['code'] as num).toInt();
      if (code != 0) {
        throw ApiException(
          body['message']?.toString() ?? '服务返回错误',
          statusCode: response.statusCode,
        );
      }
      return body['data'];
    }
    return body;
  }

  // ===================== 报修（Saturday 直连） =====================

  /// 当前登录用户的报修记录。未登录返回空列表，不展示他人公开工单。
  Future<List<RepairTicket>> getRepairTickets({
    String? status,
    int limit = 50,
    int offset = 0,
  }) async {
    final auth = AuthService();
    if (!auth.isLoggedIn) return <RepairTicket>[];
    if (auth.isRepairStaff) {
      return getMemberTickets(status: status, limit: limit, offset: offset);
    }
    return getClientTickets(status: status, limit: limit, offset: offset);
  }

  Future<List<RepairTicket>> getClientTickets({
    String? status,
    int limit = 50,
    int offset = 0,
  }) async {
    try {
      final res = await _saturdayDio.get<dynamic>(
        '/client/events',
        queryParameters: _query(status: status, limit: limit, offset: offset),
        options: await _authOptions(required: true),
      );
      return _parseTickets(res.data);
    } on DioException catch (e) {
      _throwDio(e, '获取我的维修记录');
    }
  }

  Future<List<RepairTicket>> getMemberTickets({
    String? status,
    int limit = 50,
    int offset = 0,
  }) async {
    try {
      final res = await _saturdayDio.get<dynamic>(
        '/member/events',
        queryParameters: _query(status: status, limit: limit, offset: offset),
        options: await _authOptions(required: true),
      );
      return _parseTickets(res.data);
    } on DioException catch (e) {
      _throwDio(e, '获取技术员工单');
    }
  }

  /// 公开工单队列只用于工作人员/管理员的接单与审核大厅。
  Future<List<RepairTicket>> getPublicTickets({
    String? status,
    int limit = 50,
    int offset = 0,
  }) async {
    try {
      final res = await _saturdayDio.get<dynamic>(
        '/events',
        queryParameters: _query(status: status, limit: limit, offset: offset),
      );
      return _parseTickets(res.data);
    } on DioException catch (e) {
      _throwDio(e, '获取公开工单');
    }
  }

  /// 详情按角色走不同接口：本人拿到 phone/qq，工作人员拿到完整日志。
  Future<RepairTicket> getTicketDetail(
    String id, {
    RepairTicket? fallback,
  }) async {
    final auth = AuthService();
    final eid = Uri.encodeComponent(id);

    Future<RepairTicket> tryGet(String path, {bool required = true}) async {
      final res = await _saturdayDio.get<dynamic>(
        path,
        options: await _authOptions(required: required),
      );
      return _parseTicket(res.data);
    }

    final paths = <String>[];
    final requiresAuth = <bool>[];
    if (!auth.isLoggedIn) {
      paths.add('/events/$eid');
      requiresAuth.add(false);
    } else if (auth.isRepairAdmin) {
      paths.add('/events/$eid');
      requiresAuth.add(true);
    } else if (auth.isRepairStaff) {
      paths.addAll(['/member/events/$eid', '/events/$eid']);
      requiresAuth.addAll([true, false]);
    } else {
      paths.addAll(['/client/events/$eid', '/events/$eid']);
      requiresAuth.addAll([true, false]);
    }

    DioException? lastError;
    for (var i = 0; i < paths.length; i++) {
      try {
        return await tryGet(paths[i], required: requiresAuth[i]);
      } on DioException catch (e) {
        lastError = e;
        if (e.response?.statusCode == 401 || e.response?.statusCode == 403) {
          continue;
        }
      } catch (_) {
        continue;
      }
    }
    if (fallback != null) return fallback;
    if (lastError != null) _throwDio(lastError, '获取工单详情');
    throw const ApiException('无法获取工单详情');
  }

  Future<RepairTicket> createTicket(Map<String, dynamic> data) async {
    final problem =
        (data["problem"] ?? data["description"] ?? data["title"] ?? '')
            .toString()
            .trim();
    final modelName =
        (data["model"] ?? data["device_type"] ?? '').toString().trim();
    final phone = (data["phone"] ?? data["contact"] ?? '').toString().trim();
    final qq = (data["qq"] ?? '').toString().trim();

    if (problem.isEmpty) {
      throw const ApiException('问题描述不能为空');
    }
    if (phone.isEmpty) {
      throw const ApiException('电话号码不能为空');
    }

    final body = <String, dynamic>{
      "problem": problem,
      "model": modelName.isEmpty ? '设备' : modelName,
      "phone": phone,
      "qq": qq,
      "contactPreference": "phone",
    };

    try {
      final res = await _saturdayDio.post<dynamic>(
        '/client/event',
        data: body,
        options: await _authOptions(required: true),
      );
      return _parseTicket(res.data);
    } on DioException catch (e) {
      _throwDio(e, '提交报修工单');
    }
  }

  Future<RepairTicket> updateClientEvent(
    String id,
    Map<String, dynamic> data,
  ) async {
    final eid = Uri.encodeComponent(id);
    try {
      final res = await _saturdayDio.patch<dynamic>(
        '/client/events/$eid',
        data: data,
        options: await _authOptions(required: true),
      );
      return _parseTicket(res.data);
    } on DioException catch (e) {
      _throwDio(e, '更新报修工单');
    }
  }

  Future<void> cancelClientEvent(String id) async {
    final eid = Uri.encodeComponent(id);
    try {
      await _saturdayDio.delete<dynamic>(
        '/client/events/$eid',
        options: await _authOptions(required: true),
      );
    } on DioException catch (e) {
      _throwDio(e, '取消报修工单');
    }
  }

  Future<RepairTicket> acceptEvent(String eventId) async {
    final eid = Uri.encodeComponent(eventId);
    try {
      final res = await _saturdayDio.post<dynamic>(
        '/member/events/$eid/accept',
        options: await _authOptions(required: true),
      );
      return _parseTicket(res.data);
    } on DioException catch (e) {
      _throwDio(e, '接单');
    }
  }

  Future<RepairTicket> dropEvent(String eventId) async {
    final eid = Uri.encodeComponent(eventId);
    try {
      final res = await _saturdayDio.delete<dynamic>(
        '/member/events/$eid/accept',
        options: await _authOptions(required: true),
      );
      return _parseTicket(res.data);
    } on DioException catch (e) {
      _throwDio(e, '放弃工单');
    }
  }

  Future<RepairTicket> commitEvent(
    String eventId,
    String content, {
    String? size,
  }) async {
    final eid = Uri.encodeComponent(eventId);
    try {
      final res = await _saturdayDio.post<dynamic>(
        '/member/events/$eid/commit',
        data: {
          "content": content,
          if (size != null && size.isNotEmpty) "size": size,
        },
        options: await _authOptions(required: true),
      );
      return _parseTicket(res.data);
    } on DioException catch (e) {
      _throwDio(e, '提交维修报告');
    }
  }

  Future<RepairTicket> uploadEventPhotos(
    String eventId,
    List<XFile> photos,
  ) async {
    final eid = Uri.encodeComponent(eventId);
    try {
      final formData = FormData();
      for (final photo in photos) {
        formData.files.add(
          MapEntry(
            'photos',
            MultipartFile.fromBytes(
              await photo.readAsBytes(),
              filename: photo.name,
            ),
          ),
        );
      }
      final res = await _saturdayDio.post<dynamic>(
        '/member/events/$eid/photos',
        data: formData,
        options: await _authOptions(required: true),
      );
      return _parseTicket(res.data);
    } on DioException catch (e) {
      _throwDio(e, '上传现场照片');
    }
  }

  Future<RepairTicket> alterCommitEvent(String eventId, String content) async {
    final eid = Uri.encodeComponent(eventId);
    try {
      final res = await _saturdayDio.patch<dynamic>(
        '/member/events/$eid/commit',
        data: {"content": content},
        options: await _authOptions(required: true),
      );
      return _parseTicket(res.data);
    } on DioException catch (e) {
      _throwDio(e, '修改维修报告');
    }
  }

  Future<RepairTicket> closeEvent(String eventId) async {
    final eid = Uri.encodeComponent(eventId);
    try {
      final res = await _saturdayDio.post<dynamic>(
        '/events/$eid/close',
        options: await _authOptions(required: true),
      );
      return _parseTicket(res.data);
    } on DioException catch (e) {
      _throwDio(e, '审核结单');
    }
  }

  Future<RepairTicket> rejectCommitEvent(String eventId) async {
    final eid = Uri.encodeComponent(eventId);
    try {
      final res = await _saturdayDio.delete<dynamic>(
        '/events/$eid/commit',
        options: await _authOptions(required: true),
      );
      return _parseTicket(res.data);
    } on DioException catch (e) {
      _throwDio(e, '退回工单');
    }
  }

  // ===================== Newsroom（BFF） =====================

  Future<NewsFeedData> getNewsFeed() async {
    try {
      final res = await _bffDio.get<dynamic>(ApiEndpoints.newsFeed);
      final data = _unwrapBff(res);
      if (data is Map) {
        final feed = NewsFeedData.fromJson(Map<String, dynamic>.from(data));
        if (feed.all.isEmpty) {
          // 后端在 RSS 上游失败时按契约返回空 feed；此时展示离线内容并提示。
          return _offlineNewsFeed();
        }
        return feed;
      }
      throw const ApiException('资讯接口返回了无效数据');
    } catch (e) {
      if (kDebugMode) debugPrint('获取线上资讯失败，使用离线内容: $e');
      return _offlineNewsFeed();
    }
  }

  Future<NewsArticle> getArticle(String id) async {
    try {
      final res = await _bffDio.get<dynamic>(ApiEndpoints.articleDetail(id));
      final data = _unwrapBff(res);
      if (data is Map) {
        return NewsArticle.fromJson(Map<String, dynamic>.from(data));
      }
      throw const ApiException('文章接口返回了无效数据');
    } on DioException catch (e) {
      final detail = _errorDetail(e.response?.data);
      throw ApiException(
        detail.isNotEmpty ? detail : '文章加载失败，请检查网络后重试',
        statusCode: e.response?.statusCode,
      );
    }
  }

  // ===================== Tools（BFF） =====================

  Future<AssociationCalendar> getAssociationCalendar() async {
    try {
      final res = await _bffDio.get<dynamic>(ApiEndpoints.associationCalendar);
      final data = _unwrapBff(res);
      if (data is Map) {
        return AssociationCalendar.fromJson(
          Map<String, dynamic>.from(data),
        );
      }
      throw const ApiException('协会日历接口返回了无效数据');
    } on DioException catch (e) {
      _throwDio(e, '获取协会公共日历');
    }
  }

  Future<ServiceStatus> getServiceStatus() async {
    try {
      final res = await _bffDio.get<dynamic>(ApiEndpoints.infrastructure);
      final data = _unwrapBff(res);
      if (data is Map) {
        return ServiceStatus.fromJson(Map<String, dynamic>.from(data));
      }
      if (data is List) {
        return _legacyStatus(data);
      }
      throw const ApiException('服务状态接口返回了无效数据');
    } on DioException catch (e) {
      final detail = _errorDetail(e.response?.data);
      return ServiceStatus.offline(
        detail.isNotEmpty ? detail : '服务状态加载失败，请检查网络后重试',
      );
    }
  }

  ServiceStatus _legacyStatus(List<dynamic> nodes) {
    final components = nodes.whereType<Map>().map((e) {
      final m = Map<String, dynamic>.from(e);
      return ServiceComponent(
        id: m['endpoint']?.toString() ?? '',
        name: m['name']?.toString() ?? '',
        state: m['status']?.toString() ?? 'unknown',
        error: m['note']?.toString(),
      );
    }).toList();
    return ServiceStatus(
      state: components.every((c) => c.state == 'operational')
          ? 'operational'
          : 'degraded',
      groups: [
        ServiceGroup(name: '服务节点', infra: false, components: components),
      ],
    );
  }

  Future<Map<String, dynamic>> diagnoseIssue(
    String deviceType,
    String desc,
  ) async {
    try {
      final res =
          await _bffDio.post<dynamic>(ApiEndpoints.repairDiagnose, data: {
        "device_type": deviceType,
        "description": desc,
      });
      final data = _unwrapBff(res);
      if (data is Map) return Map<String, dynamic>.from(data);
    } catch (_) {
      // 离线兜底，明确标记 source=offline。
    }

    return {
      "source": "offline",
      "possible_causes": ["硬件积灰散热不良或硅脂干涸", "驱动组件冲突或硬盘坏道", "系统引导与配置异常"],
      "self_help_steps": [
        "长按开机键 30 秒释放主板静电后重新通电",
        "使用 Dism++ 清理系统冗余与启动项",
      ],
      "recommended_tools": ["Dism++", "MemTest64"],
      "suggest_repair": true,
      "summary": "根据故障描述，建议携带设备参加本周六计算机协会义诊，由现场技术员进行深入检修测试。",
    };
  }

  NewsFeedData _offlineNewsFeed() {
    return NewsFeedData(
      isOffline: true,
      categories: const ['技术', '活动'],
      featured: [
        NewsArticle(
          id: "PhoNBT2024",
          title: "影留宁理 - 2024年度摄影展与毕业季活动回顾",
          category: "活动",
          published: "2024 年 6 月 18 日",
          author: "NBTCA 媒体部",
          summary: "光影定格青春，计算机协会联合摄影爱好者呈现宁理校园夏日记忆纪实。",
          coverUrl:
              "https://oss.nbtca.space/blog/clas/YQL05614-5xNq3t-mid-ulLkGV.jpeg",
          articleUrl: "https://nbtca.space/posts/PhoNBT2024",
          tags: ["摄影展", "毕业季", "校园生活"],
        ),
      ],
      recent: [
        NewsArticle(
          id: "go-struct-craft-1",
          title: "Go 结构体精要 1：空结构体设计",
          category: "技术",
          published: "2026 年 3 月 18 日",
          author: "Dev Team",
          summary: "深入解析 struct{} 的零内存开销特性在信号通道、集合 Set 以及锁字段中的实战用法。",
          coverUrl:
              "https://oss.nbtca.space/blog/clas/image-20220419101405456-wHMqwe.jpeg",
          articleUrl:
              "https://nbtca.space/posts/blogs/Tech/GO/go-struct-craft-1-empty-struct",
          tags: ["Golang", "性能优化"],
        ),
      ],
    );
  }
}
