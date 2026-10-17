import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import '../../../app/theme/app_colors.dart';
import '../../../core/auth/auth_service.dart';
import '../../../core/network/api_client.dart';
import '../../profile/pages/login_page.dart';

class CreateTicketPage extends StatefulWidget {
  const CreateTicketPage({super.key});

  @override
  State<CreateTicketPage> createState() => _CreateTicketPageState();
}

class _CreateTicketPageState extends State<CreateTicketPage> {
  final _formKey = GlobalKey<FormState>();

  // 1. 问题描述
  final _descController = TextEditingController();
  final _modelController = TextEditingController();

  // 2. 联系方式
  late final TextEditingController _emailController;
  final _phoneController = TextEditingController();
  final _qqController = TextEditingController();

  Timer? _debounceTimer;
  bool _isSubmitting = false;
  bool _isDiagnosing = false;
  Map<String, dynamic>? _diagnosisResult;

  final ApiClient _apiClient = ApiClient();

  @override
  void initState() {
    super.initState();
    final user = AuthService().currentUser;
    _emailController = TextEditingController(text: user?.email ?? "");
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    _descController.dispose();
    _modelController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _qqController.dispose();
    super.dispose();
  }

  void _triggerAIDiagnosis() async {
    final text = _descController.text.trim();
    if (text.length < 3) return;

    setState(() => _isDiagnosing = true);
    final modelName = _modelController.text.trim();
    final result = await _apiClient.diagnoseIssue(
      modelName.isNotEmpty ? modelName : "laptop",
      text,
    );
    if (mounted) {
      setState(() {
        _isDiagnosing = false;
        _diagnosisResult = result;
      });
    }
  }

  void _submitTicket() async {
    // 提交前校验 Token，若缺失则拦截并引导重新登录
    final token = AuthService().accessToken;
    if (token == null || token.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('登录凭证缺失，请重新登录'),
          backgroundColor: AppColors.statusPending,
          action: SnackBarAction(
            label: '去登录',
            textColor: Colors.white,
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const LoginPage()),
              );
            },
          ),
        ),
      );
      return;
    }

    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSubmitting = true);
    final desc = _descController.text.trim();
    final modelName = _modelController.text.trim();
    final phone = _phoneController.text.trim();
    final qq = _qqController.text.trim();
    final email = _emailController.text.trim();

    final data = {
      "problem": desc,
      "model": modelName.isNotEmpty ? modelName : "设备",
      "phone": phone,
      "qq": qq,
      "email": email,
      "description": desc,
      "device_type": modelName,
      "contact": phone,
      "student_id": email.contains('@') ? email.split('@').first : "29951517",
    };

    try {
      await _apiClient.createTicket(data);
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('工单提交成功！已实时录入 Saturday 维修系统'),
            backgroundColor: AppColors.statusCompleted,
          ),
        );
        Navigator.pop(context, true);
      }
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(e.message),
            backgroundColor: Colors.redAccent,
            action: e.statusCode == 401
                ? SnackBarAction(
                    label: '去登录',
                    textColor: Colors.white,
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const LoginPage()),
                      );
                    },
                  )
                : null,
          ),
        );
      }
    } on DioException catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        final statusCode = e.response?.statusCode;
        final errorMessage = statusCode == 401
            ? '登录凭证缺失或已失效，请重新登录'
            : '提交失败 (${statusCode ?? e.type})，请稍后再试';

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(errorMessage),
            backgroundColor: Colors.redAccent,
            action: statusCode == 401
                ? SnackBarAction(
                    label: '去登录',
                    textColor: Colors.white,
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const LoginPage()),
                      );
                    },
                  )
                : null,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('提交失败：$e'),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardBgColor =
        isDark ? const Color(0xFF1C1C1E) : const Color(0xFFF5F5F7);
    final subtextColor =
        isDark ? const Color(0xFF8E8E93) : const Color(0xFF6E6E73);

    return Scaffold(
      backgroundColor:
          isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF),
      appBar: AppBar(
        backgroundColor:
            isDark ? const Color(0xFF000000) : const Color(0xFFFFFFFF),
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new_rounded, size: 20),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
          children: [
            const Text(
              '预约维修',
              style: TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.w800,
                letterSpacing: -0.5,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              '在你填写表单之后，我们会尽快联系你，确认维修时间和地点。',
              style: TextStyle(
                fontSize: 14,
                color: subtextColor,
                height: 1.45,
              ),
            ),
            const SizedBox(height: 28),

            // ================= 模块一：问题描述 =================
            const Text(
              '问题描述',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 12),

            // 1. 问题描述* 输入框
            _buildCustomFormField(
              isDark: isDark,
              bgColor: cardBgColor,
              label: '问题描述',
              isRequired: true,
              hintText: '告诉我们你遇到的问题',
              controller: _descController,
              maxLines: 4,
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? '问题描述不能为空' : null,
              onChanged: (v) {
                if (v.length > 5) {
                  _debounceTimer?.cancel();
                  _debounceTimer = Timer(const Duration(milliseconds: 600), () {
                    if (mounted && !_isDiagnosing) {
                      _triggerAIDiagnosis();
                    }
                  });
                }
              },
            ),
            const SizedBox(height: 14),

            // 2. 型号 输入框
            _buildCustomFormField(
              isDark: isDark,
              bgColor: cardBgColor,
              label: '型号',
              isRequired: false,
              hintText: '你的设备型号',
              controller: _modelController,
            ),
            Padding(
              padding: const EdgeInsets.only(left: 4, top: 6),
              child: Text(
                '填写设备型号，帮助我们更快的定位问题',
                style: TextStyle(fontSize: 12, color: subtextColor),
              ),
            ),

            // AI 智能排障预检卡片
            if (_isDiagnosing)
              const Padding(
                padding: EdgeInsets.only(top: 14),
                child: Row(
                  children: [
                    SizedBox(
                        width: 14,
                        height: 14,
                        child:
                            CircularProgressIndicator.adaptive(strokeWidth: 2)),
                    SizedBox(width: 10),
                    Text(
                      'AI 正在分析排障建议...',
                      style: TextStyle(
                          color: AppColors.primary,
                          fontSize: 13,
                          fontWeight: FontWeight.w500),
                    ),
                  ],
                ),
              ),

            if (_diagnosisResult != null)
              Container(
                margin: const EdgeInsets.only(top: 14),
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: isDark
                      ? const Color(0xFF1E2633)
                      : const Color(0xFFF0F6FF),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: AppColors.primary.withOpacity(0.25),
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Row(
                      children: [
                        Icon(Icons.auto_awesome,
                            color: AppColors.primary, size: 18),
                        SizedBox(width: 8),
                        Text(
                          'AI 智能预检与自救建议',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 14,
                            color: AppColors.primary,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _diagnosisResult!["summary"]?.toString() ?? "",
                      style: const TextStyle(fontSize: 13, height: 1.45),
                    ),
                    if ((_diagnosisResult!["possible_causes"] as List?)
                            ?.isNotEmpty ==
                        true) ...[
                      const SizedBox(height: 10),
                      const Text(
                        '可能原因',
                        style: TextStyle(
                            fontSize: 12, fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 4),
                      ...(_diagnosisResult!["possible_causes"] as List)
                          .map((item) => Padding(
                                padding: const EdgeInsets.only(bottom: 3),
                                child: Text(
                                  '• $item',
                                  style: const TextStyle(
                                      fontSize: 12, height: 1.4),
                                ),
                              )),
                    ],
                    if ((_diagnosisResult!["self_help_steps"] as List?)
                            ?.isNotEmpty ==
                        true) ...[
                      const SizedBox(height: 8),
                      const Text(
                        '可先尝试',
                        style: TextStyle(
                            fontSize: 12, fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 4),
                      ...(_diagnosisResult!["self_help_steps"] as List)
                          .map((item) => Padding(
                                padding: const EdgeInsets.only(bottom: 3),
                                child: Text(
                                  '① $item',
                                  style: const TextStyle(
                                      fontSize: 12, height: 1.4),
                                ),
                              )),
                    ],
                  ],
                ),
              ),

            const SizedBox(height: 32),

            // ================= 模块二：联系方式 =================
            const Text(
              '联系方式',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 12),

            // 3. 邮箱* 输入框 (只读，绑定 Logto 账号)
            _buildCustomFormField(
              isDark: isDark,
              bgColor: cardBgColor,
              label: '邮箱',
              isRequired: true,
              hintText: 'example@nbtca.space',
              controller: _emailController,
              readOnly: true,
              keyboardType: TextInputType.emailAddress,
            ),
            Padding(
              padding: const EdgeInsets.only(left: 4, top: 6),
              child: Text(
                '我们会向此邮箱发送维修相关的通知',
                style: TextStyle(fontSize: 12, color: subtextColor),
              ),
            ),
            const SizedBox(height: 14),

            // 4. 电话号码* 输入框 (限制 11 位数字)
            _buildCustomFormField(
              isDark: isDark,
              bgColor: cardBgColor,
              label: '电话号码',
              isRequired: true,
              hintText: '必填',
              controller: _phoneController,
              keyboardType: TextInputType.phone,
              maxLength: 11,
              validator: (v) {
                if (v == null || v.trim().isEmpty) return '电话号码不能为空';
                if (v.trim().length < 11) return '请输入 11 位有效手机号码';
                return null;
              },
            ),
            const SizedBox(height: 14),

            // 5. QQ 输入框
            _buildCustomFormField(
              isDark: isDark,
              bgColor: cardBgColor,
              label: 'QQ',
              isRequired: false,
              hintText: '你的QQ号',
              controller: _qqController,
              keyboardType: TextInputType.number,
            ),

            const SizedBox(height: 36),

            // 提交按钮
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF0071E3), // 官方科技蓝
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                  elevation: 0,
                ),
                onPressed: _isSubmitting ? null : _submitTicket,
                child: _isSubmitting
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                            color: Colors.white, strokeWidth: 2),
                      )
                    : const Text(
                        '提交',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 0.5,
                        ),
                      ),
              ),
            ),

            const SizedBox(height: 40),
          ],
        ),
      ),
    );
  }

  // 1:1 还原网页端无边框扁平卡片风格输入组件
  Widget _buildCustomFormField({
    required bool isDark,
    required Color bgColor,
    required String label,
    required bool isRequired,
    required String hintText,
    required TextEditingController controller,
    int maxLines = 1,
    int? maxLength,
    bool readOnly = false,
    TextInputType keyboardType = TextInputType.text,
    String? Function(String?)? validator,
    void Function(String)? onChanged,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(12),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          RichText(
            text: TextSpan(
              text: label,
              style: TextStyle(
                fontSize: 12,
                color:
                    isDark ? const Color(0xFF8E8E93) : const Color(0xFF86868B),
                fontWeight: FontWeight.w500,
              ),
              children: [
                if (isRequired)
                  const TextSpan(
                    text: ' *',
                    style: TextStyle(
                        color: Colors.redAccent,
                        fontSize: 13,
                        fontWeight: FontWeight.bold),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 2),
          TextFormField(
            controller: controller,
            maxLines: maxLines,
            maxLength: maxLength,
            readOnly: readOnly,
            keyboardType: keyboardType,
            validator: validator,
            onChanged: onChanged,
            style: TextStyle(
              fontSize: 15,
              color: isDark ? Colors.white : const Color(0xFF1D1D1F),
              fontWeight: FontWeight.normal,
            ),
            decoration: InputDecoration(
              isDense: true,
              counterText: '',
              contentPadding: const EdgeInsets.symmetric(vertical: 4),
              hintText: hintText,
              hintStyle: TextStyle(
                fontSize: 15,
                color:
                    isDark ? const Color(0xFF636366) : const Color(0xFFA1A1A6),
              ),
              border: InputBorder.none,
              enabledBorder: InputBorder.none,
              focusedBorder: InputBorder.none,
              errorBorder: InputBorder.none,
              focusedErrorBorder: InputBorder.none,
            ),
          ),
        ],
      ),
    );
  }
}
