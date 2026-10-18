import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nbtca_app/core/auth/auth_service.dart';
import 'package:nbtca_app/core/auth/logto_auth.dart';
import 'package:nbtca_app/core/network/api_client.dart';
import 'package:nbtca_app/features/repair/models/repair_ticket.dart';

class _FakeAdapter implements HttpClientAdapter {
  _FakeAdapter(this.handler);

  final Future<ResponseBody> Function(RequestOptions options) handler;
  final List<RequestOptions> requests = [];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return handler(options);
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody _json(Object body, [int status = 200]) {
  return ResponseBody.fromString(
    jsonEncode(body),
    status,
    headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    },
  );
}

void main() {
  setUp(() async {
    FlutterSecureStorage.setMockInitialValues({});
    await AuthService().loginWithLogtoProfile(token: 'mock_test_token');
  });

  group('Logto PKCE', () {
    test('S256 challenge matches RFC 7636 vector', () {
      const verifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';
      expect(
        LogtoAuth.codeChallenge(verifier),
        'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM',
      );
    });

    test('authorize URL contains PKCE and callback', () {
      final url = LogtoAuth.buildAuthorizeUrl(
        codeChallenge: 'test-challenge',
        state: 'test-state',
      );
      expect(url.host, 'auth.app.nbtca.space');
      expect(url.queryParameters['client_id'], LogtoAuth.appId);
      expect(url.queryParameters['code_challenge_method'], 'S256');
      expect(url.queryParameters['redirect_uri'], LogtoAuth.redirectUri);
      expect(url.queryParameters.containsKey('resource'), isFalse);
    });

    test('parses JWT claims safely', () {
      final payload = base64Url
          .encode(utf8.encode(jsonEncode({'sub': 'abc', 'name': '宁理同学'})))
          .replaceAll('=', '');
      final claims = LogtoAuth.parseJwtClaims('header.$payload.signature');
      expect(claims?['sub'], 'abc');
      expect(claims?['name'], '宁理同学');
    });

    test('falls back to ID token + Saturday member when /oidc/me rejects token',
        () async {
      final logto = Dio(BaseOptions(baseUrl: 'https://auth.test'));
      logto.httpClientAdapter = _FakeAdapter(
        (options) async => _json({'error': 'unauthorized'}, 401),
      );
      final saturday = Dio(BaseOptions(baseUrl: 'https://saturday.test/v2'));
      saturday.httpClientAdapter = _FakeAdapter(
        (options) async => _json({
          'memberId': 'tech_01',
          'alias': '技术员小张',
          'role': 'admin',
          'avatar': '',
        }),
      );
      final auth = AuthService.forTesting(
        logtoClient: logto,
        saturdayClient: saturday,
      );
      final payload = base64Url
          .encode(utf8.encode(jsonEncode({
            'sub': 'logto-user-1',
            'name': '宁理同学',
            'email': 'student@nbt.edu.cn',
            'roles': <String>[],
          })))
          .replaceAll('=', '');
      final idToken = 'header.$payload.signature';

      await auth.loginWithLogtoTokens(LogtoTokenSet(
        accessToken: 'resource-token',
        idToken: idToken,
        refreshToken: 'refresh-token',
        expiresIn: 3600,
      ));

      expect(auth.currentUser?.name, '宁理同学');
      expect(auth.currentUser?.email, 'student@nbt.edu.cn');
      expect(auth.currentUser?.memberId, 'tech_01');
      expect(auth.actualHighestRole, 'admin');
      expect(auth.effectiveRole, 'admin');
      expect(auth.isRepairAdmin, isTrue);

      // 切换界面视角不能改写真实账号，且默认应自动使用最高权限。
      final identity = auth.currentUser;
      auth.setViewRole('member');
      expect(auth.currentUser, same(identity));
      expect(auth.effectiveRole, 'member');
      expect(auth.isRepairAdmin, isFalse);
      expect(auth.isRepairStaff, isTrue);
      auth.setViewRole('client');
      expect(auth.isRepairStaff, isFalse);
      auth.resetViewRole();
      expect(auth.effectiveRole, 'admin');
    });

    test('dev admin role is treated as the highest repair permission', () {
      final profile = UserProfile(
        sub: 'dev-admin',
        name: 'Dev Admin',
        email: 'dev@nbt.edu.cn',
        roles: const ['dev admin', 'Repair Member'],
      );
      expect(profile.isRepairAdmin, isTrue);
      expect(profile.isRepairStaff, isTrue);
      expect(profile.repairRoleName, '维修管理员');
    });
  });

  group('ApiClient Saturday integration', () {
    test('createTicket sends Bearer token and correct client endpoint',
        () async {
      final adapter = _FakeAdapter((options) async => _json({
            'eventId': 999,
            'clientId': 1,
            'model': 'ThinkPad X1',
            'problem': '键盘失灵',
            'phone': '13800138000',
            'qq': '10086',
            'contactPreference': 'phone',
            'status': 'open',
            'memberId': '',
            'member': null,
            'closedById': '',
            'closedBy': null,
            'size': '',
            'logs': [],
            'photos': [],
            'gmtCreate': '2026-10-17T12:00:00+08:00',
            'gmtModified': '2026-10-17T12:00:00+08:00',
          }));
      final saturday = Dio(BaseOptions(baseUrl: 'https://saturday.test/v2'));
      saturday.httpClientAdapter = adapter;
      final client = ApiClient.forTesting(
        bffClient: Dio(BaseOptions(baseUrl: 'http://bff.test/api/v1')),
        saturdayClient: saturday,
      );

      final ticket = await client.createTicket({
        'problem': '键盘失灵',
        'model': 'ThinkPad X1',
        'phone': '13800138000',
        'qq': '10086',
      });

      expect(adapter.requests.single.path, '/client/event');
      expect(adapter.requests.single.headers['Authorization'],
          'Bearer mock_test_token');
      expect((adapter.requests.single.data as Map)['phone'], '13800138000');
      expect(ticket.id, '999');
      expect(ticket.phone, '13800138000');
      expect(ticket.statusName, '待受理');
    });

    test('acceptEvent includes eventId in URL', () async {
      final adapter = _FakeAdapter((options) async => _json({
            'eventId': 750,
            'clientId': 934,
            'model': 'Acer',
            'problem': '进水',
            'status': 'accepted',
            'member': {'memberId': 'admin_01', 'alias': '管理员'},
            'logs': [],
            'photos': [],
            'gmtCreate': '2026-10-17T12:00:00+08:00',
            'gmtModified': '2026-10-17T12:00:00+08:00',
          }));
      final saturday = Dio(BaseOptions(baseUrl: 'https://saturday.test/v2'));
      saturday.httpClientAdapter = adapter;
      final client = ApiClient.forTesting(
        bffClient: Dio(BaseOptions(baseUrl: 'http://bff.test/api/v1')),
        saturdayClient: saturday,
      );

      await client.acceptEvent('750');

      expect(adapter.requests.single.path, '/member/events/750/accept');
      expect(adapter.requests.single.method, 'POST');
    });

    test('getClientTickets uses /client/events and parses full fields',
        () async {
      final adapter = _FakeAdapter((options) async => _json([
            {
              'eventId': 751,
              'clientId': 1141,
              'model': 'Dell',
              'problem': '蓝屏',
              'phone': '13900139000',
              'qq': '12345',
              'status': 'committed',
              'member': {'memberId': 'tech_01', 'alias': '张同学'},
              'logs': [
                {
                  'logId': 1,
                  'action': 'commit',
                  'description': '已更换硬盘',
                  'memberId': 'tech_01',
                  'gmtCreate': '2026-10-17T13:00:00+08:00',
                }
              ],
              'photos': [],
              'gmtCreate': '2026-10-17T12:00:00+08:00',
              'gmtModified': '2026-10-17T13:00:00+08:00',
            }
          ]));
      final saturday = Dio(BaseOptions(baseUrl: 'https://saturday.test/v2'));
      saturday.httpClientAdapter = adapter;
      final client = ApiClient.forTesting(
        bffClient: Dio(BaseOptions(baseUrl: 'http://bff.test/api/v1')),
        saturdayClient: saturday,
      );

      final tickets = await client.getClientTickets();
      expect(adapter.requests.single.path, '/client/events');
      expect(adapter.requests.single.queryParameters['order'], 'DESC');
      expect(tickets.single.phone, '13900139000');
      expect(tickets.single.memberName, '张同学');
      expect(tickets.single.timeline.single.title, '检修处理完毕');
      expect(tickets.single.statusName, '待审核');
    });

    test('admin detail uses member endpoint for full contacts', () async {
      final adapter = _FakeAdapter((options) async => _json({
            'eventId': 750,
            'clientId': 934,
            'model': 'Acer',
            'problem': '进水',
            'phone': '13500135000',
            'status': 'open',
            'member': null,
            'logs': [],
            'photos': [],
            'gmtCreate': '2026-10-17T12:00:00+08:00',
            'gmtModified': '2026-10-17T12:00:00+08:00',
          }));
      final saturday = Dio(BaseOptions(baseUrl: 'https://saturday.test/v2'));
      saturday.httpClientAdapter = adapter;
      final client = ApiClient.forTesting(
        bffClient: Dio(BaseOptions(baseUrl: 'http://bff.test/api/v1')),
        saturdayClient: saturday,
      );

      final ticket = await client.getTicketDetail('750');
      expect(adapter.requests.single.path, '/member/events/750');
      expect(ticket.phone, '13500135000');
    });
  });

  group('Model and BFF parsing', () {
    test('RepairTicket.fromJson parses legacy numeric fields', () {
      final ticket = RepairTicket.fromJson({
        'id': 'T123',
        'title': '测试标题',
        'device_type': 'laptop',
        'description': '描述',
        'status': 'pending',
        'student_id': 3230101888,
        'created_at': '2026-10-12T10:00:00.000Z',
      });
      expect(ticket.id, 'T123');
      expect(ticket.studentId, '3230101888');
      expect(ticket.statusName, '待受理');
    });

    test('getNewsFeed parses categories from BFF', () async {
      final bff = Dio(BaseOptions(baseUrl: 'http://bff.test/api/v1'));
      bff.httpClientAdapter = _FakeAdapter((options) async => _json({
            'code': 0,
            'message': 'success',
            'data': {
              'featured': [
                {
                  'id': 'a',
                  'title': 'A',
                  'category': '技术',
                  'published': '2026 年 1 月 1 日',
                  'author': 'NBTCA',
                  'summary': '',
                  'cover_url': '',
                  'article_url': 'https://nbtca.space/posts/a',
                  'tags': ['技术'],
                },
              ],
              'recent': [
                {
                  'id': 'b',
                  'title': 'B',
                  'category': '生活',
                  'published': '2026 年 1 月 2 日',
                  'author': 'NBTCA',
                  'summary': '',
                  'cover_url': '',
                  'article_url': 'https://nbtca.space/posts/b',
                  'tags': ['生活'],
                },
              ],
              'categories': ['技术', '生活'],
            },
          }));
      final client = ApiClient.forTesting(
        bffClient: bff,
        saturdayClient: Dio(BaseOptions(baseUrl: 'https://saturday.test/v2')),
      );

      final feed = await client.getNewsFeed();
      expect(feed.categories, ['技术', '生活']);
      expect(feed.isOffline, isFalse);
    });
  });
}
