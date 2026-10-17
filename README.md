# NBTCA Mobile Application (拔电关机 Android 客户端)

浙大宁波理工学院 计算机协会（NBTCA）官方移动端与聚合服务项目。

## 目录结构

```text
nbtca-app/
├── backend/                  # Go BFF 聚合层（RSS / 日历 / 服务状态 / AI 预检）
│   ├── cmd/server/main.go
│   ├── internal/config/      # 环境变量与端点配置
│   ├── internal/handler/     # HTTP 路由与处理器
│   ├── internal/service/     # RSS、ICS、status、Saturday 代理、诊断
│   └── go.mod
├── frontend/                 # Flutter Android 客户端
│   ├── lib/core/auth/        # Logto PKCE、Token 安全存储
│   ├── lib/core/network/     # BFF 与 Saturday API 客户端
│   ├── lib/features/         # Newsroom / Repair / Tools / Profile
│   └── pubspec.yaml
├── docker-compose.yml        # BFF 容器部署
├── DEPLOY.md                 # Docker 部署与 App 指向说明
└── README.md
```

## 架构与真实数据源

- **Newsroom**：BFF 从 `https://nbtca.space/rss.xml` 拉取文章列表，按需抓取文章页并转 Markdown，带内存缓存。
- **维修 (Saturday)**：客户端使用 Logto access token 直连 `https://api.nbtca.space/v2`，按角色使用 `/client/events`、`/member/events`、`/events`；BFF 不再保存假工单。
- **校历**：BFF 解析 `https://ical.nbtca.space/school.ics`，以“秋季/春季学期开始上课”计算教学周；2026-10-17 对应 2026-2027 秋季学期第 5 周。
- **协会活动日历**：BFF 单独解析 `https://ical.nbtca.space/events.ics`，展示近期协会活动、时间与描述；地点暂不展示。
- **服务状态**：BFF 转发 `https://status.nbtca.space/api/status`，App 展示真实分组、故障组件与事件。
- **AI 预检**：配置 Dify 时调用 workflow；未配置时使用本地关键词兜底，并标记来源。

## 登录

- Logto **Authorization Code + PKCE**，redirect URI 为 `https://nbtca.space/callback`。
- Token 存在 Android Keystore / 平台安全存储，支持 refresh；正式包不再默认管理员。
- 开发角色切换仅 Debug 构建可见。

## 快速开始

### 后端

```bash
cd backend
go run ./cmd/server
# 或
go build -o nbtca-backend.exe ./cmd/server
go test ./...
```

默认监听 `:8080`，健康检查 `GET /health`，API 前缀 `/api/v1`。
Docker 部署见 [DEPLOY.md](DEPLOY.md)，服务器映射端口为 `18080`。

### 客户端

```bash
cd frontend
flutter pub get
flutter analyze
flutter test
flutter run
```

- Android 模拟器 BFF：`http://10.0.2.2:8080/api/v1`。
- 真机在 `个人中心 -> 后端服务器配置` 填电脑局域网地址；会持久保存。
- Release 默认禁止明文 HTTP，请为 BFF 配置 HTTPS。

## 后端环境变量

| 变量 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `PORT` | `8080` | HTTP 监听端口 |
| `SATURDAY_API_ENDPOINT` | `https://api.nbtca.space` | Saturday API 基地址 |
| `SPACE_SITE_ENDPOINT` | `https://nbtca.space` | 官网 RSS/文章源 |
| `CALENDAR_ENDPOINT` | `https://ical.nbtca.space` | 校历 ICS 源 |
| `STATUS_API_ENDPOINT` | `https://status.nbtca.space/api/status` | 服务状态源 |
| `LOGTO_ENDPOINT` | `https://auth.app.nbtca.space` | Logto OIDC |
| `DIFY_API_ENDPOINT` / `DIFY_API_KEY` | 空 | 可选 Dify workflow |
| `CORS_ALLOWED_ORIGINS` | `*` | 逗号分隔的允许来源 |

## 相关开源工程

- [nbtca/Home](https://github.com/nbtca/Home) · [nbtca.space](https://nbtca.space)
- [nbtca/Saturday](https://github.com/nbtca/Saturday) · [nbtca/Sunday](https://github.com/nbtca/Sunday)
- `nbtca/calendar`、`nbtca/heartbeat`
