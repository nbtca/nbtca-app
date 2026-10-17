# NBTCA App BFF & Backend Service

NBTCA 移动端应用的后端聚合服务（BFF）。所有对外数据均来自真实上游，本地仅做解析、缓存与协议适配，不再返回静态 Mock。

## 职责与上游

- **Newsroom**：读取 `SPACE_SITE_ENDPOINT/rss.xml`，抓取文章页并转为 Markdown（5 分钟缓存）。
- **Calendar**：读取 `CALENDAR_ENDPOINT/school.ics`，解析教学周、假期与考试安排（10 分钟缓存）。
- **Infrastructure**：转发 `status.nbtca.space/api/status`（30 秒缓存，失败回退上一次成功结果）。
- **AI 预检**：配置 Dify 时调用 `/workflows/run`（blocking），失败自动回退关键词规则。
- **Auth**：转发 Logto `/oidc/me`，不签发、不缓存 Token。
- **Saturday**：报修工单由客户端直连 `api.nbtca.space/v2`；BFF 仅提供 `/repair/status` 连通性检查与 `/repair/diagnose`。旧的 `/repair/tickets` 假路由已删除。

## 接口

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/health` | 健康检查 |
| GET | `/api/v1/newsroom/feed` | RSS 聚合，含 featured/recent/categories |
| GET | `/api/v1/newsroom/articles/{id}` | RSS 条目 + 文章页转 Markdown |
| GET | `/api/v1/tools/calendar` | 教学周与校历（school.ics） |
| GET | `/api/v1/tools/association-calendar` | 协会公共活动（events.ics） |
| GET | `/api/v1/tools/infrastructure` | 服务状态看板 |
| GET | `/api/v1/tools/version` | 客户端版本信息 |
| GET | `/api/v1/repair/status` | Saturday API 连通性 |
| POST | `/api/v1/repair/diagnose` | AI/规则排障预检 |
| GET | `/api/v1/auth/profile` | Logto userinfo 代理（需要 Bearer Token） |
| POST | `/api/v1/auth/token-exchange` | 固定 501，Token 由 Logto 管理 |

所有成功响应保持 `{code:0,message:"success",data:...}` 外层。

## 本地开发

```bash
# 启动服务 (默认端口 8080)
go run ./cmd/server

# 编译
go build -o nbtca-backend.exe ./cmd/server

# 测试与静态检查
go test ./...
go vet ./...
```

## 环境变量

| 变量名 | 默认值 | 作用 |
| :--- | :--- | :--- |
| `PORT` | `8080` | HTTP 监听端口 |
| `HTTP_TIMEOUT_SECONDS` | `10` | 上游请求超时，自动限制在 8-12 秒 |
| `CORS_ALLOWED_ORIGINS` | `*` | 逗号分隔的允许来源 |
| `SATURDAY_API_ENDPOINT` | `https://api.nbtca.space` | Saturday API 基地址（可带 `/v2`） |
| `SPACE_SITE_ENDPOINT` | `https://nbtca.space` | 官网/RSS 基地址 |
| `CALENDAR_ENDPOINT` | `https://ical.nbtca.space` | 校历 iCal 基地址 |
| `LOGTO_ENDPOINT` | `https://auth.app.nbtca.space` | Logto OIDC 地址 |
| `STATUS_API_ENDPOINT` | `https://status.nbtca.space/api/status` | 服务状态 JSON |
| `DIFY_API_ENDPOINT` | 空 | Dify API 基地址（如 `https://dify.example/v1`） |
| `DIFY_API_KEY` | 空 | Dify Workflow API Key |
| `APP_LATEST_VERSION` | `1.0.0` | 最新客户端版本 |
| `APP_VERSION_CODE` | `100` | 最新 versionCode |
| `APP_DOWNLOAD_URL` | 内置 APK 地址 | 下载地址 |
| `APP_CHANGELOG` | 内置文案 | 更新日志 |
| `APP_RELEASE_DATE` | `2026-10-16` | 发布日期 |
