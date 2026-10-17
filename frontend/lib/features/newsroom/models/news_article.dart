class NewsArticle {
  final String id;
  final String title;
  final String category;
  final String published;
  final String author;
  final String summary;
  final String coverUrl;
  final String articleUrl;
  final String? contentMarkdown;
  final List<String> tags;

  NewsArticle({
    required this.id,
    required this.title,
    required this.category,
    required this.published,
    required this.author,
    required this.summary,
    required this.coverUrl,
    required this.articleUrl,
    this.contentMarkdown,
    this.tags = const [],
  });

  factory NewsArticle.fromJson(Map<String, dynamic> json) {
    return NewsArticle(
      id: json['id']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      category: json['category']?.toString() ?? '资讯',
      published: json['published']?.toString() ?? '',
      author: json['author']?.toString() ?? 'NBTCA',
      summary: json['summary']?.toString() ?? '',
      coverUrl: json['cover_url']?.toString() ?? '',
      articleUrl: json['article_url']?.toString() ?? '',
      contentMarkdown: json['content_markdown']?.toString(),
      tags: (json['tags'] as List?)?.map((e) => e.toString()).toList() ??
          const [],
    );
  }

  NewsArticle copyWith({
    String? id,
    String? title,
    String? category,
    String? published,
    String? author,
    String? summary,
    String? coverUrl,
    String? articleUrl,
    String? contentMarkdown,
    List<String>? tags,
  }) {
    return NewsArticle(
      id: id ?? this.id,
      title: title ?? this.title,
      category: category ?? this.category,
      published: published ?? this.published,
      author: author ?? this.author,
      summary: summary ?? this.summary,
      coverUrl: coverUrl ?? this.coverUrl,
      articleUrl: articleUrl ?? this.articleUrl,
      contentMarkdown: contentMarkdown ?? this.contentMarkdown,
      tags: tags ?? this.tags,
    );
  }
}

class NewsFeedData {
  final List<NewsArticle> featured;
  final List<NewsArticle> recent;
  final List<String> categories;
  final bool isOffline;

  NewsFeedData({
    required this.featured,
    required this.recent,
    this.categories = const [],
    this.isOffline = false,
  });

  List<NewsArticle> get all => [...featured, ...recent];

  factory NewsFeedData.fromJson(Map<String, dynamic> json) {
    final featured = (json['featured'] as List?)
            ?.whereType<Map>()
            .map((e) => NewsArticle.fromJson(Map<String, dynamic>.from(e)))
            .toList() ??
        <NewsArticle>[];
    final recent = (json['recent'] as List?)
            ?.whereType<Map>()
            .map((e) => NewsArticle.fromJson(Map<String, dynamic>.from(e)))
            .toList() ??
        <NewsArticle>[];
    var categories = (json['categories'] as List?)
            ?.map((e) => e.toString())
            .where((e) => e.isNotEmpty)
            .toList() ??
        <String>[];
    if (categories.isEmpty) {
      categories = _deriveCategories([...featured, ...recent]);
    }
    return NewsFeedData(
      featured: featured,
      recent: recent,
      categories: categories,
    );
  }

  static List<String> _deriveCategories(List<NewsArticle> articles) {
    final seen = <String>[];
    for (final a in articles) {
      if (a.category.isNotEmpty && !seen.contains(a.category)) {
        seen.add(a.category);
      }
    }
    return seen;
  }
}
