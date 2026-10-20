# 部署入口

以下命令均在工作空间根目录执行。

```sh
cp deploy/.env.example .env
# 修改 .env 中的密码和密钥
docker compose -p aivora -f deploy/compose.yml --env-file .env up -d --no-build --pull never
```

上述命令仅启动本机基础设施。构建并启动前后端容器：

```sh
docker compose -p aivora -f deploy/compose.yml --env-file .env --profile container-app up -d --build
```

显式使用 `-p aivora` 保持迁移前的项目和数据卷名称，避免目录改名后创建新的空数据卷。若此前使用自定义项目名，请继续使用原名称。

Compose 构建上下文为 `..`，两个 Dockerfile 的 COPY 路径相对于工作空间根目录；根 `.dockerignore` 必须保留。实际 `.env` 保留在根目录供本地启动脚本使用，不提交。配置示例集中于本目录。

本地运行说明见 [LOCAL_RUN](../docs/dev/LOCAL_RUN.md)。生产部署前设置独立密码和密钥，并禁用 dev profile。

## 可选视频直播：OBS → SRS → 网页 HLS

本机后端模式使用 `deploy/compose.media.yml`，默认仅监听本机端口，不自动启动或拉取媒体镜像：

```sh
docker compose -p aivora-media -f deploy/compose.media.yml up -d
```

在 `deploy/.env` 中配置后重启后端：

```dotenv
AIVORA_MEDIA_ENABLED=true
AIVORA_RTMP_PUBLIC_URL=rtmp://localhost:1935/live
AIVORA_HLS_INTERNAL_URL=http://127.0.0.1:18085
```

打开 `/live`，选择房间，在「视频直播」生成推流配置；OBS 服务选「自定义」，填入服务器和完整推流密钥。先开始互动，再启动 OBS 推流。视频 H.264、音频 AAC、关键帧间隔 2 秒；观看入口 `/live/watch/{streamId}`。字幕需作为 OBS 浏览器来源添加到视频场景。

`deploy/srs.conf` 的发布回调默认指向 `host.docker.internal:8080`，适配本机运行的后端。若后端也运行在容器中，需把回调地址改为实际后端服务地址，把 `AIVORA_HLS_INTERNAL_URL` 改为媒体服务在其网络内的地址；不要把容器内的 127.0.0.1 当成其他服务。

媒体服务的 HTTP 8080 通过宿主机 18085 仅向本机开放，观看必须经 Aivora `/api/live-view/**` 转发；每个清单和分片请求均检查房间 LIVE、账号启用和随机观看链接。推流回调另外校验独立发布令牌，服务端只保存 SHA-256。结束互动或生成新推流配置后，旧观看链接不可读取；已经建立的 OBS 推流需要手动停止。

远程部署时，将 RTMP 端口限制到授权推流端并配置公开推流地址；观看走现有 HTTPS 站点。不要把原始 HLS 18085 或 SRS 管理端口直接公开，否则会绕过应用的观看检查。当前为单节点、HLS 转发方案，不包含 CDN、WebRTC 或视频转码。

停止本轮媒体服务：`docker compose -p aivora-media -f deploy/compose.media.yml down`。

实现依据：[SRS HTTP 回调](https://ossrs.net/lts/en-us/docs/v6/doc/http-callback)、[SRS HLS 配置](https://ossrs.net/lts/en-us/docs/v6/doc/hls)。媒体服务实际推流联调需 Docker 和可用数据库，本轮运行状态以开发交接记录为准。
