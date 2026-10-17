import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';

/// Logto 原生/SPA 公共客户端的 Authorization Code + PKCE 实现。
///
/// 使用 nbtca.space 官网同一个 Logto app id，redirect_uri 为官网已注册的
/// https://nbtca.space/callback。App 在 WebView 中拦截该回调，取 code 后
/// 用 PKCE verifier 在本地换取 token，不依赖读取网页 localStorage。
class LogtoAuth {
  LogtoAuth._();

  static const String endpoint = 'https://auth.app.nbtca.space';
  static const String appId = 'a3tt7oyxeounfiqqugiah';
  static const String redirectUri = 'https://nbtca.space/callback';
  static const String scope =
      'openid profile email offline_access roles custom_data';

  static final Random _random = Random.secure();

  static String generateCodeVerifier([int length = 64]) {
    const alphabet =
        'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
    final buffer = StringBuffer();
    for (var i = 0; i < length; i++) {
      buffer.write(alphabet[_random.nextInt(alphabet.length)]);
    }
    return buffer.toString();
  }

  static String codeChallenge(String verifier) {
    final digest = sha256.convert(ascii.encode(verifier));
    return base64Url.encode(digest.bytes).replaceAll('=', '');
  }

  static String generateState([int bytes = 24]) {
    final values = List<int>.generate(bytes, (_) => _random.nextInt(256));
    return base64Url.encode(values).replaceAll('=', '');
  }

  static Uri buildAuthorizeUrl({
    required String codeChallenge,
    required String state,
  }) {
    return Uri.https('auth.app.nbtca.space', '/oidc/auth', {
      'client_id': appId,
      'redirect_uri': redirectUri,
      'response_type': 'code',
      'scope': scope,
      'code_challenge': codeChallenge,
      'code_challenge_method': 'S256',
      'state': state,
    });
  }

  static bool isRedirect(Uri uri) {
    final expected = Uri.parse(redirectUri);
    return uri.scheme == expected.scheme &&
        uri.host == expected.host &&
        uri.path == expected.path;
  }

  static Future<LogtoTokenSet> exchangeCode({
    required String code,
    required String codeVerifier,
    Dio? client,
  }) async {
    final dio = client ?? _defaultDio();
    final response = await dio.post<dynamic>(
      '/oidc/token',
      data: {
        'grant_type': 'authorization_code',
        'client_id': appId,
        'code': code,
        'redirect_uri': redirectUri,
        'code_verifier': codeVerifier,
      },
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
    return LogtoTokenSet.fromJson(_asMap(response.data));
  }

  static Future<LogtoTokenSet> refresh({
    required String refreshToken,
    Dio? client,
  }) async {
    final dio = client ?? _defaultDio();
    final response = await dio.post<dynamic>(
      '/oidc/token',
      data: {
        'grant_type': 'refresh_token',
        'client_id': appId,
        'refresh_token': refreshToken,
      },
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
    return LogtoTokenSet.fromJson(_asMap(response.data));
  }

  static Dio _defaultDio() {
    return Dio(BaseOptions(
      baseUrl: endpoint,
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 15),
      headers: {'Accept': 'application/json'},
    ));
  }

  static Map<String, dynamic>? parseJwtClaims(String? token) {
    if (token == null || token.isEmpty) return null;
    final parts = token.split('.');
    if (parts.length < 2) return null;
    try {
      var payload = parts[1].replaceAll('-', '+').replaceAll('_', '/');
      payload = payload.padRight((payload.length + 3) & ~3, '=');
      final decoded = utf8.decode(base64.decode(payload));
      final parsed = json.decode(decoded);
      return parsed is Map ? Map<String, dynamic>.from(parsed) : null;
    } catch (_) {
      return null;
    }
  }

  static Map<String, dynamic> _asMap(dynamic value) {
    if (value is Map) return Map<String, dynamic>.from(value);
    throw const FormatException(
        'Logto token endpoint returned a non-object body');
  }
}

class LogtoTokenSet {
  final String accessToken;
  final String? idToken;
  final String? refreshToken;
  final int expiresIn;

  const LogtoTokenSet({
    required this.accessToken,
    this.idToken,
    this.refreshToken,
    required this.expiresIn,
  });

  DateTime get expiresAt =>
      DateTime.now().add(Duration(seconds: expiresIn <= 0 ? 3600 : expiresIn));

  factory LogtoTokenSet.fromJson(Map<String, dynamic> json) {
    final accessToken = json['access_token']?.toString() ?? '';
    if (accessToken.isEmpty) {
      throw const FormatException(
          'Logto token endpoint did not return access_token');
    }
    return LogtoTokenSet(
      accessToken: accessToken,
      idToken: json['id_token']?.toString(),
      refreshToken: json['refresh_token']?.toString(),
      expiresIn: (json['expires_in'] as num?)?.toInt() ?? 3600,
    );
  }
}
