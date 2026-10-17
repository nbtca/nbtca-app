class ServiceStatus {
  final String state;
  final int updatedEpoch;
  final List<ServiceGroup> groups;
  final List<ServiceIncident> incidents;
  final bool isOffline;
  final String? message;

  ServiceStatus({
    required this.state,
    this.updatedEpoch = 0,
    this.groups = const [],
    this.incidents = const [],
    this.isOffline = false,
    this.message,
  });

  bool get isOperational => state == 'operational';
  int get totalComponents =>
      groups.fold(0, (sum, group) => sum + group.components.length);
  int get healthyComponents => groups.fold(
        0,
        (sum, group) =>
            sum +
            group.components.where((c) => c.state == 'operational').length,
      );

  factory ServiceStatus.fromJson(Map<String, dynamic> json) {
    final groups = (json['groups'] as List?)
            ?.whereType<Map>()
            .map((e) => ServiceGroup.fromJson(Map<String, dynamic>.from(e)))
            .toList() ??
        <ServiceGroup>[];
    final incidents = (json['incidents'] as List?)
            ?.whereType<Map>()
            .map((e) => ServiceIncident.fromJson(Map<String, dynamic>.from(e)))
            .toList() ??
        <ServiceIncident>[];
    return ServiceStatus(
      state: json['state']?.toString() ?? 'unknown',
      updatedEpoch: (json['updated'] as num?)?.toInt() ?? 0,
      groups: groups,
      incidents: incidents,
      message: json['message']?.toString(),
    );
  }

  factory ServiceStatus.offline(String message) =>
      ServiceStatus(state: 'unknown', isOffline: true, message: message);
}

class ServiceGroup {
  final String name;
  final bool infra;
  final List<ServiceComponent> components;

  ServiceGroup({
    required this.name,
    required this.infra,
    required this.components,
  });

  factory ServiceGroup.fromJson(Map<String, dynamic> json) {
    return ServiceGroup(
      name: json['name']?.toString() ?? '',
      infra: json['infra'] == true,
      components: (json['components'] as List?)
              ?.whereType<Map>()
              .map((e) =>
                  ServiceComponent.fromJson(Map<String, dynamic>.from(e)))
              .toList() ??
          const [],
    );
  }
}

class ServiceComponent {
  final String id;
  final String name;
  final String state;
  final int sinceEpoch;
  final List<String> failed;
  final String? error;

  ServiceComponent({
    required this.id,
    required this.name,
    required this.state,
    this.sinceEpoch = 0,
    this.failed = const [],
    this.error,
  });

  bool get isOperational => state == 'operational';
  bool get isDegraded => state == 'degraded' || state == 'partial';
  bool get isDown => state == 'major' || state == 'down' || state == 'offline';

  factory ServiceComponent.fromJson(Map<String, dynamic> json) {
    return ServiceComponent(
      id: json['id']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
      state: json['state']?.toString() ?? 'unknown',
      sinceEpoch: (json['since'] as num?)?.toInt() ?? 0,
      failed: (json['failed'] as List?)?.map((e) => e.toString()).toList() ??
          const [],
      error: json['error']?.toString(),
    );
  }
}

class ServiceIncident {
  final String title;
  final String impact;
  final String? updatedAt;

  ServiceIncident({
    required this.title,
    required this.impact,
    this.updatedAt,
  });

  factory ServiceIncident.fromJson(Map<String, dynamic> json) {
    return ServiceIncident(
      title: json['title']?.toString() ?? '',
      impact: json['impact']?.toString() ?? '',
      updatedAt: (json['updatedAt'] ?? json['updated_at'])?.toString(),
    );
  }
}
