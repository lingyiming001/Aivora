# 本机运行与测试

当前使用本机已有镜像运行基础设施，Java 21 和 Vite 在主机运行。端口避开本机其他容器：PostgreSQL 15432、Redis 16379、MinIO 19000/19001、后端 8080、前端 5173。

1. `cp deploy/.env.example .env`，设置随机的数据库密码、JWT 密钥和供应商加密密钥；模拟支付需要 `SPRING_PROFILES_ACTIVE=dev`。不要提交 `.env`。
2. `docker compose -p aivora -f deploy/compose.yml --env-file .env up -d --no-build --pull never`。默认只启动已有镜像的 PostgreSQL、Redis 和 MinIO。
3. 使用 Java 21 运行 `mvn -f aivora/pom.xml -DskipTests package`，再用 `.env` 中的变量启动 `java -jar aivora/aivora-admin/target/aivora-admin-0.1.0.jar`。首次启动由 Flyway 自动迁移。
4. 在 `aivora-frontend` 执行 `npm ci` 和 `npm run dev -- --host 127.0.0.1 --port 5173`。打开 http://127.0.0.1:5173/ 。

本机容器状态：`docker compose -p aivora -f deploy/compose.yml --env-file .env ps`。后端健康检查：http://127.0.0.1:8080/actuator/health 。停止容器：`docker compose -p aivora -f deploy/compose.yml --env-file .env down`。演示账号凭据仅保存在本机被忽略的 `.local-test-credentials`，不放进文档或仓库。

`container-app` profile 仍可用于容器化构建前后端；默认开发路径不触发远程拉取。

## 后端本地重启（2026-10-14）
打包后运行 `python3 scripts/run_local_backend.py`。脚本读取本地.env、使用本机15432/16379映射，复制jar到/tmp不可变运行副本后启动；不直接覆盖正在使用的jar。演示模型及供应商已停用，聊天使用后台已配置的真实模型。
