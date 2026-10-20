# Aivora

```text
aivora/                 Maven 后端父项目
  aivora-common/        公共异常、错误处理、当前用户与权限上下文
  aivora-ai/            AI 网关、模型服务、密钥加密
  aivora-auth/          登录、认证、访客和短信验证
  aivora-user/          用户个人资料
  aivora-character/     角色卡、互动和 AI 创作
  aivora-chat/          聊天、路由、组合生成和多模态
  aivora-wallet/        钱包及消耗统计
  aivora-payment/       充值和支付
  aivora-message/       Token 预算及后台通知
  aivora-system/        系统管理、审核、权限及模型配置
  aivora-admin/         唯一启动模块、运行配置与数据库迁移
aivora-frontend/        React 前端
```

业务模块按 `controller / service / dto` 分层，已有领域接口和基础设施适配器保留 `domain / infrastructure`。仅创建有实际职责的目录，保留 JDBC 数据访问方式、请求校验和响应格式。数据库迁移集中在启动模块，原 SQL 内容不变。

依赖方向：业务模块依赖 common；chat 依赖 ai/wallet；character 依赖 chat/ai；payment 依赖 wallet；message 依赖 ai；system 依赖 chat/ai/wallet。admin 聚合业务模块并启动，业务模块不依赖 admin。组织、产品、独立文件/订单模块尚无独立业务，未创建空模块。

构建：`mvn -f aivora/pom.xml -pl aivora-admin -am verify`（Java 21）。
启动：`java -jar aivora/aivora-admin/target/aivora-admin-0.1.0.jar`。

本机启动步骤见 [LOCAL_RUN](docs/dev/LOCAL_RUN.md)。

## 代码格式化

格式化工具集中在 `.agents/format/`，在该目录安装并执行：

```sh
cd .agents/format
npm ci
npm run format:check
# 需要修改格式时运行 npm run format
```

命令自动切换到工作空间根目录，统一格式化前后端源码和部署配置。使用 Prettier 与 Java/XML 插件；数据库迁移 SQL、生成物、依赖目录和环境凭据不参与格式化。前端运行依赖仍在 `aivora-frontend` 中独立安装。

前端目录与职责说明见 [aivora-frontend/README.md](aivora-frontend/README.md)。

## 工作空间文件

- `deploy/`：Compose、Nginx、前后端 Dockerfile、环境变量示例及部署说明；Docker 构建上下文为工作空间根目录。
- `.dockerignore`：必须位于构建上下文根目录，用于排除依赖、生成物和本机凭据。
- `.agents/format/`：跨前后端格式化工具、依赖锁文件和配置；根 `.editorconfig` 保留供编辑器自动识别。
- `scripts/`：本机前后端启动工具；`docs/`：运行、功能及历史验收说明。
- `.agents/`：开发交接记录；历史测试截图放在 `.agents/test/legacy-screenshots/`。
- `.env`、`.local-test-credentials`：本机配置与凭据，保留且忽略提交。
- `.github/workflows/ci.yml`：前后端构建验证。

## 直播与开放能力

登录后进入 `/live`，配置直播间、消息插件与 OBS 字幕。外部系统通过 `/api/mcp` 或 REST 桥接弹幕/字幕；YouTube/Twitch 支持授权令牌连接；支持语音字幕和可选 OBS RTMP/HLS 视频链路，其他平台仍需授权适配器。接口、插件扩展及接入示例见 [直播平台说明](docs/live-platform.md)。
