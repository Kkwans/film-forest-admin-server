# Phase 0 验证证据与交付门禁

> 状态：进行中，尚未满足 Phase 0 完成条件
>
> 记录日期：2026-08-08
>
> 执行分支：`remediation/film-forest-20260808`
>
> 权威需求 SHA-256：`95935335b7ccf4b6b1fc7e70350a4b7fba2a699396485cc616dec7353d7ef54c`
>
> 总控契约 SHA-256：`bd6229ebcff310e44d82c380b9931924cbbacab7288b7acfd0d64e83a2c9174a`

本文档只记录可复核的执行证据和剩余门禁，不表示已发布、已部署或已经进入 Phase 1。

## 1. 阶段边界

- 未启动或部署 film-forest 服务。
- 未写入、迁移或清理生产 `film_forest` 数据库。
- 未修改共享 MySQL 的账号、端口、网络或其他使用方。
- 未执行 Git 历史改写、强制推送、rebase、amend、hard reset 或 destructive cleanup。
- 未进入 Phase 1 的产品功能、爬虫稳定化或 UI 重构范围。

## 2. Git 基线与当前提交

四仓均从执行前 HEAD 创建同名整改分支，当前工作树均为 clean。

| 仓库 | 相对 `origin/main` 领先 | Phase 0 新提交 | 说明 |
| --- | ---: | ---: | --- |
| `client-ui` | 1 | 1 | 前端依赖安全基线 |
| `client-server` | 6 | 6 | 公共写接口、认证、配置、密码和依赖基线 |
| `admin-ui` | 2 | 2 | 前端依赖安全基线与 API 地址外置 |
| `admin-server` | 19 | 17 | 保留执行前已存在的 2 个本地提交，新增权限、导出、迁移与文档提交 |

当前提交尚未推送。精确远端与目标分支的外部写入授权仍是发布门禁。

## 3. 后端验证

### client-server

- 执行 `mvn clean verify` 成功。
- 共执行 15 个测试，0 failure，0 error。
- 生成可构建 JAR。
- 回归覆盖客户端公共内容接口只读边界、认证配置、JWT 实时账号校验、登录限流和遗留密码渐进升级。

### admin-server

- 执行 `mvn clean verify` 成功。
- 共执行 119 个测试，0 failure，0 error，0 skipped。
- 生成可构建 JAR。
- 回归覆盖管理端公开注册封堵、`ADMIN` 实时授权、登录限流、遗留密码渐进升级、动态表名白名单、CSV 注入防护、数据库定位信息隐藏和爬虫既有行为。

两后端均已升级到 Spring Boot 3.5.16、MyBatis-Plus 3.5.17、Druid 1.2.28；相关安全依赖按各自实际调用范围升级。

## 4. 前端验证

### client-ui

- Next.js 16.3.0、React 19.2.8、Axios 1.18.0。
- 默认 Turbopack production build 成功，TypeScript 校验通过，共生成 14 个静态页面/路由。
- `npm audit --json` 报告 0 vulnerability。
- `npm ls --depth=0` 依赖树有效。
- 既有 lint 债务仍为 30 errors / 39 warnings，本阶段未混入无关格式化或重构。

### admin-ui

- Next.js 16.3.0、React 19.2.8、Axios 1.18.0。
- NAS 无法访问 Google Fonts；使用本地响应替身后，Webpack production build 成功，TypeScript 校验通过，共生成 13 个静态页面。
- 默认 Turbopack build 因 Google Fonts 网络请求失败，替身模式会触发 Turbopack fetch panic；不得据此宣称默认构建链已通过。
- `npm audit --json` 报告 0 vulnerability。
- `npm ls --depth=0` 依赖树有效。
- 既有 lint 债务仍为 29 errors，分布在 10 个文件中。

当前 NAS 没有本地浏览器/Computer Use 环境，尚未执行真实浏览器截图与交互验收。

## 5. 数据库与 Flyway 证据

只读检查确认生产 `film_forest` 当前状态：

- 18 个 base table；
- 0 个 Phase 0 安全字段；
- 0 个 Flyway schema history 表；
- schema 总大小约 109.94 MiB；
- 2 个用户，其中 1 个 BCrypt、1 个遗留 SHA-256；
- 仅 1 个未删除管理员用户名基线候选。

生产数据库在本阶段未发生写入。`admin-server` 是唯一迁移 owner，已经建立：

- V1：按真实 schema 固化 18 表 baseline，结构比对为 237/237 columns、89/89 indexes、2/2 foreign keys；
- V2：非破坏性新增 `role`、`password_algorithm`、`must_change_password`，识别现有 BCrypt，并仅为已确认管理员回填 `ADMIN`；
- `baselineOnMigrate` 使用 V1，`validate` 开启，`clean` 禁用；
- 应用配置和部署配置均保持首次迁移默认关闭，并有聚焦配置测试锁定显式 opt-in 语义；必须先通过隔离恢复演练再允许发布时启用。

### 共享 MySQL 凭据影响审计

对 NAS 当前运行状态和可读配置范围完成只读检查：

- `mysql8` 同时承载 `film_forest` 与 `interview_master`，主机端口 3306 绑定在 IPv4/IPv6 全接口；本阶段未修改端口、网络或防火墙。
- `interview_master` 使用独立非 root 数据库授权；MySQL root 账号同时允许 `%` 与 `localhost` 来源。
- `performance_schema` 显示实例已连续运行约 7 天；期间 root 连接只有 `localhost` 来源，共 20 次，检查时没有外部 root 活动连接。
- 当前没有运行中的 film-forest 容器；`film-forest-client.service` 与 `film-forest-admin.service` 均未加载且处于 inactive/dead。
- `/volume2/DockerProject/film-forest/` 保留一份旧部署副本；其管理后端配置使用固定数据库 URL、固定 root 用户名和字面量密码，轮换或再次启用前必须同步替换。
- 在 `/volume2/Project` 与 `/volume2/DockerProject` 的可读配置范围内，除 MySQL 自身配置、当前整改工作区和上述旧部署副本外，未发现其他 MySQL 连接配置；部分无读取权限的目录未被本次文件扫描覆盖。

该快照说明当前轮换影响面较小，但不能证明休眠任务或外部主机永远不会使用 root。实际轮换前仍须再次检查活动连接，并优先为 `film_forest` 创建最小权限独立账号，而不是继续让应用使用共享 root。

### 备份恢复演练预检

- `/volume2` 可用空间约 7.2 TiB，足以容纳约 110 MiB 的逻辑备份和隔离 MySQL 数据目录。
- `/tmp` 已用满，仅剩约 23 MiB；演练不得把 dump 或临时数据库写入 `/tmp`，必须使用工作区内权限受控的 Phase 0 缓存目录。
- NAS 本地已有 `mysql:8` arm64 镜像，镜像 ID 与当前生产 `mysql8` 容器一致。
- 预定临时容器名 `film-forest-phase0-restore-20260808` 当前无冲突。
- `deploy/.env.example` 已默认使用专用账号名 `film_forest_app`、保留密码空值，并保持 Flyway 默认关闭。

以上只证明演练具备容量、镜像和命名前提；尚未创建 dump、容器、数据目录或测试账号。

## 6. 部署配置静态验证

工作区根目录不是 Git 仓库。以下部署产物已修改但无法纳入四仓提交：

- `deploy/docker-compose.yml`
- `deploy/deploy.sh`
- `deploy/.env.example`
- `deploy/.gitignore`

已完成：

- 数据库、JWT、CORS 和管理端内部 API 地址改为运行时注入；
- 删除部署脚本中的固定数据库密码和遗留 CLI 凭据；
- 固定 arm64 可用的 Node 与 Temurin runtime tags；
- `bash -n deploy/deploy.sh` 成功；
- 使用占位环境变量执行 `docker compose config --quiet` 成功；
- 精确扫描未发现新的固定敏感值。

既有 systemd unit 仍指向未经本阶段确认的旧路径，因此未擅自修改。

## 7. 尚未通过的完成门禁

以下事项缺少当前任务所需的精确外部状态变更授权，尚未执行：

1. 对约 110 MiB 的生产 schema 执行单事务逻辑备份，在临时隔离 MySQL 中完成恢复、旧 schema 校验、Flyway 升级与空库启动演练，并在验证后精确删除临时容器、数据和敏感 dump。
2. 按上方只读影响审计结果，为 `film_forest` 创建最小权限独立账号、更新实际部署密钥源和旧部署副本，再轮换已经暴露过的 MySQL root 凭据；GitHub 外部凭据同样需要在提供方侧撤销/轮换。上述数据库写入和外部凭据操作仍待精确授权。
3. 将四仓整改分支分别推送到已确认的四个远端仓库。

在以上门禁未完成前：

- Codex Goal Mode 保持 active；
- 不得将 Phase 0 标记为 complete；
- 不得启动 Phase 1；
- 不得部署或启用 Flyway；
- 不得删除本阶段缓存、备份、镜像或其他可回滚材料。
