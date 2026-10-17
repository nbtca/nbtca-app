import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'app/theme/app_theme.dart';
import 'core/auth/auth_service.dart';
import 'core/config/app_settings.dart';
import 'features/navigation/main_navigation_page.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppSettings.instance.initialize();
  await AuthService().initialize();
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);
  runApp(const NBTCAApp());
}

class NBTCAApp extends StatefulWidget {
  const NBTCAApp({super.key});

  @override
  State<NBTCAApp> createState() => _NBTCAAppState();
}

class _NBTCAAppState extends State<NBTCAApp> {
  ThemeMode _themeMode = AppSettings.instance.themeMode;

  void _toggleTheme() {
    setState(() {
      _themeMode =
          _themeMode == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark;
    });
    unawaited(AppSettings.instance.setThemeMode(_themeMode));
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '拔电关机',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: _themeMode,
      locale: const Locale('zh', 'CN'),
      supportedLocales: const [
        Locale('zh', 'CN'),
        Locale('en', 'US'),
      ],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      home: MainNavigationPage(
        onToggleTheme: _toggleTheme,
        currentThemeMode: _themeMode,
      ),
    );
  }
}
