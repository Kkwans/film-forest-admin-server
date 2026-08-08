# Phase 1 爬虫 Job 生命周期验证与生产交付证据

> 状态：Phase 1 已完成并部署，自动调度保持关闭
>
> 记录日期：2026-08-08
>
> 执行分支：四仓 `master`
>
> 权威需求 SHA-256：`9fbbd6e2d33abc40347712c1c78164b0b945315efa87d32fb22dabb66324470b`
>
> 计划补充 SHA-256：`18bc4fecccbf2f6f34a7c25dffc406c4dcf4bcdaf0fc584e703100f18c5a232e`
>
> 总控契约 SHA-256：`bd6229ebcff310e44d82c380b9931924cbbacab7288b7acfd0d64e83a2c9174a`

本文记录 Phase 1 的代码、数据库、发布工件和 NAS 生产运行证据。TMDB 海报源与管理端浅色侧栏不属于本阶段实现范围，已经进入后续阶段计划。

## 1. 交付范围与提交

### admin-server

- `c1bd26e feat(crawler): 建立可靠 Job 生命周期`
- `ab23ee7 test(crawler): 覆盖 Job 并发取消与恢复`
- `8e5dbde fix(crawler): 统一历史运行时间为 UTC`
- `d3de5c5 fix(crawler): 保留既有 UTC 运行时间`

最终 `master` 与 `origin/master` 均为 `d3de5c59e7dae8c6810d18d82bfe951586a8a272`。

### admin-ui

- `2ccd64b feat(crawler): 适配可靠 Job 状态`

最终 `master` 与 `origin/master` 均为 `2ccd64b63f50c921a13b147c62322f3810b64bda`。

本阶段没有修改 `client-server` 或 `client-ui` 的源码。

## 2. Job 生命周期结果

- `crawler_task_log` 成为权威 Job 表，状态集合为 `queued`、`running`、`cancel_requested`、`success`、`partial_success`、`failed`、`cancelled`、`interrupted`。
- 生成列 `active_schedule_id` 与唯一索引 `uk_crawler_job_active_schedule` 在数据库层保证同一 schedule 最多一个活动 Job。
- 创建 Job 与派发解耦：事务提交后发布事件，worker 再原子领取 `queued` Job。
- executor 使用固定单 worker、有限队列和拒绝策略，不使用 `CallerRunsPolicy`，不会在调度线程执行爬虫业务。
- 队列饱和时 Job 留在数据库 `queued` 状态，由周期派发补领，不把暂时背压误记为业务失败。
- 取消先持久化 `cancel_requested`，再取消当前 HTTP call，并在睡眠、HTTP、解析和写入边界退出。
- Job 持久化当前页、当前内容、计数、checkpoint、heartbeat 与 progress 时间；重启和心跳过期恢复为 `interrupted`。
- scheduler 只处理 `enabled=1` 且 `next_run_time` 到期的 schedule，并在行锁内复核；cron 按 Asia/Shanghai 解释，运行时间统一按 UTC 存储。
- NAS 默认并发为 `worker=1`、`detail=1`、`queue=4`。
- 管理端支持新状态展示、筛选、进度与可重试终态；旧的 `stopped` 不再作为新 Job 状态。

## 3. 源码与自动化验证

### Maven 环境限制

`mvn verify` 与 `mvn -o verify` 均在项目模型解析阶段停止：NAS 无法解析 Maven Central，且本地缓存缺少 Spring Boot `3.5.16` parent。该失败发生在源码编译前，不能记为测试通过，也没有通过跳过 hooks 或降低版本规避。

### 等价编译与聚焦测试

- 使用生产 fat JAR 中精确的 Spring Boot 3.5.16 运行依赖和本地 Lombok 1.18.32，通过 JDK 17 compiler module 全量编译 96 个 main 源文件。
- 最终编译显式使用 `-parameters`；反射检查覆盖 94 个 Controller 方法、151 个参数，全部 `isNamePresent=true`。
- 使用最新 Phase 1 test classes 与 JUnit Jupiter engine 执行 133 个聚焦测试：`passed=133 failed=0 aborted=0`。
- 覆盖数据库原子去重、manual/scheduled/retry 冲突、after-commit 派发、取消、HTTP/解析/写入边界、unknown content type、partial success、失败、checkpoint、heartbeat、stale recovery 与 UTC 时间语义。
- 失败路径测试会按预期记录 `Unknown contentType` error log；该日志由测试主动触发，测试本身通过。
- 本机仅缓存 Spring Boot Test 3.2.5，而生产运行依赖为 3.5.16；依赖 `@WebMvcTest` 的旧上下文测试因版本不兼容不能运行。相关源码已成功编译，运行 API 边界由独立工件预检和生产 HTTP 验收补充验证。

### admin-ui

- `npm run build` 成功，Next.js 16.3.0 完成 TypeScript/production build，共生成 13 个页面/路由，`/crawler` 成功生成。
- 针对改动文件执行 ESLint 时仍有 13 个既有 error、9 个 warning，主要是历史页面 effect 中 setState、render 内动态组件和未转义字符；本阶段不混入无关 UI 重构，因此不能宣称 lint 全绿。
- 当前 NAS 没有本地浏览器或 Computer Use 环境，尚未完成真实浏览器截图、交互与浅色模式视觉验收。

## 4. 数据库迁移与时间语义

### 隔离演练

- V1→V2→V3 在固定名称、`network=none`、tmpfs MySQL 8 容器中成功执行。
- 历史样例 `2026-08-08 12:09:10` 在 V3 前后原值不变；旧 `running` 转为 `interrupted`，schedule 转为 `idle` 且 `enabled=0`、`next_run_time=NULL`。
- 同一 schedule 的第二个活动 Job 插入被 `uk_crawler_job_active_schedule` 以 MySQL error 1062 原子拒绝。
- 另以只有 `film_forest.*` 权限、没有 `performance_schema` SELECT 权限的隔离账号运行 Flyway 13，V1–V3 自动迁移和 migrate 后 validate 均成功。

生产旧服务运行在 UTC 容器中，生产 `last_run_time` 与容器日志证明历史无时区 datetime 已经是 UTC。V3 因此保留历史运行时间，未执行错误的减 8 小时平移。

### 生产迁移

首次让 Spring Boot/Druid 直接启用 Flyway 时，Flyway 捕获了 `performance_schema.user_variables_by_thread` 权限拒绝，但 Druid 同时废弃连接，后续读取 `foreign_key_checks` 失败；容器重启 1 次后被立即停止，V3 尚未开始执行。

未扩大共享 MySQL 权限，也未再次修改 root。随后使用与应用相同的 Flyway 13、MySQL Connector/J 9.7.0 和 `film_forest_app`，通过普通 JDBC 一次性运行器执行迁移。Flyway 自动完成 migrate、写入 schema history 和 migrate 后 validate：

- 生产 schema 从 version 2 升至 version 3；
- V3 description 为 `establish crawler job lifecycle`；
- V3 checksum 为 `-632726101`；
- `success=1`。

最终数据库只读验收：

- 12 个未删除 schedule，`enabled=0`、`next_run_time IS NOT NULL=0`、`status=idle` 共 12 个；
- Job 共 6761 条，数量与迁移前一致；
- `success=6638`、`failed=107`、`interrupted=16`；
- `queued/running/cancel_requested` 活动 Job 为 0；
- `heartbeat_at`、`checkpoint`、`active_schedule_id` 三个关键列齐全；
- 活动 schedule 唯一索引存在且只有一个定义。

## 5. 备份、发布工件与部署

停服前与 crawler 静默后分别创建完整逻辑备份：

| 备份 | 大小 | SHA-256 |
| --- | ---: | --- |
| `film_forest-predeploy.sql` | 90,047,135 bytes | `c8050c9740f60fcc322396b44050f59dc4f1b81ccdb0da0c05c1e1fee3bfde52` |
| `film_forest-quiesced.sql` | 90,047,135 bytes | `c02b434430ad581ea88dcd5eeb383763649f2b58c45ae7ba7e1099f23460c76f` |

两份 dump 均为 19 张表，不包含 `CREATE DATABASE` 或 `USE`；备份目录、dump、`.env` 与 Compose 快照保持受限权限。

最终发布目录：

`deploy/releases/20260808-phase1-09aeb72-19953a5-2ccd64b-d3de5c5`

- `film-forest-admin.jar` SHA-256：`23a50300a8bb34ae906a85a5c39a15127e8b8acdda6ee9bf78fb1d1030ea2afa`；
- Spring Boot 嵌套依赖保持 ZIP `stored`，完整 `unzip -t` 通过；
- `SHA256SUMS` 中四个核心工件全部通过；
- 发布目录最终恢复为目录 0555、文件 0444；
- `deploy/current` 指向上述 Phase 1 发布目录；
- Phase 0 发布目录保留为应用回滚点。

首个 Phase 1 JAR 因手工编译遗漏 `-parameters`，在生产探针中暴露 Spring 参数绑定错误；管理前后端随即临时回切 Phase 0。修正后从原始 Phase 0 fat JAR 重新打包，完整替换 `BOOT-INF/classes` 与资源，并在 18081 一次性容器中通过启动与请求重放后才再次发布。错误候选未作为最终 `current` 保留，相关临时容器和可重建缓存已精确清理。

## 6. 生产运行验收

- 四个容器均归属 Compose project `film-forest`：`client-server`、`admin-server`、`client-ui`、`admin-ui`。
- 四容器均为 `running`，`RestartCount=0`。
- 常态 admin-server 明确为 `Flyway=false`。
- `http://127.0.0.1:3000/` 返回 200。
- `http://127.0.0.1:3001/login` 返回 200。
- `http://127.0.0.1:8080/api/content/movie` 返回预期 401。
- admin-server 的 crawler status、未知 jobs 路径、带筛选参数的 logs、带 query 参数的 toggle 均稳定返回 401 和 65 字节统一响应，不再产生日志异常。
- `http://127.0.0.1:3001/api/crawler/status` 经管理端代理返回预期 401。
- admin-server 日志包含 Tomcat 8081 与 `Started AdminApplication`，最终部署和请求重放后没有 ERROR、参数绑定异常或爬虫 fetch 记录。
- admin-ui 在后端就绪后单独重建，最终日志只有 Next.js ready 信息，没有代理错误。
- 自动调度保持关闭，生产未启动真实抓取任务。

## 7. 回滚与剩余门禁

- 应用回滚：原子切换 `deploy/current` 至保留的 Phase 0 release，并只重建受影响服务；该路径已在生产中实际执行并验证可用。
- schema 回滚：V3 新增列和约束对 Phase 0 应用向后兼容，默认不做破坏性逆迁移；若必须恢复 schema，先停止写入，再使用静默备份恢复到隔离库验证后执行。
- Phase 1 结束时所有 schedule 仍关闭；重新启用属于后续业务授权，不是本阶段自动动作。
- 当前 NAS 无真实浏览器，管理端 crawler 页的最终视觉、移动端和交互验收仍待 TX5Pro 浏览器任务完成。
- TMDB 每用户配置、海报双来源语义和管理端浅色侧栏修复均未在 Phase 1 实现；须按已确认计划进入对应后续阶段。

完成本证据提交并核验远端 OID 后，Phase 1 Goal 可标记 complete。
