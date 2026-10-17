import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../network/api_endpoints.dart';

/// 轻量、同步可读的应用设置；初始化在 main() 中完成。
class AppSettings {
  AppSettings._();
  static final AppSettings instance = AppSettings._();

  static const _themeKey = 'settings.theme_mode';
  static const _baseUrlKey = 'settings.api_base_url';

  SharedPreferences? _prefs;
  ThemeMode _themeMode = ThemeMode.system;
  String _apiBaseUrl = ApiEndpoints.defaultBaseUrl;

  ThemeMode get themeMode => _themeMode;
  String get apiBaseUrl => _apiBaseUrl;

  Future<void> initialize() async {
    _prefs = await SharedPreferences.getInstance();
    final storedTheme = _prefs?.getString(_themeKey);
    _themeMode = switch (storedTheme) {
      'light' => ThemeMode.light,
      'dark' => ThemeMode.dark,
      _ => ThemeMode.system,
    };
    final storedUrl = _prefs?.getString(_baseUrlKey);
    if (storedUrl != null && storedUrl.trim().isNotEmpty) {
      _apiBaseUrl = storedUrl.trim();
    }
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    _themeMode = mode;
    await _prefs?.setString(
        _themeKey,
        switch (mode) {
          ThemeMode.light => 'light',
          ThemeMode.dark => 'dark',
          ThemeMode.system => 'system',
        });
  }

  Future<void> setApiBaseUrl(String url) async {
    final normalized = url.trim();
    if (normalized.isEmpty) return;
    _apiBaseUrl = normalized;
    await _prefs?.setString(_baseUrlKey, normalized);
  }
}
