class ApiEndpoints {
  // 默认连本机模拟器；可用 --dart-define=NBTCA_API_BASE_URL=... 指向已部署的 BFF。
  static const String defaultBaseUrl = String.fromEnvironment(
    'NBTCA_API_BASE_URL',
    defaultValue: "http://10.0.2.2:8080/api/v1",
  );

  // Newsroom
  static const String newsFeed = "/newsroom/feed";
  static String articleDetail(String id) =>
      "/newsroom/articles/${Uri.encodeComponent(id)}";

  // Repair
  static const String repairDiagnose = "/repair/diagnose";

  // Tools
  static const String associationCalendar = "/tools/association-calendar";
  static const String infrastructure = "/tools/infrastructure";
  static const String appVersion = "/tools/version";

  // Auth
  static const String userProfile = "/auth/profile";
}
