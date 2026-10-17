class AssociationCalendar {
  final String name;
  final String updated;
  final List<AssociationEvent> events;

  AssociationCalendar({
    required this.name,
    required this.updated,
    required this.events,
  });

  factory AssociationCalendar.fromJson(Map<String, dynamic> json) {
    return AssociationCalendar(
      name: json['name']?.toString() ?? 'NBTCA 计算机协会日历',
      updated: json['updated']?.toString() ?? '',
      events: (json['events'] as List?)
              ?.whereType<Map>()
              .map((e) =>
                  AssociationEvent.fromJson(Map<String, dynamic>.from(e)))
              .toList() ??
          const [],
    );
  }
}

class AssociationEvent {
  final String id;
  final String title;
  final String description;
  final String location;
  final String url;
  final DateTime? start;
  final DateTime? end;
  final bool allDay;

  AssociationEvent({
    required this.id,
    required this.title,
    required this.description,
    required this.location,
    required this.url,
    required this.start,
    required this.end,
    required this.allDay,
  });

  factory AssociationEvent.fromJson(Map<String, dynamic> json) {
    return AssociationEvent(
      id: json['id']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      description: json['description']?.toString() ?? '',
      location: json['location']?.toString() ?? '',
      url: json['url']?.toString() ?? '',
      start: DateTime.tryParse(json['start']?.toString() ?? ''),
      end: DateTime.tryParse(json['end']?.toString() ?? ''),
      allDay: json['all_day'] == true,
    );
  }
}
