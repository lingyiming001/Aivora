# 直播工作台与开放接口

入口 `/live`。支持互动、YouTube/Twitch 授权令牌连接、浏览器实时语音字幕、模型录音识别和可选 OBS RTMP → HLS 视频链路。平台 OAuth 自动登录、WebRTC、自动 AI 回复及第三方插件上传执行仍未提供。

## 使用

1. 登录普通账号，打开「直播与开放能力」，选择平台并创建直播间。
2. 点击「开始互动」，发送弹幕或字幕。消息落库并经过当前房间启用的插件；结束互动后禁止写入且字幕链接不可读取。
3. 创建时生成字幕链接，也可重新生成（旧链接立即失效）。把链接添加到 OBS「浏览器」来源，建议 1920×1080、透明背景。只有 SUBTITLE 消息上屏，10 秒后清除；CHAT 留在控制台。
4. 链接片段携带独立随机读取令牌，服务端只保存 SHA-256；该令牌可读取此房间所有互动消息，不能写入或管理。链接不应公开分享；撤销请重新生成。

消息每批最多 100 条；控制台每 2 秒拉取、字幕每 1 秒拉取。当前不适合超大弹幕量；后续可在相同消息模型上增加 SSE/队列和保留期限。

## 平台预设与连接边界

| 平台 | 当前方式 | 仍需完成 |
| --- | --- | --- |
| 通用 | JSONL 桥接脚本 / REST / MCP | 连接自己的事件来源 |
| 哔哩哔哩、抖音 | 平台与房间 ID 配置，统一消息入口 | 合规授权的弹幕适配器，当前未自动连接 |
| Twitch | 官方 EventSub WebSocket 自动接收 | 在控制台配置应用/主播/授权用户 ID 和有效用户访问令牌 |
| YouTube | 官方 Live Chat API 轮询接收 | 在控制台配置 Live Chat ID 和有效 OAuth 访问令牌 |
| OBS | 浏览器来源字幕页面 | 在 OBS 添加该页面，不负责视频推流 |

Twitch 适配器应读取官方 channel.chat.message 事件的 message.text / chatter_user_name / message_id，转换为下方字段。YouTube 适配器应将 liveChatMessages 的 snippet.displayMessage / authorDetails.displayName / id 转换。内置连接器的访问令牌使用现有 SecretCipher 加密保存，GET 接口只返回 has_token，不回显令牌；外部适配器自行保管其凭据。不要填写平台登录密码。

## REST 与字幕输入

所有 `/api/live/**` 接口均使用现有账号的 `Authorization: Bearer <accessToken>`，游客不可使用；数据按账号隔离。访问令牌有有效期，外部客户端需使用既有登录/刷新接口维护令牌。

- `GET /api/live/catalog`：平台和消息插件。
- `GET /api/live/rooms`：自己的最近 100 个房间。
- `POST /api/live/rooms`：`{"name":"我的直播间","platform":"custom","externalRoom":"123"}`；仅本次返回 overlayToken。
- `PUT /api/live/rooms/{id}`：`{"status":"LIVE","plugins":["trim","hide-links"]}`，状态支持 READY/LIVE/ENDED。
- `POST /api/live/rooms/{id}/events`：下方消息体。
- `GET /api/live/rooms/{id}/events?after=0`：按递增 ID 增量读取；取最后一条 ID 作为下次游标。
- `POST /api/live/rooms/{id}/overlay-token`：轮换字幕只读令牌。
- `GET /api/live-overlay/{id}?token=...&after=0`：无需账号的受令牌保护输出。

```json
{"kind":"SUBTITLE","author":"主播","content":"欢迎来到直播间","externalId":"asr-session-1-segment-1"}
```

kind 为 CHAT 或 SUBTITLE。内容最多 2000 字符，作者最多 80 字符。externalId 可选、最长 150 字符，同房间去重，重复返回 duplicate=true。字幕可来自外部语音识别器，也可使用工作台的浏览器实时识别或模型录音识别。模型识别复用已有供应商调用、并发限制和用量记录，启用前由用户选择。

桥接脚本不依赖第三方 Python 库，从环境变量读取账号访问令牌，从 stdin 接收上述 JSON，每行一条：

```sh
export AIVORA_ACCESS_TOKEN='<当前账号访问令牌>'
python3 scripts/live_bridge.py --base-url http://127.0.0.1:8080 --room 1 < messages.jsonl
```

脚本失败即停止并返回 1，避免悄悄丢失消息；由调用方恢复失败行，externalId 保证重放去重。外部地址要求 HTTPS，拒绝跟随重定向。

## MCP

端点 `POST /api/mcp`，采用 MCP 2025-11-25 的无会话 JSON 响应兼容模式。支持 initialize、notifications/initialized、ping、tools/list、tools/call；GET/DELETE 返回 405。无服务端 SSE、资源、提示词或 OAuth 授权服务器；客户端需支持静态 Bearer 请求头。不是 2026-07-28 新协议实现。

请求头：`Content-Type: application/json`、`Accept: application/json, text/event-stream`、`Authorization: Bearer <accessToken>`；初始化后的请求带 `MCP-Protocol-Version: 2025-11-25`。

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"my-client","version":"1"}}}
```

```json
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"live_publish","arguments":{"roomId":1,"kind":"SUBTITLE","author":"主播","content":"由其他系统调用"}}}
```

工具：live_rooms（列房间）、live_catalog（平台和插件）、live_events（roomId/after）、live_publish（roomId/kind/author/content/externalId）。MCP 与 REST 共用数据、插件、所有权校验和去重。

## 插件与后续扩展

当前可逐房间启停内置文本插件 trim、hide-links，按保存顺序作用于 CHAT 和 SUBTITLE。插件不会请求外部网址或执行上传代码。新增可信服务端插件只需实现 LiveMessagePlugin 并注册 Spring Bean，启动时自动发现并检查 ID 重复，配置页自动显示。插件按房间设置顺序执行，输出仍限制 2000 字符；已配置但缺失的插件会阻止发送。第三方上传执行、权限清单和外部连接器审计仍待扩展。

后续：平台 OAuth 自动刷新、哔哩哔哩/抖音官方授权连接器、消息队列、可选角色回复与预算控制、音频播报、CDN/WebRTC。

参考：[MCP 2025-11-25 传输规范](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports)、[Twitch EventSub](https://dev.twitch.tv/docs/eventsub/handling-websocket-events)、[OBS 浏览器来源](https://obsproject.com/kb/browser-source)。

## 内置平台连接与语音接口

工作台新增「平台连接器」「语音字幕」「视频直播」。连接器保存配置不会自动连接；房间 LIVE 后点击连接才调用平台。连接状态为 CONNECTING/CONNECTED/RETRYING/ERROR/DISCONNECTED，结束互动或账号停用后停止采集。网络错误采用最多 60 秒退避；401/403 等授权或配置错误进入 ERROR，需更新凭据并重新连接。访问令牌过期不自动刷新，暂不保存平台 refresh token。YouTube 首次只读取平台返回的最近消息；断线期间消息完整性取决于平台游标及保留期，不能保证无限补齐。当前连接器按单节点运行，最多同时处理 64 个连接。

- `GET /api/live/rooms/{id}/connection`：不包含访问令牌的配置和状态。
- `PUT /api/live/rooms/{id}/connection`：`{"sourceId":"...","clientId":"","actorId":"","token":"..."}`。YouTube sourceId 为 Live Chat ID；Twitch sourceId 为主播用户 ID，clientId 为应用 ID，actorId 为令牌所属用户 ID。留空 token 保留旧令牌。保存会断开现有连接。
- `POST /api/live/rooms/{id}/connection/connect` 与 `/disconnect`：手动启停。Twitch 需要 `user:read:chat` 用户授权；YouTube 需要可读取目标 Live Chat 的 OAuth 授权。
- `GET /api/live/rooms/{id}/speech`：模型 ASR 可用性。
- `POST /api/live/rooms/{id}/speech`：`{"audio":"data:audio/webm;base64,..."}`，仅自有且 LIVE 的房间可调用，返回 text 草稿；不自动发布。音频上限 4 MB，前端录音上限 30 秒。浏览器实时识别支持临时结果预览、最终结果自动发布开关和手动编辑；切换房间/结束互动时关闭麦克风。
- `GET /api/live/rooms/{id}/broadcast`：媒体启用状态与已有随机观看 ID。
- `POST /api/live/rooms/{id}/broadcast`：轮换推流密钥和观看链接，密钥仅本次返回。
- `POST /api/live-media/publish`：SRS 发布鉴权回调，只校验 on_publish/live 和随机流名、发布令牌、房间/账号状态。
- `GET /api/live-view/{streamId}/index.m3u8` 与受限制的 TS 分片名：观看入口；拥有链接即可观看正在互动的直播。每次请求重新校验状态。

媒体服务部署与端口说明见 [deploy/README.md](../deploy/README.md)。HLS 存在缓冲延迟；已经建立的 OBS 发布连接需在 OBS 中停止，应用结束互动会立即阻止后续观看请求，播放器已经缓冲的片段可能继续播放数秒。推流配置轮换会改变观看地址。

连接器依据：[YouTube Live Chat](https://developers.google.com/youtube/v3/live/docs/liveChatMessages/list)、[Twitch 聊天 EventSub](https://dev.twitch.tv/docs/chat/send-receive-messages)、[浏览器语音识别](https://developer.mozilla.org/en-US/docs/Web/API/SpeechRecognition)。实际平台连接需要用户自己的有效授权，测试使用模拟平台响应，不代表已登录用户的平台账号。
