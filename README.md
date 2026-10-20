# NBTCA Android App (拔电关机)

宁波工程学院计算机协会（NBTCA）官方 Android 客户端，采用 **Kotlin + Jetpack Compose** 现代原生架构开发。

本仓库专用于 **Android 端** 客户端的开发、维护与持续交付。

---

## 📱 核心功能特性

1. **资讯广场 (Newsroom)**
   - 协会官方新闻、大型活动发布与学术讲座通知
   - 分类筛选（志愿维修、学术讲座、系统公告等）
   - 精选置顶头条推荐与离线自适应支持

2. **电脑报修 (Repair)**
   - 宁波工程学院“周末电脑义诊”线上化全流程报修支持
   - 周六常规义诊时间、地点及值班状态实时监测
   - 工单全生命周期追踪（待接单、处理中、已结单、已驳回）
   - 支持报修同学、技术员及管理员多重身份与操作联动

3. **教务系统 (Academic)**
   - 网页登录一次后保存会话，课表 / 考试 / 成绩本地缓存
   - 课表按校历周次翻页（本周优先），支持任意学年学期查询
   - 数据全部存本机，离线可看；退出可清会话

4. **在线更新 (Update)**
   - 从 GitHub Releases 检查更新，支持「稳定版 / Beta」双通道
   - 发现新版本可直接跳转 APK 下载

5. **社团工具 (Tools)**
   - **社团日历**：直接同步与解析协会官方活动日程（`events.ics`）
   - **服务可用性监控**：实时探测 BFF 网关、Saturday 接口、Logto 认证中心等基础设施运行健康度
   - **快捷入口**：关联官方主站、开源镜像源及交流群组

4. **个人中心 (Profile)**
   - 统一身份认证：基于 **Logto OAuth2 + PKCE** 安全授权登录与 Token 自动续期
   - 角色与权限展现：关联用户协会职务、技术员认证与管理员权限
   - 调试模式：支持快速切换报修同学/技术员/管理员视角，便于本地功能测试与审核演示
   - 网络环境配置：自由切换本地调试模拟器（`10.0.2.2:8080`）或线上生产环境（`bff.nbtca.space`）

---

## 🛠️ 技术架构

- **语言**：Kotlin 2.1+
- **UI 框架**：Jetpack Compose (Material 3)
- **网络与序列化**：OkHttp 4.12 + Kotlinx.Serialization JSON
- **异步处理**：Kotlin Coroutines + Flow / StateFlow
- **图片加载**：Coil Compose
- **目标 SDK**：`minSdk: 26` / `targetSdk: 35` / `compileSdk: 35`
- **构建工具**：Gradle 8.10+ (KTS)

---

## 🚀 编译与调试

### 1. 环境准备
- **JDK**：OpenJDK 17 或 21（推荐使用 LibericaJDK 17 或 Android Studio 自带 JBR）
- **Android SDK**：API Level 35/36 Build-Tools

### 2. 编译调试 APK
```bash
# Windows PowerShell
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```
产物输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### 3. 安装至模拟器 / 真机
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n space.nbtca.app/.MainActivity
```
