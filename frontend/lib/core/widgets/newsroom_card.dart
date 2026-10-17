import 'package:flutter/material.dart';
import '../../app/theme/app_colors.dart';
import 'network_or_data_image.dart';
import '../../features/newsroom/models/news_article.dart';

class NewsroomCard extends StatelessWidget {
  final NewsArticle article;
  final bool isLargeFeatured;
  final VoidCallback onTap;

  const NewsroomCard({
    super.key,
    required this.article,
    this.isLargeFeatured = false,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: isDark ? AppColors.surfaceDark : AppColors.surfaceLight,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(isDark ? 0.4 : 0.05),
              blurRadius: 16,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AspectRatio(
              aspectRatio: isLargeFeatured ? (16 / 10) : (16 / 9),
              child: article.coverUrl.isNotEmpty
                  ? NetworkOrDataImage(
                      url: article.coverUrl,
                      fit: BoxFit.cover,
                      placeholder: Container(
                        color: isDark
                            ? AppColors.surfaceDarkElevated
                            : const Color(0xFFE5E5EA),
                        child: const Center(
                            child: CircularProgressIndicator.adaptive()),
                      ),
                      errorWidget: Container(
                        color: isDark
                            ? AppColors.surfaceDarkElevated
                            : const Color(0xFFE5E5EA),
                        child: const Icon(Icons.image_not_supported_outlined,
                            size: 36, color: Colors.grey),
                      ),
                    )
                  : Container(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          colors: isDark
                              ? const [Color(0xFF0F2027), Color(0xFF2C5364)]
                              : const [Color(0xFFE8F1FA), Color(0xFFBFD9F2)],
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                        ),
                      ),
                      child: Stack(
                        children: [
                          Positioned(
                            right: -12,
                            bottom: -12,
                            child: Icon(
                              Icons.newspaper_rounded,
                              size: 120,
                              color: AppColors.primary
                                  .withValues(alpha: isDark ? 0.18 : 0.12),
                            ),
                          ),
                          Padding(
                            padding: const EdgeInsets.all(18),
                            child: Align(
                              alignment: Alignment.bottomLeft,
                              child: Text(
                                article.category.isEmpty
                                    ? 'NBTCA'
                                    : article.category,
                                style: TextStyle(
                                  fontSize: isLargeFeatured ? 22 : 18,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: 1.2,
                                  color: isDark
                                      ? AppColors.textPrimaryDark
                                      : const Color(0xFF0B3A66),
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(
                        article.category.toUpperCase(),
                        style: const TextStyle(
                          color: AppColors.primary,
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.8,
                        ),
                      ),
                      const Spacer(),
                      Row(
                        children: [
                          Icon(Icons.access_time_rounded,
                              size: 13,
                              color: isDark
                                  ? AppColors.textSecondaryDark
                                  : AppColors.textSecondaryLight),
                          const SizedBox(width: 4),
                          Text(
                            article.published,
                            style: TextStyle(
                              color: isDark
                                  ? AppColors.textSecondaryDark
                                  : AppColors.textSecondaryLight,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    article.title,
                    style: TextStyle(
                      color: isDark
                          ? AppColors.textPrimaryDark
                          : AppColors.textPrimaryLight,
                      fontSize: isLargeFeatured ? 20 : 17,
                      fontWeight: FontWeight.bold,
                      height: 1.3,
                      letterSpacing: -0.3,
                    ),
                  ),
                  if (article.summary.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text(
                      article.summary,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: isDark
                            ? AppColors.textSecondaryDark
                            : AppColors.textSecondaryLight,
                        fontSize: 14,
                        height: 1.4,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
