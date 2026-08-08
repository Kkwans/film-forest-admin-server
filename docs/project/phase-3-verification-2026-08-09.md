# Phase 3 增量、幂等与资源 Diff 验证

> 状态：已实现、已迁移、已部署
>
> 记录日期：2026-08-09
>
> 代码发布提交：`2ea0c67`
>
> 权威需求 SHA-256：`cc5120b9b106baadcd104f9a9c75d3ffe6eae805fca20e0a710419912d37b4df`

## 1. 交付范围

- 新增 `crawler_source_item`，以 `source_code + content_type + external_id` 唯一定位来源条目，同时保存来源 URL、内部内容 ID、列表/详情 fingerprint、发现/抓取时间和有限解析状态。
- 列表与详情 fingerprint 使用长度前缀 SHA-256，避免分隔符歧义；详情 fingerprint 覆盖内容字段与完整资源集合。
- `LATEST` 固定从第一页开始，对近期页真实回查；超过近期页后，列表 fingerprint 未变的已知条目可跳过详情抓取。连续 20 个旧条目后停止，近期回查页数默认 2，均可通过环境变量调整。
- `FULL` 只能手工触发，不允许启用定时调度；失败重试从 Job 的 `current_page/checkpoint` 恢复，且不受 LATEST `batchSize` 限制。
- 三类资源不再执行 delete-all/insert-all。磁力优先使用 infoHash，网盘使用 `diskType + normalized share URL`，在线资源使用 `source + season + episode + normalized URL` 生成稳定 key。
- 单内容内容写入与资源 Diff 处于同一事务；结果区分 added、updated、removed、unchanged。解析失败、持久化失败或完整资源集合为空时，不清除旧资源。
- V5 只给历史资源补充 `source_code='legacy'` 和 nullable Diff 元数据，不猜测来源、时间或 key，不合并或删除历史行。首次命中时只允许按精确归一化 key 收养一个 legacy 行。

## 2. 小提交与远端验证

本阶段每个独立功能点均在 `master` 精确提交并立即推送：

- `939a153`：来源映射与资源 Diff 数据库结构；
- `49e0747`：来源条目状态与稳定 fingerprint；
- `5bde239`：事务化资源归一化和 Diff；
- `8c17fca`：修复 CI 发现的 Mockito 重载歧义；
- `e5d1029`：LATEST/FULL 模式、触发和 checkpoint 语义；
- `2ea0c67`：将来源状态与 fingerprint 幂等接入真实 Job 执行。

普通 push 使用 GitHub Actions JDK 17 轻量编译。Phase 末 workflow dispatch 执行 `mvn clean verify`，run `31271299278` 成功；验证制品 SHA-256：

`3d3e669b977fab86d1767b16682ca39cce74d043761745e4f90ed1701fd4c199`

## 3. 数据库备份与恢复演练

生产迁移前备份：

- 路径：`deploy/backups/20260809-phase3-09aeb72-19953a5-2ccd64b-2ea0c67/film_forest-predeploy.sql`
- 大小：90,953,913 bytes；
- SHA-256：`93b5df1cc3d1ec17da7e960d1aef27b207ad30b13d289d48be84eb4d554ab57f`。

隔离演练使用固定临时容器、固定临时卷及仅回环地址 `127.0.0.1:43308`。等待正式 `mysqld` 成为 PID 1 后完成生产备份恢复和 V4 → V5 迁移：

- V5 成功记录为 1；
- `crawler_source_item` 为 15 列、0 行；
- 12 个 schedule 仍全部关闭，0 个活动 Job；
- 三类资源行数分别保持 `123878 / 257541 / 4441`，与未迁移生产库完全一致；
- 385,860 条历史资源全部保持 `legacy`，`resource_key` 和 `removed_at` 均未被推测写入；
- 修复后的 CI JAR 完整 Spring Context 启动成功。

部署完成后，临时迁移应用、临时 MySQL、临时卷和 CI 制品目录已按授权精确删除；生产备份与所有 rollback release 保留。

## 4. 生产迁移与发布

生产使用受保护 MySQL root 只执行经过演练的 V5，未扩大 `film_forest_app` 权限。迁移后：

- Flyway V1–V5 均成功；
- `crawler_source_item` 为 15 列、0 行；
- schedule 的 crawl mode 只剩 `latest/full`，启用 schedule 为 0；
- `queued/running/cancel_requested` Job 为 0；
- 三类历史资源总数仍为 385,860，全部保持 `legacy`，keyed 资源为 0。

最终 release：

`deploy/releases/20260809-phase3-09aeb72-19953a5-2ccd64b-2ea0c67`

只替换 `backend/film-forest-admin.jar`。`deploy/current` 原子切换后只重建 `admin-server`；回滚可切回保留的 Phase 2 release，数据库新增表/列可向后兼容保留。

## 5. 运行验收

- 四个 Compose 容器均为 `running`，`RestartCount=0`；
- 常态 `admin-server` 的 `SPRING_FLYWAY_ENABLED=false`；
- 用户首页与管理登录页返回 200；client-server 内容 API 返回 200；admin-server 和 admin-ui 代理的受保护爬虫 API 返回 401；
- `admin-server` 正常完成 Tomcat 与 Spring Context 启动；部署后日志无 ERROR、无爬虫 fetch、无 TMDB 调用；
- 自动 schedule 仍保持关闭，本阶段没有对外站点发起生产抓取。

## 6. 浏览器与外部门禁

Phase 3 没有前端代码或样式变化。当前 NAS 与本会话没有可控制并回读截图的浏览器/Computer Use 工具，因此不能把 HTTP、构建或静态检查冒充真实视觉验收。独立 `film-forest-browser-audit` 的真实浏览器截图与交互仍是外部门禁；本阶段后端发布不依赖该门禁。
