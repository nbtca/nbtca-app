import 'dart:convert';
import 'dart:typed_data';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

/// 同时支持 http(s) 与 data:image/...;base64 的图片组件。
/// nbtca.space 的部分文章封面/正文图片是内联 data URI，不能交给
/// CachedNetworkImage 处理，否则会显示破图。
class NetworkOrDataImage extends StatelessWidget {
  final String url;
  final BoxFit fit;
  final double? width;
  final double? height;
  final Widget? placeholder;
  final Widget? errorWidget;

  const NetworkOrDataImage({
    super.key,
    required this.url,
    this.fit = BoxFit.cover,
    this.width,
    this.height,
    this.placeholder,
    this.errorWidget,
  });

  @override
  Widget build(BuildContext context) {
    if (url.startsWith('data:image/')) {
      final bytes = _decodeDataUri(url);
      if (bytes != null) {
        return Image.memory(
          bytes,
          fit: fit,
          width: width,
          height: height,
          gaplessPlayback: true,
          errorBuilder: (_, __, ___) =>
              errorWidget ?? const Icon(Icons.image_not_supported_outlined),
        );
      }
      return errorWidget ?? const Icon(Icons.image_not_supported_outlined);
    }

    return CachedNetworkImage(
      imageUrl: url,
      fit: fit,
      width: width,
      height: height,
      placeholder: placeholder == null ? null : (context, url) => placeholder!,
      errorWidget:
          errorWidget == null ? null : (context, url, error) => errorWidget!,
    );
  }

  Uint8List? _decodeDataUri(String value) {
    try {
      final comma = value.indexOf(',');
      if (comma < 0) return null;
      final meta = value.substring(0, comma);
      if (!meta.contains('base64')) return null;
      return base64Decode(value.substring(comma + 1));
    } catch (_) {
      return null;
    }
  }
}
