# Phase 5 管理语义与每用户海报闭环验证

> 状态：代码、迁移、部署与运行验证通过；真实浏览器视觉/交互和真实用户 TMDB
> 凭据联调门禁待外部执行
>
> 记录日期：2026-08-09
>
> 部署代码：`client-ui@f57cc22`、`client-server@e06dc57`、
> `admin-ui@780618d`、`admin-server@62472bc`

## 1. 交付范围

- 管理端统一内容查询覆盖电影、剧集、综艺、动漫和短剧，使用服务端真实
  `total`、分页、类型、状态、关键词和排序；批量动作只作用于显式选择的 ID。
- 爬虫管理拆分为任务配置、运行 Job、执行日志和运行统计四个区域；配置返回最近
  Job 结果，Job 和日志使用服务端真分页，7/30 天统计来自 SQL 聚合。
- 动态区在活动 Job 时 4 秒轮询、无活动 Job 时停止高频轮询；页面隐藏时降为
  15 秒，不引入 WebSocket。
- 每用户海报设置只从认证上下文取得 `userId`；API 仅返回配置状态、掩码提示和
  最近验证结果，不返回明文凭据。
- TMDB 凭据使用 AES-256-GCM、随机 IV 和用户/密钥版本绑定 AAD 加密；运行环境
  使用独立生成的 32 字节密钥，旧会话 API key 未使用、未保存、未写入日志。
- 访问时按需补全和用户显式批量补全复用同一匹配器；批量 Job 每用户只允许一个
  活动实例，可查看进度、诊断和取消，不创建周期任务。
- 原图始终保留，用户选择 TMDB 时优先使用已接受结果，任一失败路径回退原图。
- About 页面提供 TMDB 精确免责声明和官方 approved logo。

## 2. 数据库备份、恢复演练与 V7

发布前一致性备份：

- 路径：
  `deploy/backups/20260809-phase5-62472bc-780618d-e06dc57-f57cc22/film_forest-predeploy.sql`
- 大小：102,115,989 bytes；
- SHA-256：`938f38b78cc70dafe7aa91186af4a4b0a83e3d440193a4afd45a57dc2ba4cc58`。
- 已部署 `.env` 和 Compose 另存为权限 0600 的运行恢复副本；其中 AES 密钥不输出、
  不入 Git，也不写入校验日志。

备份在独立 MySQL 8.4 容器和独立临时卷中完成全量恢复。首次 V7 演练发现
`poster_enrichment_job.user_id` 同时被存储生成列依赖时，MySQL 不允许该列使用
`ON DELETE CASCADE`。用户表采用逻辑删除，因此 Job 外键改为默认
`NO ACTION/RESTRICT`；重新从原始备份恢复后，25 张表、2 张新增表、9 项关键约束
全部成立，五类内容行数与生产逐类一致。临时容器、卷和临时 root 文件均已删除。

生产首次运行 Flyway 13 时，Druid 在最小权限账号无法读取
`performance_schema.user_variables_by_thread` 后禁用了连接。未扩大
`film_forest_app` 权限；按 Spring Boot 3.5 官方机制配置
`spring.flyway.url/user/password`，让 Flyway 使用独立原生 DataSource 后迁移成功。

生产最终状态：Flyway V7、checksum `-755177219`、`success=1`；
`user_poster_setting` 和 `poster_enrichment_job` 均为 0 行，活动海报 Job 为 0。
V7 只新增表，不覆盖五类内容表的 `poster_url`。

## 3. 提交与 CI 门禁

本阶段所有独立功能点均在聚焦验证后提交并推送。最终完整门禁：

- admin-server：GitHub Actions run `31279450638`，`mvn clean verify` 成功；
- admin-ui：GitHub Actions run `31276798984`，多架构 Docker build 成功；
- client-server：GitHub Actions run `31279934558`，`mvn clean verify` 成功；
- client-ui：GitHub Actions run `31278522043`，lint、TypeScript 和 Next.js
  production build 成功。

NAS 本机 Maven 因 Spring Boot 3.5.16 parent 未缓存且只读 Maven cache 无法补齐依赖，
未把该本机失败冒充测试结果；Java 完整验证由 GitHub Actions 提供。

部署验收首次发现两个 Phase 5 Bean 同时存在生产构造器和测试构造器，但没有显式
注入标记，Spring 6 因而查找无参构造器并使 `client-server` 重启。分别为
`PosterCredentialCipher` 和 `TmdbApiClient` 增加显式生产构造器注入及 Spring 容器
回归测试后，最终 JAR 先在临时端口 18080 完成完整启动和 `/health=200`，再替换
正式服务。临时冒烟容器已删除。

## 4. 发布、运行与回滚

最终不可变 release：

`deploy/releases/20260809-phase5-f57cc22-e06dc57-780618d-62472bc`

`SHA256SUMS` 只覆盖四个发布入口产物：两个 JAR 和两个 Next.js `server.js`，未对
未改动依赖树重复执行全量哈希。`deploy/current` 已原子指向该 release。

最终运行验证：

- 四个 Compose 容器均为 `running`，`RestartCount=0`；
- 用户端健康接口返回 200，五类内容 API 返回真实分页：电影 270、剧集 168、
  综艺 15、动漫 41、短剧 0；
- 用户端首页、About、个人中心和管理端首页、内容、爬虫路由均返回 200；
- 未认证 poster settings、resolve、enrichment jobs 和管理 API 均返回 401；
- About HTML 同时包含 TMDB 精确免责声明和 approved logo URL；
- Flyway V7 成功，启用 crawler schedule 为 0，活动 crawler Job 为 0，活动海报
  Job 为 0；
- 最终两个后端日志无启动失败或 ERROR，运行海报加密密钥未出现在四容器日志中。

应用回滚可原子切回保留的 Phase 4 release 并重建四服务。V7 是向后兼容的新增空表，
应用回滚时可保留；只有确认需要覆盖数据库时才使用 Phase 5 备份，不把应用回滚
扩大成数据恢复。

## 5. 外部门禁与后续边界

当前 NAS 和本会话没有浏览器、Playwright、Computer Use 或可回读截图的能力，HTTP
不能替代真实视觉和交互验收。仍需在独立浏览器环境验证：

- 管理内容真分页、筛选、排序、显式批量选择和四区爬虫交互；
- 活动/空闲/隐藏状态下轮询节奏、Job 进度、取消和诊断；
- 每用户录入、替换、清除、验证 TMDB 凭据，以及两个用户之间的数据隔离；
- 原图/TMDB 偏好切换、按需补全、显式批量补全、失败回退和移动端行为；
- About/Credits 的免责声明、官方标志和可访问性。

本阶段没有可用的独立测试用户 TMDB 凭据，且禁止使用会话中曾暴露的旧 key，因此
没有向 TMDB 发起真实联调请求。搜索、图片和 configuration 适配器行为已由单元测试
与契约测试覆盖，真实用户凭据联调仍保持未完成状态。

管理端浅色模式 sidebar token 修复属于已确认的 Phase 6 范围，本阶段未提前修改。
