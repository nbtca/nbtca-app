import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../app/theme/app_colors.dart';
import '../../../core/network/api_client.dart';
import '../../../core/widgets/network_or_data_image.dart';
import '../models/news_article.dart';

class ArticleDetailPage extends StatefulWidget {
  final NewsArticle article;

  const ArticleDetailPage({super.key, required this.article});

  @override
  State<ArticleDetailPage> createState() => _ArticleDetailPageState();
}

class _ArticleDetailPageState extends State<ArticleDetailPage> {
  final ApiClient _apiClient = ApiClient();
  late NewsArticle _article;
  bool _isLoading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _article = widget.article;
    _loadArticle();
  }

  Future<void> _loadArticle() async {
    if (_article.id.isEmpty) return;
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final article = await _apiClient.getArticle(_article.id);
      if (!mounted) return;
      setState(() {
        _article = article;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _error = e.toString();
      });
    }
  }

  Future<void> _openBrowser() async {
    final uri = Uri.tryParse(_article.articleUrl);
    if (uri != null && await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } else if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('无法打开该链接，请稍后再试')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: Text(_article.category),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            tooltip: '刷新正文',
            onPressed: _isLoading ? null : _loadArticle,
          ),
          IconButton(
            icon: const Icon(Icons.open_in_browser_rounded),
            tooltip: '在浏览器打开 nbtca.space 原文',
            onPressed: _openBrowser,
          ),
        ],
        bottom: _isLoading
            ? const PreferredSize(
                preferredSize: Size.fromHeight(2),
                child: LinearProgressIndicator(minHeight: 2),
              )
            : null,
      ),
      body: ListView(
        children: [
          if (_article.coverUrl.isNotEmpty)
            AspectRatio(
              aspectRatio: 16 / 9,
              child: NetworkOrDataImage(
                url: _article.coverUrl,
                fit: BoxFit.cover,
                placeholder: Container(
                  color: isDark
                      ? AppColors.surfaceDarkElevated
                      : const Color(0xFFE5E5EA),
                  child: const Center(
                    child: CircularProgressIndicator.adaptive(),
                  ),
                ),
                errorWidget: Container(
                  color: isDark
                      ? AppColors.surfaceDarkElevated
                      : const Color(0xFFE5E5EA),
                  child:
                      const Icon(Icons.image_not_supported_outlined, size: 40),
                ),
              ),
            ),
          Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: AppColors.primary.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        _article.category,
                        style: const TextStyle(
                          color: AppColors.primary,
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Text(
                      _article.published,
                      style: TextStyle(
                        fontSize: 13,
                        color: isDark
                            ? AppColors.textSecondaryDark
                            : AppColors.textSecondaryLight,
                      ),
                    ),
                    const Spacer(),
                    Flexible(
                      child: Text(
                        '作者: ${_article.author}',
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                          color: isDark
                              ? AppColors.textSecondaryDark
                              : AppColors.textSecondaryLight,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Text(
                  _article.title,
                  style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                    height: 1.3,
                    color: isDark
                        ? AppColors.textPrimaryDark
                        : AppColors.textPrimaryLight,
                  ),
                ),
                if (_article.tags.isNotEmpty) ...[
                  const SizedBox(height: 14),
                  Wrap(
                    spacing: 6,
                    runSpacing: 6,
                    children: _article.tags
                        .map(
                          (tag) => Chip(
                            label: Text('#$tag',
                                style: const TextStyle(fontSize: 11)),
                            padding: EdgeInsets.zero,
                            visualDensity: VisualDensity.compact,
                            backgroundColor: isDark
                                ? AppColors.surfaceDark
                                : Colors.grey[200],
                            side: BorderSide.none,
                          ),
                        )
                        .toList(),
                  ),
                ],
                if (_error != null) ...[
                  const SizedBox(height: 14),
                  _buildErrorBanner(_error!),
                ],
                const Divider(height: 36),
                MarkdownBody(
                  data: _article.contentMarkdown ??
                      ('## 摘要内容\n\n${_article.summary}\n\n*正文暂时无法从 nbtca.space 获取，点击右上角按钮阅读完整图文。*'),
                  selectable: true,
                  styleSheet:
                      MarkdownStyleSheet.fromTheme(Theme.of(context)).copyWith(
                    p: TextStyle(
                      fontSize: 16,
                      height: 1.6,
                      color: isDark
                          ? AppColors.textPrimaryDark
                          : AppColors.textPrimaryLight,
                    ),
                    h1: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.bold,
                      color: isDark
                          ? AppColors.textPrimaryDark
                          : AppColors.textPrimaryLight,
                    ),
                    h2: TextStyle(
                      fontSize: 19,
                      fontWeight: FontWeight.bold,
                      color: isDark
                          ? AppColors.textPrimaryDark
                          : AppColors.textPrimaryLight,
                    ),
                    h3: TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.bold,
                      color: isDark
                          ? AppColors.textPrimaryDark
                          : AppColors.textPrimaryLight,
                    ),
                    code: TextStyle(
                      backgroundColor: isDark
                          ? const Color(0xFF2C2C2E)
                          : const Color(0xFFF0F0F2),
                      color: AppColors.primary,
                      fontSize: 14,
                      fontFamily: 'monospace',
                    ),
                    codeblockDecoration: BoxDecoration(
                      color: isDark
                          ? const Color(0xFF1E1E20)
                          : const Color(0xFFF4F4F6),
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                  imageBuilder: (uri, title, alt) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(12),
                      child: NetworkOrDataImage(
                        url: uri.hasScheme
                            ? uri.toString()
                            : Uri.parse(_article.articleUrl)
                                .resolveUri(uri)
                                .toString(),
                        fit: BoxFit.contain,
                        placeholder: const Center(
                          child: Padding(
                            padding: EdgeInsets.all(20),
                            child: CircularProgressIndicator.adaptive(),
                          ),
                        ),
                        errorWidget: Text(
                          alt?.isNotEmpty == true ? alt! : '图片加载失败',
                          style: const TextStyle(fontSize: 12),
                        ),
                      ),
                    ),
                  ),
                  onTapLink: (text, href, title) async {
                    if (href == null) return;
                    final parsed = Uri.tryParse(href);
                    final uri = parsed == null
                        ? null
                        : parsed.hasScheme
                            ? parsed
                            : Uri.parse(_article.articleUrl).resolveUri(parsed);
                    if (uri != null && await canLaunchUrl(uri)) {
                      await launchUrl(uri,
                          mode: LaunchMode.externalApplication);
                    }
                  },
                ),
                const SizedBox(height: 48),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildErrorBanner(String message) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.statusPending.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: AppColors.statusPending.withValues(alpha: 0.3),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.cloud_off_rounded,
              size: 18, color: AppColors.statusPending),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              '正文加载失败，当前显示摘要：$message',
              style: const TextStyle(fontSize: 12, height: 1.4),
            ),
          ),
        ],
      ),
    );
  }
}
