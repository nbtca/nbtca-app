# Docker 部署

## 1. 构建 Linux 二进制

```bash
cd backend
GOOS=linux GOARCH=amd64 CGO_ENABLED=0 \
  go build -trimpath -ldflags="-s -w" \
  -o nbtca-backend-linux-amd64 ./cmd/server
```

## 2. 本机/Tailscale 部署

```bash
# 仅本机
docker compose up -d --build
# 或 Tailscale 地址
BFF_BIND=100.x.x.x docker compose up -d --build
docker compose ps
curl http://127.0.0.1:18080/health
```

## 3. 公网机 + Caddy 部署

公网机使用 `docker-compose.public.yml`，BFF 只监听
`127.0.0.1:18080`，由 Caddy 反代：

```bash
docker compose -f docker-compose.public.yml up -d --build
```

Caddy 站点配置见 [deploy/bff.nbtca.space.caddy](deploy/bff.nbtca.space.caddy)：

```caddyfile
bff.nbtca.space {
    tls {
        issuer acme
        issuer internal
    }
    encode zstd gzip
    reverse_proxy 127.0.0.1:18080
}
```

### Cloudflare DNS

`nbtca.space` 已托管在 Cloudflare。添加：

| 类型 | 名称 | 内容 | 代理状态 |
| :--- | :--- | :--- | :--- |
| A | `bff` | `<ORIGIN_IP>` | **DNS only（灰色云）** |

注意：`api.nbtca.space` 已用于现有服务，因此本 BFF 使用 `bff.nbtca.space`。

Caddy 会通过 HTTP-01 申请 Let's Encrypt 证书；DNS only 即可直接使用
HTTPS。`issuer internal` 仅作为申请失败时的兜底，不建议依赖。

验证：

```bash
curl https://bff.nbtca.space/health
```

> 部署时把 `<ORIGIN_IP>` 换成实际公网 IP；不要把服务器凭据写入仓库。

App 构建：

```bash
flutter run \
  --dart-define=NBTCA_API_BASE_URL=https://bff.nbtca.space/api/v1
```

Release 构建同理：

```bash
flutter build apk --release \
  --dart-define=NBTCA_API_BASE_URL=https://bff.nbtca.space/api/v1
```

## 4. 更新 Caddy 配置

公网机 Caddy 配置实际路径：

```text
/opt/axonhub/Caddyfile
```

修改后需要重启 Caddy 容器，确保只读 bind mount 指向新文件：

```bash
docker restart axonhub-caddy-1
```

## 5. CI/CD

工作流：

- `.github/workflows/ci.yml`：Go 测试/vet、Flutter analyze/test。
- `.github/workflows/release.yml`：推送 `v*` tag 时构建签名 APK、推送 BFF 镜像到 GHCR、创建 GitHub Release。
- `.github/workflows/deploy.yml`：手动触发，通过 SSH 在服务器拉取 GHCR 镜像并重启容器。

Release 签名 secrets：

| Secret | 说明 |
| :--- | :--- |
| `ANDROID_KEYSTORE_BASE64` | release.jks 的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | key alias，默认 `nbtca` |
| `ANDROID_KEY_PASSWORD` | key 密码 |

Deploy secrets：

| Secret | 说明 |
| :--- | :--- |
| `DEPLOY_HOST` | 公网服务器地址 |
| `DEPLOY_USER` | SSH 用户 |
| `DEPLOY_SSH_KEY` | SSH 私钥 |
| `GHCR_USER` | 拉取 GHCR 镜像的用户 |
| `GHCR_TOKEN` | 具有 `read:packages` 的 Token |

打 tag 发布：

```bash
git tag v1.0.0
git push origin v1.0.0
```

