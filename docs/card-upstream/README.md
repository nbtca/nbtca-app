# Card 上游接口适配

以 nbtca/card 的 main 为来源，不维护服务端分支，不依赖 /native。当前官方认证为 Cloudflare Access / Logto 网页会话，CookieManager 保存会话；它与 App OAuth Token 相互独立。

上游尚无完整社员卡 JSON API，CardRepository 在客户端适配现有网页/HTTP 入口并验证结构、表单和提交结果。维护者以后提供结构化接口时，在这一层替换，不向 Card 增加 Android 专用路由。

- GET /login?next=/e：官方授权。
- GET/POST /me：卡片及资料表单。
- GET /members、/members/{id}：列表和详情。
- GET /e、GET/POST /e/{slug}：活动和领券。
- GET /media/members/...：受保护图片。
- PUT /api/me/media?kind=background 或 portrait：multipart full、thumb。
- GET /api/pass?id=...：核销状态。
- POST /api/redeem：上游核销接口，App 尚无核销界面。

领券表单支持上游条件要求的 name、student_id、class_name、phone；未知字段或结构变更报兼容错误，服务端验证错误和未签发票券不能当作成功。

contract.json 固定已审阅的源码 commit/blob 哈希。Card upstream compatibility 工作流每天北京时间 09:00、手动触发和相关修改时检查差异。变更时 CI 失败并列出文件，不自动覆盖代码或部署服务端。

同步顺序：审阅上游差异 → 更新适配和固定样例测试 → 实机检查 → 更新基线。检查通过仅代表源码没有漂移，不等于所有业务已实测。不要只更新哈希消除失败。

维护者在 [nbtca-app#3](https://github.com/nbtca/nbtca-app/issues/3) 明确要求网页登录和 CF_Authorization Cookie；生产 main 不再提供 /native。此约定是当前适配依据。

上游 fe9e06b 的兼容性审阅：票券二维码的透明度和模糊样式限定在票根内，资料页配色按钮的渐变延伸到边框。两处均只修改 CSS，Android 使用的 HTML 结构、表单字段和 HTTP 接口保持兼容。
