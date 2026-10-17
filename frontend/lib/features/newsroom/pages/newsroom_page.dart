import 'package:flutter/material.dart';

import '../../../app/theme/app_colors.dart';
import '../../../core/network/api_client.dart';
import '../../../core/widgets/newsroom_card.dart';
import '../models/news_article.dart';
import 'article_detail_page.dart';

class NewsroomPage extends StatefulWidget {
  const NewsroomPage({super.key});

  @override
  State<NewsroomPage> createState() => _NewsroomPageState();
}

class _NewsroomPageState extends State<NewsroomPage> {
  final ApiClient _apiClient = ApiClient();
  NewsFeedData? _feedData;
  bool _isLoading = true;
  String _selectedCategory = '全部';
  List<String> _categories = const ['全部'];

  @override
  void initState() {
    super.initState();
    _loadFeed();
  }

  Future<void> _loadFeed() async {
    setState(() => _isLoading = true);
    final data = await _apiClient.getNewsFeed();
    if (!mounted) return;
    final categories = <String>[
      '全部',
      ...data.categories.where((c) => c.isNotEmpty),
    ];
    // 兼容后端没返回 categories 的情况，从文章本身补齐。
    for (final article in [...data.featured, ...data.recent]) {
      if (article.category.isNotEmpty &&
          !categories.contains(article.category)) {
        categories.add(article.category);
      }
    }
    setState(() {
      _feedData = data;
      _categories = categories;
      if (!_categories.contains(_selectedCategory)) {
        _selectedCategory = '全部';
      }
      _isLoading = false;
    });
  }

  List<NewsArticle> _filterArticles(List<NewsArticle> list) {
    if (_selectedCategory == '全部') return list;
    return list
        .where((a) =>
            a.category == _selectedCategory ||
            a.tags.contains(_selectedCategory))
        .toList();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final feed = _feedData;
    final featuredList =
        feed != null ? _filterArticles(feed.featured) : <NewsArticle>[];
    final recentList =
        feed != null ? _filterArticles(feed.recent) : <NewsArticle>[];
    final isEmpty = featuredList.isEmpty && recentList.isEmpty;

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('拔电关机',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 22)),
            Text(
              'To be at the intersection of technology and liberal arts.',
              style: TextStyle(
                fontSize: 11,
                fontStyle: FontStyle.italic,
                color: isDark
                    ? AppColors.textSecondaryDark
                    : AppColors.textSecondaryLight,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: _loadFeed,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator.adaptive())
          : RefreshIndicator(
              onRefresh: _loadFeed,
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: [
                  if (feed?.isOffline == true) _buildOfflineBanner(isDark),
                  Padding(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 16, vertical: 10),
                    child: SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: Row(
                        children: _categories.map((cat) {
                          final isSelected = cat == _selectedCategory;
                          return Padding(
                            padding: const EdgeInsets.only(right: 8),
                            child: FilterChip(
                              label: Text(cat),
                              selected: isSelected,
                              onSelected: (_) {
                                setState(() => _selectedCategory = cat);
                              },
                              showCheckmark: false,
                              backgroundColor:
                                  isDark ? AppColors.surfaceDark : Colors.white,
                              selectedColor: AppColors.primary,
                              labelStyle: TextStyle(
                                color: isSelected
                                    ? Colors.white
                                    : (isDark
                                        ? AppColors.textPrimaryDark
                                        : AppColors.textPrimaryLight),
                                fontWeight: isSelected
                                    ? FontWeight.w600
                                    : FontWeight.normal,
                              ),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(20),
                              ),
                              side: BorderSide(
                                color: isSelected
                                    ? Colors.transparent
                                    : (isDark
                                        ? AppColors.dividerDark
                                        : AppColors.dividerLight),
                              ),
                            ),
                          );
                        }).toList(),
                      ),
                    ),
                  ),
                  if (isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(
                          vertical: 60, horizontal: 20),
                      child: Center(
                        child: Column(
                          children: [
                            Icon(Icons.article_outlined,
                                size: 56, color: Colors.grey[400]),
                            const SizedBox(height: 12),
                            Text(
                              '暂无「$_selectedCategory」分类相关文章',
                              style: TextStyle(
                                color: isDark
                                    ? AppColors.textSecondaryDark
                                    : AppColors.textSecondaryLight,
                                fontSize: 15,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  if (featuredList.isNotEmpty) ...[
                    _buildSectionLabel('精选焦点', isDark),
                    ...featuredList.map(
                      (article) => NewsroomCard(
                        article: article,
                        isLargeFeatured: true,
                        onTap: () => _openArticle(article),
                      ),
                    ),
                  ],
                  if (recentList.isNotEmpty) ...[
                    _buildSectionLabel('更多文章', isDark),
                    ...recentList.map(
                      (article) => NewsroomCard(
                        article: article,
                        isLargeFeatured: false,
                        onTap: () => _openArticle(article),
                      ),
                    ),
                  ],
                  const SizedBox(height: 32),
                ],
              ),
            ),
    );
  }

  Widget _buildSectionLabel(String text, bool isDark) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 4),
      child: Text(
        text,
        style: TextStyle(
          fontSize: 15,
          fontWeight: FontWeight.w700,
          color: isDark
              ? AppColors.textSecondaryDark
              : AppColors.textSecondaryLight,
          letterSpacing: 0.5,
        ),
      ),
    );
  }

  Widget _buildOfflineBanner(bool isDark) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.statusPending.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: AppColors.statusPending.withValues(alpha: 0.3),
        ),
      ),
      child: Row(
        children: [
          const Icon(Icons.cloud_off_rounded,
              size: 18, color: AppColors.statusPending),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              '当前显示离线缓存内容。启动 Go BFF 或检查网络后下拉刷新。',
              style: TextStyle(
                fontSize: 12,
                height: 1.4,
                color: isDark
                    ? AppColors.textSecondaryDark
                    : AppColors.textSecondaryLight,
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _openArticle(NewsArticle article) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ArticleDetailPage(article: article),
      ),
    );
  }
}
