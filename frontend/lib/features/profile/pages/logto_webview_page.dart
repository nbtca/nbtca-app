import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../../app/theme/app_colors.dart';
import '../../../core/auth/auth_service.dart';
import '../../../core/auth/logto_auth.dart';

/// 使用 Logto Authorization Code + PKCE 的原生登录页。
///
/// 不再读取 repair.nbtca.space 的 localStorage，也不依赖 Web 端 app id 猜测。
class LogtoWebViewPage extends StatefulWidget {
  final VoidCallback? onLoginSuccess;

  const LogtoWebViewPage({super.key, this.onLoginSuccess});

  @override
  State<LogtoWebViewPage> createState() => _LogtoWebViewPageState();
}

class _LogtoWebViewPageState extends State<LogtoWebViewPage> {
  late final WebViewController _controller;
  late String _codeVerifier;
  late String _state;

  bool _isLoading = true;
  bool _isCompleting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(Colors.white)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: (_) {
            if (mounted) setState(() => _isLoading = true);
          },
          onPageFinished: (_) {
            if (mounted) setState(() => _isLoading = false);
          },
          onWebResourceError: (error) {
            if (error.isForMainFrame ?? true) {
              if (mounted) {
                setState(() {
                  _isLoading = false;
                  _error = '登录页面加载失败：${error.description}';
                });
              }
            }
          },
          onNavigationRequest: (request) {
            final uri = Uri.tryParse(request.url);
            if (uri != null && LogtoAuth.isRedirect(uri)) {
              _handleRedirect(uri);
              return NavigationDecision.prevent;
            }
            return NavigationDecision.navigate;
          },
        ),
      );
    _startAuth();
  }

  void _startAuth() {
    _codeVerifier = LogtoAuth.generateCodeVerifier();
    _state = LogtoAuth.generateState();
    final authorizeUrl = LogtoAuth.buildAuthorizeUrl(
      codeChallenge: LogtoAuth.codeChallenge(_codeVerifier),
      state: _state,
    );
    _controller.loadRequest(authorizeUrl);
    if (mounted) {
      setState(() {
        _error = null;
        _isCompleting = false;
        _isLoading = true;
      });
    }
  }

  Future<void> _handleRedirect(Uri uri) async {
    if (_isCompleting) return;
    setState(() {
      _isCompleting = true;
      _isLoading = true;
      _error = null;
    });

    final error = uri.queryParameters['error'];
    if (error != null) {
      _fail(uri.queryParameters['error_description'] ?? '登录被取消或失败');
      return;
    }
    if (uri.queryParameters['state'] != _state) {
      _fail('登录回调校验失败，请重新登录');
      return;
    }
    final code = uri.queryParameters['code'];
    if (code == null || code.isEmpty) {
      _fail('登录回调缺少授权码');
      return;
    }

    try {
      final tokens = await LogtoAuth.exchangeCode(
        code: code,
        codeVerifier: _codeVerifier,
      );
      await AuthService().loginWithLogtoTokens(tokens);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('登录成功，已同步 NBTCA 身份'),
          backgroundColor: AppColors.statusCompleted,
        ),
      );
      widget.onLoginSuccess?.call();
      Navigator.pop(context, true);
    } on DioException catch (e) {
      if (e.response?.statusCode == 400) {
        _fail('登录会话已过期，请点击“重新登录”');
      } else {
        final detail = e.response?.statusCode?.toString() ?? e.type.toString();
        _fail('登录失败：$detail');
      }
    } catch (e) {
      _fail(e is AuthException ? e.message : '登录失败，请检查网络后重试');
    }
  }

  void _fail(String message) {
    if (!mounted) return;
    setState(() {
      _isCompleting = false;
      _isLoading = false;
      _error = message;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'NBTCA 统一身份登录',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold),
        ),
        leading: IconButton(
          icon: const Icon(Icons.close_rounded),
          onPressed: () => Navigator.pop(context),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            tooltip: '重新加载登录页',
            onPressed: _isCompleting ? null : _startAuth,
          ),
        ],
        bottom: _isLoading
            ? const PreferredSize(
                preferredSize: Size.fromHeight(2),
                child: LinearProgressIndicator(
                  minHeight: 2,
                  color: AppColors.primary,
                ),
              )
            : null,
      ),
      body: Stack(
        children: [
          WebViewWidget(controller: _controller),
          if (_isCompleting)
            Container(
              color: Colors.black.withValues(alpha: 0.35),
              alignment: Alignment.center,
              child: const Card(
                child: Padding(
                  padding: EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CircularProgressIndicator.adaptive(),
                      SizedBox(height: 14),
                      Text('正在验证登录并同步身份…'),
                    ],
                  ),
                ),
              ),
            ),
          if (_error != null)
            Container(
              color: Theme.of(context).scaffoldBackgroundColor,
              alignment: Alignment.center,
              padding: const EdgeInsets.all(28),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.error_outline_rounded,
                      size: 48, color: AppColors.statusAlert),
                  const SizedBox(height: 12),
                  Text(
                    _error!,
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 15, height: 1.5),
                  ),
                  const SizedBox(height: 20),
                  FilledButton.icon(
                    onPressed: _startAuth,
                    icon: const Icon(Icons.refresh_rounded),
                    label: const Text('重新登录'),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
