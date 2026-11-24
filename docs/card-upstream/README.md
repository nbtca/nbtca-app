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

### 上游 7617672 的兼容性审阅（Card #49–#53）

| 审阅文件 | 对 Android 契约的影响 |
| --- | --- |
| `src/components/EventList.astro`、`src/pages/e/index.astro` | 活动新增往期分区、`已结束` 状态和同日日期/时间换行；原有标题、活动详情路径、地点与票面节点保留。客户端分组解析，往期列表不覆盖近期空态；缩略票面允许空持有人。 |
| `src/cards/pass.ts`、`src/cards/passes/default.astro`、`src/pages/e/[slug]/index.astro` | 主办券新增 `data-state="host"` 以及对应文案，不再显示领券表单。原参与者表单、票券/签到数据及路线保持。主办券没有入场二维码，客户端不把主办券当作已领的参与者券；仅在有效全尺寸参与者票券的开放签到节点读取入场码。新增主办人列表和活动管理链接不属于客户端编辑流程。 |
| `src/pages/me.astro`、`src/components/PassShelf.astro` | 已登录的访客可读取自己的票夹，社员原有 `.card.full`、资料表单及提交确认保留。客户端只在匹配票夹结构时接受访客页，待入场及票根分别展示，不向访客提供资料编辑。票架组件新增监控，因客户端读取其分区、缩略票面及详情链接。 |
| `src/middleware.ts`、`src/layouts/Base.astro` | 网页头像统一指向 `/me`；Access / Logto 与 `CF_Authorization` 仍适用。访客不能编辑成员资料或调用 `/api/me` 媒体上传。解析器接受访客 GET 响应不等于接受编辑保存响应；客户端仍拒绝登录页、拒绝页及未知结构。 |
| `src/cards/MemberCard.astro`、`src/pages/members/[id].astro` | 新增主办统计与票根来源，上游自行计算会员勋章等级，既有名字、部门、角色、图片与 `.card.full` 选择器不变。Android 继续读取现有会员字段，不把统计或主办数据当作编辑授权。 |

回归样例覆盖访客空/有票、社员和授权/结构失败、近期与往期（含只有往期及无活动）、同日日期/场地和详情链接、主办券与参与者入场码。未使用真实账号验证生产登录。
