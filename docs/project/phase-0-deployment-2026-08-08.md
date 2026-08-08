# Phase 0 主干收敛与生产部署证据

> 状态：已部署，停在 Phase 1 之前
>
> 记录日期：2026-08-08
>
> 权威需求 SHA-256：`b113478d4ee3a21899f72f737c8a0a9fa69b2c5c9a249306b7e474e3205b96a0`
>
> 总控契约 SHA-256：`bd6229ebcff310e44d82c380b9931924cbbacab7288b7acfd0d64e83a2c9174a`

本文档记录用户在 Phase 0 原始交付门禁之后另行授权的四仓 `master`
收敛、生产数据库迁移和 NAS Docker Compose 部署。它不表示已经进入
Phase 1，也不替代真实浏览器 UI/UX 验收。

## 1. Git 主干收敛

- 四仓本地与远程均只保留 `master`，GitHub 默认分支均为 `master`。
- 删除其他分支前，所有本地、远程、未合并和未推送提交均已验证可从
  `master` 追溯。
- `client-ui` 与 `admin-server` 按授权使用 `ours` 历史合并：
  旧 `master` 提交仍可追溯，最终文件树采用整改分支。
- `admin-ui` 采用普通合并并保留旧 `master` 的 Docker workflow 修复；
  `client-server` 直接以整改产物收敛。
- 全程使用普通 push，未 force push、rebase、amend 或改写历史。

部署产物对应的四仓提交：

| 仓库 | `master` |
| --- | --- |
| `client-ui` | `09aeb722c49815ccf7b7ff60a730ee05d7d60826` |
| `client-server` | `19953a571369` |
| `admin-ui` | `ab8a81018e1e92101eab152c7c3f2391774fd117` |
| `admin-server` | 本证据提交的父提交 `e7a08b38208c` |

部署前另行发现并交付两个小型 UI 构建兼容提交，以及一个公共注册死入口修复：

- Next.js 16.3 在当前 NAS/Node 24 环境下通过 TypeScript CLI 读取
  `--showConfig` 时丢失 stdout；按 Next 随包文档改用 TypeScript
  JavaScript API。
- Turbopack 的 PostCSS worker 在受控 NAS 执行环境内绑定临时端口被拒绝；
  生产构建固定为 Next 官方支持的 Webpack 路径。
- 客户端不再展示或调用已由后端封堵的公共注册；`/register` 静态页面只包含
  到 `/login` 的 Next redirect，旧注册表单和
  `/api/auth/register` 前端调用均已移除。

## 2. 构建与不可变发布产物

- `client-server`：`mvn -B -o clean verify`，15/15 测试通过。
- `admin-server`：`mvn -B -o clean verify`，125/125 测试通过。
- `client-ui`：`npm run build`，TypeScript 通过，生成 14 个路由。
- `admin-ui`：`npm run build`，TypeScript 通过，生成 13 个路由。

不可变 release：

`deploy/releases/20260808-phase0-09aeb72-19953a5-ab8a810-e7a08b3/`

- 共 4424 个文件，约 190 MB。
- 两个 JAR 与两个 standalone `server.js` 的 `SHA256SUMS` 校验通过。
- release 内未发现 `.env`、PEM 或私钥文件。
- `deploy/current` 原子指向上述 release。
- release manifest SHA-256：
  `de7f16c27dab4e3f5e2943ca3ba8fd13d6b74932ab812816a88d66ac7804f292`。

## 3. Compose 与 NAS 项目注册

- Compose project 固定为 `film-forest`，working directory 为
  `/volume2/Project/film-forest/deploy`。
- `docker compose ls -a` 显示 `film-forest running(4)`。
- 四个容器均带有 `com.docker.compose.project=film-forest` 标签：
  `film-forest-client-ui`、`film-forest-client-server`、
  `film-forest-admin-ui`、`film-forest-admin-server`。
- 验收时四容器均为 `running`、`RestartCount=0`。
- Compose SHA-256：
  `df4f3175888b8fcab7a8ce660d9a7cf15e71669a53980c09c076bf0914aad23b`。

运行镜像固定到已由 Docker 官方 manifest 验证的 arm64 digest：

- Temurin 17.0.19 JRE Jammy：
  `sha256:a9a83259bb576657930d10b003c251f17d9e42d33e0024e718aefe8228b984d6`。
- Node 24.18.0 Alpine 3.23：
  `sha256:e8470651b67dd2af4e982ea4c8b4166b521d55f3951cc1ef1dcb1671befe7dd3`。

最初配置的 Temurin Alpine 3.23 完整标签只发布 amd64，首次拉取按预期失败；
部署未使用该不兼容镜像。确认官方 manifest 后改为精确 arm64 Jammy digest。

## 4. 生产数据库迁移

迁移前一致性备份：

`deploy/backups/20260808-phase0-435bddc-19953a5-67dc723-e7a08b3/film_forest-predeploy.sql`

- 89,176,989 字节，18 个 `CREATE TABLE`。
- SHA-256：
  `5712c61ddbf106ac6120e751f91518cea0b1c937670649e2e6e311bd3806cbd6`。
- 备份目录为 `0700`，dump 和两份受保护环境快照均为 `0600`。

首次启动时 Flyway 13 在连接初始化阶段需要读取
`performance_schema.user_variables_by_thread`，专用账号的最小权限阻止了该读取；
此时数据库仍为 18 表、0 history、0 安全字段。停止重试容器并确认无迁移后，
临时授予该单表 `SELECT`，完成迁移，再将运行时 Flyway 关闭、重建管理端并撤销
临时授权。最终 `film_forest_app` 只保留 `film_forest.*` 的既定业务权限。

最终数据库事实：

- Flyway history：V1 `BASELINE` 成功，V2 `SQL` 成功。
- 用户表新增 3 个安全字段与 3 个 CHECK。
- 2 个用户保持不丢失，其中 1 个 `ADMIN`、1 个 BCrypt、1 个遗留 SHA-256。
- 运行时 `FILM_FOREST_FLYWAY_ENABLED=false`。

## 5. 运行验收

内部地址：

- `GET http://127.0.0.1:8080/health` → 200，服务状态 `ok`。
- `GET http://127.0.0.1:8080/api/movies?page=1&size=1` → 200。
- `GET http://127.0.0.1:3000/api/movies?page=1&size=1` → 200，代理链通过。
- 管理 API 直连 8081 与通过 3001 代理均返回 401。
- `GET /api/auth/register` 返回 401，公共注册不可用。
- 客户端首页、客户端登录页、管理端登录页均返回 200 并包含中文文本。
- `/register` 不包含旧注册表单，包含到 `/login` 的 307 redirect 指令。

CORS：

- 客户端 LAN Origin `http://192.168.5.110:3000` 精确放行。
- 管理端 Tailscale Origin `http://100.66.66.66:3001` 精确放行。
- 未授权 Origin `https://example.invalid` 返回 403，且无
  `Access-Control-Allow-Origin`。

LAN 与 Tailscale：

- `192.168.5.110` 的 3000、3001、8080 均返回 200，8081 管理 API 返回 401。
- `100.66.66.66` 的同组端点返回相同结果。
- 当前四容器日志扫描未发现 `ERROR`、`FATAL` 或
  `Application run failed`。

## 6. 回滚与剩余门禁

已保留：

- 迁移前数据库 dump 及校验和；
- 修改前 Compose 与 `.env`；
- 实际部署 Compose 与受保护运行时 `.env` 快照；
- 不可变 release 及其 `SHA256SUMS`；
- 四仓完整 Git 历史。

本次未执行数据库回滚、删除备份、删除 release、删除镜像或 Compose
`down`。生产回滚如需恢复迁移前 schema，必须另获明确授权并在停止四个目标服务后
使用保留 dump；不得影响共享 MySQL 的 `interview_master`。

当前 NAS 没有本地浏览器/Computer Use 环境。真实浏览器截图、交互、响应式布局和
视觉质量仍必须在 TX5Pro 的独立 `film-forest-browser-audit` 工程验收。由于
Phase 0 包含权限、认证、公共写入、注册入口、配置和数据库语义变化，不满足
“无功能或样式变化则自动进入下一阶段”的条件；Phase 1 未创建、未开始。
