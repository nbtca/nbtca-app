# NBTCA Android App (Flutter 客户端)

浙大宁波理工学院 计算机协会（NBTCA）官方移动端应用——“拔电关机”。

## 当前能力

- **拔电关机 (Newsroom)**：从 Go BFF 获取 nbtca.space RSS 聚合的真实文章，支持动态分类、封面、Markdown 正文与原文跳转；BFF 不可用时显示明确的离线缓存提示。
- **电脑义诊 (Saturday)**：使用 Logto access token 直连 `https://api.nbtca.space/v2`。普通用户走 `/client/events`，技术员走 `/member/events`，管理员使用公开队列 + 管理接口；支持预约、查看完整联系方式、修改/取消预约、接单、提交维修报告、审核结单与退回。
- **极客工具**：接入 `status.nbtca.space/api/status` 的真实服务状态分组、`ical.nbtca.space/events.ics` 的协会活动日历，以及协会文档入口。
- **个人中心**：Logto 统一身份登录、深浅主题持久化、BFF 地址持久化、社团开源入口。

## 登录与安全

- 使用 Logto **Authorization Code + PKCE**，redirect URI 为 `https://nbtca.space/callback`，App 在 WebView 中拦截回调后本地换取 token，不读取网页 localStorage。
- Access/Refresh Token 存入 Android Keystore（`flutter_secure_storage`），支持过期自动刷新。
- 正式包不再默认以管理员身份启动；开发角色切换仅在 Debug 构建和登录页调试区可见。
- Release 构建默认禁止明文 HTTP；本地 BFF 调试仅 Debug 构建允许 `http://10.0.2.2:8080`。

## 在 Android Studio 中运行

1. 打开本目录 `frontend`。
2. 确认已安装 Flutter / Dart 插件。
3. 启动模拟器或连接真机。
4. 运行 `main.dart` 配置（或 `flutter run`）。

## 网络配置

- Android 模拟器默认 BFF：`http://10.0.2.2:8080/api/v1`。
- 真机：在 `个人中心 -> 后端服务器配置` 填入电脑局域网地址；地址会持久保存。
- 报修 API 固定直连 `https://api.nbtca.space/v2`，不受 BFF 地址影响。

## 验证命令

```bash
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

## 相关工程

- [nbtca.space](https://nbtca.space) / `nbtca/Home`：官网、RSS、Web 报修流程
- [Saturday](https://github.com/nbtca/Saturday)：报修 API
- [Sunday](https://github.com/nbtca/Sunday)：维修管理台
- `nbtca/calendar`、`nbtca/heartbeat`
