import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class StoredSession {
  final String accessToken;
  final String? refreshToken;
  final String? idToken;
  final DateTime? expiresAt;
  final Map<String, dynamic>? profile;

  const StoredSession({
    required this.accessToken,
    this.refreshToken,
    this.idToken,
    this.expiresAt,
    this.profile,
  });
}

/// Token 使用 Android Keystore / 平台安全存储；用户资料缓存在同一安全容器。
class TokenStorage {
  TokenStorage({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(resetOnError: true),
            );

  static const _accessKey = 'auth.access_token';
  static const _refreshKey = 'auth.refresh_token';
  static const _idKey = 'auth.id_token';
  static const _expiryKey = 'auth.expires_at';
  static const _profileKey = 'auth.profile_json';

  final FlutterSecureStorage _storage;

  Future<void> save({
    required String accessToken,
    String? refreshToken,
    String? idToken,
    DateTime? expiresAt,
    Map<String, dynamic>? profile,
  }) async {
    await _storage.write(key: _accessKey, value: accessToken);
    await _writeOptional(_refreshKey, refreshToken);
    await _writeOptional(_idKey, idToken);
    await _writeOptional(_expiryKey, expiresAt?.toUtc().toIso8601String());
    await _writeOptional(
      _profileKey,
      profile == null ? null : jsonEncode(profile),
    );
  }

  Future<StoredSession?> read() async {
    final accessToken = await _storage.read(key: _accessKey);
    if (accessToken == null || accessToken.isEmpty) return null;
    final expiry = await _storage.read(key: _expiryKey);
    final profileJson = await _storage.read(key: _profileKey);
    Map<String, dynamic>? profile;
    if (profileJson != null && profileJson.isNotEmpty) {
      try {
        final decoded = json.decode(profileJson);
        if (decoded is Map) profile = Map<String, dynamic>.from(decoded);
      } catch (_) {}
    }
    return StoredSession(
      accessToken: accessToken,
      refreshToken: await _storage.read(key: _refreshKey),
      idToken: await _storage.read(key: _idKey),
      expiresAt: expiry == null ? null : DateTime.tryParse(expiry),
      profile: profile,
    );
  }

  Future<void> clear() async {
    await _storage.delete(key: _accessKey);
    await _storage.delete(key: _refreshKey);
    await _storage.delete(key: _idKey);
    await _storage.delete(key: _expiryKey);
    await _storage.delete(key: _profileKey);
  }

  Future<void> _writeOptional(String key, String? value) async {
    if (value == null || value.isEmpty) {
      await _storage.delete(key: key);
    } else {
      await _storage.write(key: key, value: value);
    }
  }
}
