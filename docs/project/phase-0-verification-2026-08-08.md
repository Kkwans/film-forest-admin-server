# Phase 0 验证证据与交付门禁

> 状态：运行态门禁已完成，等待四仓推送核验
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
- 未写入、迁移或清理生产 `film_forest` 的业务表、业务数据或 Flyway history；仅按授权修改 MySQL 系统账号与授权表。
- 共享 MySQL 的端口、网络、容器和 `interview_master` 授权保持不变；按授权新增 `film_forest_app` 并轮换两个 root 账号，未重启容器。
- 未执行 Git 历史改写、强制推送、rebase、amend、hard reset 或非授权清理；仅精确销毁已验证的隔离演练容器、数据目录、敏感 dump 和凭据暂存目录。
- 未进入 Phase 1 的产品功能、爬虫稳定化或 UI 重构范围。

## 2. Git 基线与当前提交

四仓均从执行前 HEAD 创建同名整改分支，当前工作树均为 clean。

| 仓库 | 相对 `origin/main` 领先 | Phase 0 新提交 | 说明 |
| --- | ---: | ---: | --- |
| `client-ui` | 1 | 1 | 前端依赖安全基线 |
| `client-server` | 6 | 6 | 公共写接口、认证、配置、密码和依赖基线 |
| `admin-ui` | 2 | 2 | 前端依赖安全基线与 API 地址外置 |
| `admin-server` | 24 | 22 | 保留执行前已存在的 2 个本地提交，新增权限、导出、迁移、演练与文档提交 |

四仓推送已获授权，当前尚待执行并核验远端 OID；只允许推送同名整改分支，不允许强推或改写历史。

## 3. 后端验证

### client-server

- 执行 `mvn clean verify` 成功。
- 共执行 15 个测试，0 failure，0 error。
- 生成可构建 JAR。
- 回归覆盖客户端公共内容接口只读边界、认证配置、JWT 实时账号校验、登录限流和遗留密码渐进升级。

### admin-server

- 执行 `mvn clean verify` 成功。
- 共执行 125 个测试，0 failure，0 error，0 skipped。
- 生成可构建 JAR。
- 回归覆盖管理端公开注册封堵、`ADMIN` 实时授权、登录限流、遗留密码渐进升级、动态表名白名单、CSV 注入防护、数据库定位信息隐藏和爬虫既有行为。

两后端均已升级到 Spring Boot 3.5.16、MyBatis-Plus 3.5.17、Druid 1.2.28；管理端显式锁定 Flyway 13.0.0，相关安全依赖按各自实际调用范围升级。

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

生产 `film_forest` 业务 schema 在本阶段未发生写入。`admin-server` 是唯一迁移 owner，已经建立：

- V1：按真实 schema 固化 18 表 baseline，结构比对为 237/237 columns、89/89 indexes、2/2 foreign keys；
- V2：非破坏性新增 `role`、`password_algorithm`、`must_change_password`，识别现有 BCrypt，并仅为已确认管理员回填 `ADMIN`；
- `baselineOnMigrate` 使用 V1，`validate` 开启，`clean` 禁用；
- 应用配置和部署配置均保持首次迁移默认关闭，并有聚焦配置测试锁定显式 opt-in 语义；必须先通过隔离恢复演练再允许发布时启用。

### 备份恢复与 Flyway 隔离演练

- 使用单事务逻辑备份生成 89,176,766 字节 dump，SHA-256 为 `223077f888ba96321aa55ee73d1d8301e3d065b9c9f7ca840762f3ddedf0b952`；dump 包含 18 个 `CREATE TABLE`，不包含 `CREATE DATABASE` 或 `USE`。
- 隔离容器固定命名为 `film-forest-phase0-restore-20260808`，使用与生产相同的 MySQL 8 镜像 digest，数据目录位于权限受控的 Phase 0 缓存，端口仅绑定 `127.0.0.1:32768`。
- `RESTORED` 路径通过：18 张既有业务表由 Flyway 登记 V1 `BASELINE`，V2 以 `SQL` 成功执行；迁移后为 18 张业务表、3 个安全字段、3 个 CHECK，2 个用户保持为 1 BCrypt、1 legacy，并回填 1 个 `ADMIN`。
- `EMPTY` 路径通过：V1、V2 均以 `SQL` 从零执行；迁移后为 18 张业务表、3 个安全字段、3 个 CHECK，用户表为空。
- 空库执行 V1 时 MySQL 8.4 报告 7 组重复列索引的兼容性警告；只读对照确认这些冗余索引与生产现状一致，因此 Phase 0 保持 baseline 忠实，不擅自删除。后续如优化，必须通过独立、可回滚的版本化迁移处理。
- 两条路径均使用 Flyway 13.0.0，未再出现旧版本对 MySQL 8.4 的支持范围警告。
- 演练完成后，固定命名容器、临时 schema、数据目录、敏感 dump 和凭据文件均已精确删除并复核不存在。

### 共享 MySQL 凭据轮换

轮换前对 NAS 当前运行状态和可读配置范围完成只读检查：

- `mysql8` 同时承载 `film_forest` 与 `interview_master`，主机端口 3306 绑定在 IPv4/IPv6 全接口；本阶段未修改端口、网络或防火墙。
- `interview_master` 使用独立非 root 数据库授权；MySQL root 账号同时允许 `%` 与 `localhost` 来源。
- `performance_schema` 显示实例已连续运行约 7 天；期间 root 连接只有 `localhost` 来源，共 20 次，检查时没有外部 root 活动连接。
- 当前没有运行中的 film-forest 容器；`film-forest-client.service` 与 `film-forest-admin.service` 均未加载且处于 inactive/dead。
- `/volume2/DockerProject/film-forest/` 保留一份旧部署副本；其固定 root 口令已因本次轮换失效，旧产物不得直接重启，后续发布只能使用整改分支重新构建的产物和专用账号。
- 在 `/volume2/Project` 与 `/volume2/DockerProject` 的可读配置范围内，除 MySQL 自身配置、当前整改工作区和上述旧部署副本外，未发现其他 MySQL 连接配置；部分无读取权限的目录未被本次文件扫描覆盖。

已按授权完成：

- 创建 `film_forest_app@%`，认证插件为 `caching_sha2_password`；仅授予 `film_forest.*` 的 `SELECT`、`INSERT`、`UPDATE`、`DELETE`、`CREATE`、`ALTER`、`INDEX`、`REFERENCES`，读取目标库成功，访问 `interview_master` 被拒绝。
- 将 `/volume2/DockerProject/mysql/docker-compose.yml` 中的字面量 root 口令替换为强制 `.env` 引用；Compose 文件权限为 0644，新的 `.env` 权限为 0600，`docker compose config --quiet` 通过。
- 同时轮换 `root@%` 与 `root@localhost`；新口令分别通过 TCP 与 socket 登录验证，运行中容器保留的旧环境口令在两条路径均被拒绝。
- MySQL 容器未重启或重建，当前容器环境仍保存已经失效的旧值；未来重建会从受保护 `.env` 注入新值。该环境残留不影响认证，但不得通过 `docker inspect` 输出。
- `deploy/.env` 已以 0600 权限保存 `film_forest_app` 数据库凭据；JWT 与 CORS 仍为空、Flyway 仍为 `false`，因此该文件不构成部署授权，也尚不可用于启动服务。
- 轮换后使用专用账号复核：生产仍为 18 张基础表、0 个 Phase 0 安全字段、0 个 Flyway history 表、2 个用户。

轮换前没有外部 root 活动连接，但这不能证明休眠任务或外部主机从未保存旧口令；这类调用方现在会按预期认证失败。

### 备份恢复演练预检与护栏

- `/volume2` 可用空间约 7.2 TiB，足以容纳约 110 MiB 的逻辑备份和隔离 MySQL 数据目录。
- `/tmp` 已用满，仅剩约 23 MiB；演练不得把 dump 或临时数据库写入 `/tmp`，必须使用工作区内权限受控的 Phase 0 缓存目录。
- NAS 本地已有 `mysql:8` arm64 镜像，镜像 ID 与当前生产 `mysql8` 容器一致。
- 预定临时容器名 `film-forest-phase0-restore-20260808` 当前无冲突。
- `deploy/.env.example` 已默认使用专用账号名 `film_forest_app`、保留密码空值，并保持 Flyway 默认关闭。

`FlywayRestoreDrillIT` 已提供显式隔离护栏和两种演练模式：恢复旧 schema 后验证 V1 baseline + V2，以及空 schema 验证 V1 + V2。默认测试集合另有 6 个纯单元测试，证明正确隔离目标可通过，生产端口、非本机主机、schema/mode 错配和错误确认短语会被拒绝。IT 本身不进入默认测试集合，必须显式指定，并且编译成功不能替代实际隔离 MySQL 验证。

上述两个实际模式均已执行并通过；保护性默认测试与完整管理端回归也已通过。最终只读闭环确认生产 `film_forest` 仍为 18 张基础表、0 个 Phase 0 安全字段、0 个 Flyway history 表，且预定演练容器名与目录均不存在。

## 6. 部署配置静态验证

工作区根目录不是 Git 仓库。以下部署产物已修改但无法纳入四仓提交：

- `deploy/docker-compose.yml`
- `deploy/deploy.sh`
- `deploy/.env.example`
- `deploy/.gitignore`
- `deploy/.env`（权限 0600，受 `.gitignore` 排除，不包含在提交或报告中）

已完成：

- 数据库、JWT、CORS 和管理端内部 API 地址改为运行时注入；
- 删除部署脚本中的固定数据库密码和遗留 CLI 凭据；
- 固定 arm64 可用的 Node 与 Temurin runtime tags；
- `bash -n deploy/deploy.sh` 成功；
- 使用占位环境变量执行 `docker compose config --quiet` 成功；
- 精确扫描未发现新的固定敏感值。

既有 systemd unit 仍指向未经本阶段确认的旧路径，因此未擅自修改。

## 7. 剩余完成门禁

备份恢复演练、临时清理、专用账号创建和 MySQL root 轮换均已完成。剩余事项：

1. 完成四仓最终差异、测试与敏感信息扫描，将 `remediation/film-forest-20260808` 分支分别推送到已确认远端，并核验每个远端 OID 与本地 HEAD 一致。
2. GitHub 旧 token 由用户在提供方侧自行撤销；Codex 不持有也不输出该 token。该外部动作不扩展为 Codex 的撤销授权。

在以上门禁未完成前：

- Codex Goal Mode 保持 active；
- 不得将 Phase 0 标记为 complete；
- 不得启动 Phase 1；
- 不得部署或启用 Flyway；
- 不得删除未纳入本次精确清理范围的缓存、镜像或其他可回滚材料。
