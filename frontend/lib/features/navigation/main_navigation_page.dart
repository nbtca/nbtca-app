import 'package:flutter/material.dart';
import '../newsroom/pages/newsroom_page.dart';
import '../repair/pages/repair_home_page.dart';
import '../tools/pages/tools_page.dart';
import '../profile/pages/profile_page.dart';

class MainNavigationPage extends StatefulWidget {
  final VoidCallback onToggleTheme;
  final ThemeMode currentThemeMode;

  const MainNavigationPage({
    super.key,
    required this.onToggleTheme,
    required this.currentThemeMode,
  });

  @override
  State<MainNavigationPage> createState() => _MainNavigationPageState();
}

class _MainNavigationPageState extends State<MainNavigationPage> {
  int _currentIndex = 0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: [
          const NewsroomPage(),
          const RepairHomePage(),
          const ToolsPage(),
          ProfilePage(
            onToggleTheme: widget.onToggleTheme,
            currentThemeMode: widget.currentThemeMode,
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (idx) => setState(() => _currentIndex = idx),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.newspaper_outlined),
            selectedIcon: Icon(Icons.newspaper_rounded),
            label: '拔电关机',
          ),
          NavigationDestination(
            icon: Icon(Icons.build_circle_outlined),
            selectedIcon: Icon(Icons.build_circle_rounded),
            label: '维修',
          ),
          NavigationDestination(
            icon: Icon(Icons.cloud_done_outlined),
            selectedIcon: Icon(Icons.cloud_done_rounded),
            label: '工具',
          ),
          NavigationDestination(
            icon: Icon(Icons.account_circle_outlined),
            selectedIcon: Icon(Icons.account_circle_rounded),
            label: '个人中心',
          ),
        ],
      ),
    );
  }
}
