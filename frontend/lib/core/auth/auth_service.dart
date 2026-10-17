import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:url_launcher/url_launcher.dart';

import 'logto_auth.dart';
import 'token_storage.dart';

class UserProfile {
  final String sub;
  final String name;
  final String email;
  final String? username;
  final String? avatar;
  final List<String> roles;
  final String? memberId;
  final String? saturdayRole;
  final String? accessToken;

  UserProfile({
    required this.sub,
    required this.name,
    required this.email,
    this.username,
    this.avatar,
    this.roles = const [],
    this.memberId,
    this.saturdayRole,
    this.accessToken,
  });

  static const Set<String> _adminRoleNames = {
    'repair admin',
    'repair-admin',
    'repair administrator',
    'dev admin',
    'dev-admin',
    'devadmin',
    'developer admin',
    'admin',
  };
  static const Set<String> _staffRoleNames = {
    'repair member',
    'repair-member',
    'repair staff',
    'technician',
    'member',
  };

  static bool _hasAnyRole(List<String> values, Set<String> candidates) =>
      values.any((value) => candidates.contains(value.trim().toLowerCase()));

  bool get isRepairAdmin => _hasAnyRole(roles, _adminRoleNames);
  bool get isRepairStaff =>
      isRepairAdmin || _hasAnyRole(roles, _staffRoleNames);

  /// 账号真实角色对应的最高权限名称（不受当前界面视角影响）。
  String get repairRoleName => isRepairAdmin
      ? '维修管理员'
      : isRepairStaff
          ? '维修技术员'
          : '报修用户';

  factory UserProfile.fromJson(Map<String, dynamic> json,
      {String? accessToken}) {
    return UserProfile(
      sub: json['sub']?.toString() ?? '',
      name: (json['name'] ?? json['username'] ?? json['sub'] ?? 'NBTCA 用户')
          .toString(),
      email: json['email']?.toString() ?? '',
      username: json['username']?.toString(),
      avatar: (json['picture'] ?? json['avatar'])?.toString(),
      roles: (json['roles'] as List? ?? const [])
          .map((e) => e.toString())
          .toList(growable: false),
      memberId: json['memberId']?.toString(),
      saturdayRole: json['saturdayRole']?.toString(),
      accessToken: accessToken,
    );
  }

  Map<String, dynamic> toJson() => {
        'sub': sub,
        'name': name,
        'email': email,
        'username': username,
        'picture': avatar,
        'roles': roles,
        'memberId': memberId,
        'saturdayRole': saturdayRole,
      };
}

class AuthException implements Exception {
  final String message;
  const AuthException(this.message);
  @override
  String toString() => message;
}

class AuthService extends ChangeNotifier {
  static final AuthService _instance = AuthService._internal();
  factory AuthService() => _instance;

  AuthService._internal()
      : _storage = TokenStorage(),
        _logtoDio = Dio(BaseOptions(
          baseUrl: LogtoAuth.endpoint,
          connectTimeout: const Duration(seconds: 15),
          receiveTimeout: const Duration(seconds: 15),
          headers: {'Accept': 'application/json'},
        )),
        _saturdayDio = Dio(BaseOptions(
          baseUrl: saturdayEndpoint,
          connectTimeout: const Duration(seconds: 12),
          receiveTimeout: const Duration(seconds: 12),
          headers: {'Accept': 'application/json'},
        ));

  @visibleForTesting
  AuthService.forTesting({
    Dio? logtoClient,
    Dio? saturdayClient,
    TokenStorage? storage,
  })  : _storage = storage ?? TokenStorage(),
        _logtoDio =
            logtoClient ?? Dio(BaseOptions(baseUrl: LogtoAuth.endpoint)),
        _saturdayDio =
            saturdayClient ?? Dio(BaseOptions(baseUrl: saturdayEndpoint));

  static const String officialRepairAuthUrl = 'https://nbtca.space/repair';
  static const String logtoEndpoint = LogtoAuth.endpoint;
  static const String saturdayEndpoint = 'https://api.nbtca.space/v2';

  final TokenStorage _storage;
  final Dio _logtoDio;
  final Dio _saturdayDio;

  UserProfile? _currentUser;
  String? _accessToken;
  String? _refreshToken;
  String? _idToken;
  DateTime? _expiresAt;
  bool _isAuthenticating = false;
  bool _initialized = false;
  int _sessionVersion = 0;
  Future<String?>? _refreshing;

  /// 只影响界面视角，不改写真实账号/token。null 表示自动使用最高权限。
  String? _viewRole;

  bool get isLoggedIn =>
      _currentUser != null && (_accessToken?.isNotEmpty ?? false);
  bool get isAuthenticating => _isAuthenticating;
  bool get isInitialized => _initialized;
  UserProfile? get currentUser => _currentUser;
  String? get accessToken => _accessToken;
  String? get idToken => _idToken;

  String get actualHighestRole {
    if (!isLoggedIn) return 'client';
    if (_currentUser!.isRepairAdmin) return 'admin';
    if (_currentUser!.isRepairStaff) return 'member';
    return 'client';
  }

  String get effectiveRole {
    if (!isLoggedIn) return 'client';
    switch (_viewRole) {
      case 'admin':
        if (_currentUser!.isRepairAdmin) return 'admin';
        break;
      case 'member':
        if (_currentUser!.isRepairStaff) return 'member';
        break;
      case 'client':
        return 'client';
    }
    return actualHighestRole;
  }

  String? get viewRole => _viewRole;
  bool get canViewAdmin => isLoggedIn && _currentUser!.isRepairAdmin;
  bool get canViewMember => isLoggedIn && _currentUser!.isRepairStaff;
  bool get isRepairAdmin => isLoggedIn && effectiveRole == 'admin';
  bool get isRepairStaff =>
      isLoggedIn && (effectiveRole == 'admin' || effectiveRole == 'member');

  String get effectiveRoleName {
    switch (effectiveRole) {
      case 'admin':
        return '维修管理员';
      case 'member':
        return '维修技术员';
      default:
        return '报修用户';
    }
  }

  String get actualRoleName => _currentUser?.repairRoleName ?? '未登录';

  /// 切换界面视角；不会修改 currentUser、token 或个人资料。
  void setViewRole(String? role) {
    if (!isLoggedIn) {
      _viewRole = null;
      notifyListeners();
      return;
    }
    final normalized = role?.trim().toLowerCase();
    if (normalized != null &&
        !const {'admin', 'member', 'client'}.contains(normalized)) {
      return;
    }
    if (normalized == 'admin' && !_currentUser!.isRepairAdmin) return;
    if (normalized == 'member' && !_currentUser!.isRepairStaff) return;
    _viewRole = normalized;
    notifyListeners();
  }

  void resetViewRole() => setViewRole(null);

  /// 从安全存储恢复登录态。先恢复缓存资料，再在后台校验/刷新，避免阻塞启动。
  Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;
    _viewRole = null;
    try {
      final stored = await _storage.read();
      if (stored != null) {
        _accessToken = stored.accessToken;
        _refreshToken = stored.refreshToken;
        _idToken = stored.idToken;
        _expiresAt = stored.expiresAt;
        if (stored.profile != null) {
          _currentUser = UserProfile.fromJson(
            stored.profile!,
            accessToken: stored.accessToken,
          );
        }
        notifyListeners();
        unawaited(_validateStoredSession());
      }
    } catch (_) {
      // 安全存储不可用时保持未登录状态，而不是伪造管理员身份。
      _accessToken = null;
      _currentUser = null;
    }
  }

  Future<void> _validateStoredSession() async {
    final version = _sessionVersion;
    try {
      final token = await getValidAccessToken();
      if (token == null) {
        logout();
        return;
      }
      final profile = await _fetchProfile(token, idToken: _idToken);
      if (version != _sessionVersion) return;
      _currentUser = profile;
      await _persistSession();
      notifyListeners();
    } on DioException catch (e) {
      if (e.response?.statusCode == 401 || e.response?.statusCode == 403) {
        logout();
      }
      // 网络错误时保留缓存身份，避免离线打开 App 被强制退出。
    } catch (_) {
      // 保持缓存身份。
    }
  }

  Future<UserProfile> _fetchProfile(String accessToken,
      {String? idToken}) async {
    Map<String, dynamic>? info;
    DioException? userInfoError;

    // 并发请求 Logto userinfo 与 Saturday member，避免串行等待造成登录超时。
    Future<Map<String, dynamic>?> loadUserInfo() async {
      try {
        final response = await _logtoDio.get<dynamic>(
          '/oidc/me',
          options: Options(headers: {'Authorization': 'Bearer $accessToken'}),
        );
        if (response.data is Map) {
          return Map<String, dynamic>.from(response.data as Map);
        }
      } on DioException catch (e) {
        userInfoError = e;
        if (kDebugMode) {
          final detail =
              e.response?.statusCode?.toString() ?? e.type.toString();
          debugPrint('Logto /oidc/me 失败（将回退 ID Token）: $detail');
        }
      }
      return null;
    }

    // Saturday /member 是维修权限的权威来源；普通报修用户会 401，忽略即可。
    Future<Map<String, dynamic>?> loadMember() async {
      try {
        final member = await _saturdayDio.get<dynamic>(
          '/member',
          options: Options(headers: {'Authorization': 'Bearer $accessToken'}),
        );
        if (member.data is Map) {
          return Map<String, dynamic>.from(member.data as Map);
        }
      } catch (_) {
        // 未绑定维修成员、或当前账号只是普通报修用户。
      }
      return null;
    }

    final results = await Future.wait([loadUserInfo(), loadMember()]);
    info = results[0];
    Map<String, dynamic>? memberData = results[1];

    final claims = LogtoAuth.parseJwtClaims(idToken);
    if (info == null && claims != null) {
      info = claims;
    }

    // 极端情况下 userinfo 与 ID Token 都没有，但 Saturday 已识别出会员。
    if (info == null && memberData != null) {
      info = <String, dynamic>{
        'sub': memberData['memberId']?.toString() ?? '',
        'name': memberData['alias'] ?? memberData['name'],
        'username': memberData['alias'],
        'picture': memberData['avatar'],
        'roles': const <String>[],
      };
    }

    if (info == null) {
      final status = userInfoError?.response?.statusCode;
      if (status == 401 || status == 403) {
        throw const AuthException('登录凭证已失效，请重新登录');
      }
      throw const AuthException('无法验证登录身份，请检查网络后重试');
    }

    final memberId = memberData?['memberId']?.toString();
    final sub = info['sub']?.toString().trim().isNotEmpty == true
        ? info['sub'].toString()
        : (memberId ?? '');
    if (sub.isEmpty) {
      throw const AuthException('身份服务未返回用户标识');
    }

    final roleSet = <String>{
      ...((info['roles'] as List?) ?? const [])
          .map((e) => e.toString())
          .where((e) => e.isNotEmpty),
    };
    // 如果 Logto roles 没下发，用 Saturday 的会员角色兜底，避免管理员/技术员
    // 登录后失去维修管理入口。
    final memberRole = memberData?['role']?.toString().toLowerCase();
    if (memberRole == 'admin') {
      roleSet.addAll(const ['Repair Admin', 'Repair Member']);
    } else if (memberRole == 'member') {
      roleSet.add('Repair Member');
    }

    final name = (info['name'] ??
            info['username'] ??
            memberData?['alias'] ??
            memberData?['name'] ??
            sub)
        .toString();
    final username =
        (info['username'] ?? info['preferred_username'] ?? memberData?['alias'])
            ?.toString();
    final avatar = (info['picture'] ?? info['avatar'] ?? memberData?['avatar'])
        ?.toString();

    return UserProfile(
      sub: sub,
      name: name,
      email: info['email']?.toString() ?? '',
      username: username,
      avatar: avatar,
      roles: List.unmodifiable(roleSet.toList(growable: false)),
      memberId: memberId,
      saturdayRole: memberRole,
      accessToken: accessToken,
    );
  }

  /// 用 WebView 拿到的授权码换取 token 后调用；写入安全存储并加载资料。
  Future<void> loginWithLogtoTokens(LogtoTokenSet tokens) async {
    final version = ++_sessionVersion;
    _viewRole = null;
    _isAuthenticating = true;
    notifyListeners();
    try {
      final profile = await _fetchProfile(
        tokens.accessToken,
        idToken: tokens.idToken,
      );
      if (version != _sessionVersion) return;
      _accessToken = tokens.accessToken;
      _refreshToken = tokens.refreshToken;
      _idToken = tokens.idToken;
      _expiresAt = tokens.expiresAt;
      _currentUser = profile;
      await _persistSession();
    } finally {
      if (version == _sessionVersion) {
        _isAuthenticating = false;
        notifyListeners();
      }
    }
  }

  /// 兼容旧调用（测试/离线 Mock）。真实登录应使用 [loginWithLogtoTokens]。
  Future<void> loginWithLogtoProfile({
    String? account,
    String? name,
    String? avatar,
    String? token,
  }) async {
    if (token == null || token.trim().isEmpty) {
      throw const AuthException('未取得登录凭证，请在官方登录页面完成认证');
    }

    if (token.startsWith('mock_') || token.startsWith('dev_')) {
      _accessToken = token;
      _refreshToken = null;
      _idToken = null;
      _expiresAt = null;
      _viewRole = null;
      _currentUser = UserProfile(
        sub: 'mock_sub_001',
        name: name ?? account ?? '测试用户',
        email: account ?? 'test@nbt.edu.cn',
        roles: const ['Repair Admin', 'Repair Member'],
        memberId: 'admin_01',
        accessToken: token,
      );
      notifyListeners();
      return;
    }

    await loginWithLogtoTokens(LogtoTokenSet(
      accessToken: token,
      idToken: null,
      refreshToken: null,
      expiresIn: 3600,
    ));
  }

  /// 业务请求前取一个有效 token；过期时自动 refresh。
  Future<String?> getValidAccessToken() async {
    final token = _accessToken;
    if (token == null || token.isEmpty) return null;
    if (token.startsWith('dev_') || token.startsWith('mock_')) return token;
    final expiresAt = _expiresAt;
    if (expiresAt == null ||
        expiresAt.isAfter(DateTime.now().add(const Duration(seconds: 60)))) {
      return token;
    }
    return _refreshAccessToken();
  }

  Future<String?> _refreshAccessToken() {
    final existing = _refreshing;
    if (existing != null) return existing;
    final future = _doRefresh();
    _refreshing = future;
    return future.whenComplete(() => _refreshing = null);
  }

  Future<String?> _doRefresh() async {
    final refreshToken = _refreshToken;
    if (refreshToken == null || refreshToken.isEmpty) {
      logout();
      return null;
    }
    try {
      final tokens = await LogtoAuth.refresh(
        refreshToken: refreshToken,
        client: _logtoDio,
      );
      _accessToken = tokens.accessToken;
      _refreshToken = tokens.refreshToken ?? refreshToken;
      _idToken = tokens.idToken ?? _idToken;
      _expiresAt = tokens.expiresAt;
      await _persistSession();
      notifyListeners();
      return _accessToken;
    } on DioException catch (e) {
      if (e.response?.statusCode == 400 || e.response?.statusCode == 401) {
        logout();
      }
      return null;
    } catch (_) {
      return null;
    }
  }

  Future<void> _persistSession() async {
    final user = _currentUser;
    try {
      await _storage.save(
        accessToken: _accessToken ?? '',
        refreshToken: _refreshToken,
        idToken: _idToken,
        expiresAt: _expiresAt,
        profile: user?.toJson(),
      );
    } catch (_) {
      // 存储失败不应导致已登录流程崩溃；下次启动会重新登录。
    }
  }

  /// 仅调试构建允许快速切换视角，正式包不再默认管理员。
  void switchDevRole(String role) {
    if (!kDebugMode) return;
    _sessionVersion++;
    List<String> roles;
    String name;
    String email;
    String memberId;

    if (role == 'Repair Admin') {
      roles = const ['Repair Admin', 'Repair Member'];
      name = '管理员 (Admin)';
      email = 'admin@nbt.edu.cn';
      memberId = 'admin_01';
    } else if (role == 'Repair Member') {
      roles = const ['Repair Member'];
      name = '技术员 (Member)';
      email = 'tech@nbt.edu.cn';
      memberId = 'tech_01';
    } else {
      roles = const [];
      name = '报修同学 (Client)';
      email = 'student@nbt.edu.cn';
      memberId = '';
    }

    _viewRole = null;
    _accessToken = 'dev_token_';
    _refreshToken = null;
    _idToken = null;
    _expiresAt = null;
    _currentUser = UserProfile(
      sub: 'dev_user_',
      name: name,
      email: email,
      roles: roles,
      memberId: memberId.isNotEmpty ? memberId : null,
      accessToken: _accessToken,
    );
    notifyListeners();
  }

  Future<bool> launchOfficialRepair() => launchUrl(
        Uri.parse(officialRepairAuthUrl),
        mode: LaunchMode.inAppBrowserView,
      );

  void logout() {
    _sessionVersion++;
    _isAuthenticating = false;
    _viewRole = null;
    _currentUser = null;
    _accessToken = null;
    _refreshToken = null;
    _idToken = null;
    _expiresAt = null;
    unawaited(_storage.clear());
    notifyListeners();
  }
}
