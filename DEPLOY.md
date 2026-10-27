# 构建与发布

## 1. 本地构建

```bash
# Windows PowerShell
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 2. 单元测试

解析器与状态模型有 JVM 单测，fixtures 位于 `app/src/test/resources/fixtures`：

```bash
.\gradlew.bat testDebugUnitTest
```

## 3. Release 签名

本地签名配置放在 `key.properties`（已被 .gitignore 忽略），并在
`app/build.gradle.kts` 中读取。CI 通过仓库 secrets 还原同一套签名：

| Secret | 说明 |
| :--- | :--- |
| `ANDROID_KEYSTORE_BASE64` | release.jks 的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | key alias，默认 `nbtca` |
| `ANDROID_KEY_PASSWORD` | key 密码 |

打 tag 发布（GitHub Actions 会构建签名 APK 并创建 Release）：

```bash
git tag v1.0.0
git push origin v1.0.0
```

## 4. 工作流

- `.github/workflows/ci.yml`：push/PR 时运行单元测试并构建 Debug APK。
- `.github/workflows/release.yml`：推送 `v*` tag 时构建签名 APK、创建 GitHub Release。

## 5. 旧 BFF 下线（一次性）

App 从 v1.1.0 起不再依赖自建 BFF；v1.0.0 及更早版本的资讯、工具与校历仍指向
`https://bff.nbtca.space`。确认旧版本不再需要后，按下面步骤下线：

```bash
# 1. 停止并删除容器（公网机）
docker stop nbtca-app-bff
docker rm nbtca-app-bff
docker image rm ghcr.io/nbtca/nbtca-app-bff:latest

# 2. 从 /opt/axonhub/Caddyfile 删除 bff.nbtca.space 这一段（不要动其他站点）
#    然后重启 Caddy
docker restart axonhub-caddy-1

# 3. Cloudflare 删除 bff 的 A 记录

# 4. 验证已下线
curl -sS https://bff.nbtca.space/health || echo "bff offline"
```

> 旧版本在 BFF 下线后仍可使用 Saturday 报修；资讯会走离线兜底，工具页会提示网络不可用。
> 如需平滑过渡，可在应用商店/公告中提示升级，再执行第 1 步。
