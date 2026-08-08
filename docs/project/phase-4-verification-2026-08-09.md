# Phase 4 用户端功能正确性验证

> 状态：代码、迁移、部署与运行验证通过；真实浏览器视觉/交互门禁待外部执行
>
> 记录日期：2026-08-09
>
> 部署代码：`client-ui@9d27af9`、`client-server@6a74162`、
> `admin-ui@2ccd64b`、`admin-server@065bf5e`

## 1. 交付范围

- 前后端集中定义五类内容映射，短剧保持 `/short` 路由与
  `short_drama` 内部类型。
- 五类列表以 URL search params 为唯一筛选、排序和分页状态，筛选进入
  服务端查询，返回真实 `total/current/pages`。
- 首页只请求轻量聚合接口，同类型热门与最近更新去重，并通过 mixed status
  batch 一次回显多类型用户状态。
- 五类详情收敛到共享 Server Component 数据流、共享 metadata 请求和共享
  layout；资源局部延迟加载，脏字段安全解析，资源失败不伪装成内容 404。
- 搜索支持 URL 恢复、真实类型筛选和分页、请求取消与过期响应隔离、键盘建议、
  历史记录、真实热门词和无结果恢复。
- 所有随机 Picsum 图片已替换为本地中性海报占位图。
- 修复前端 CI 未上传隐藏 `.next` 目录的问题，发布制品现在可下载。

## 2. 标签结构补全

部署验收首次请求 `/api/tags` 时发现生产库从未创建代码长期依赖的 `tag` 和
`content_tag` 表。该问题同时影响用户端标签筛选、详情标签、管理端标签管理和
内容标签关联。

V6 以兼容方式补齐两张表及以下约束，不生成、覆盖或推测任何标签数据：

- 活跃标签名称唯一；
- `content_type + content_id + tag_id` 关联唯一；
- 标签筛选复合索引；
- 标签外键和五类内容类型检查。

V6 在独立 MySQL 8 容器、独立临时卷和仅回环端口中完成表结构、四项约束及最小
关联写入演练。临时容器和卷已删除。

生产 `film_forest_app` 已具有本 schema 的 DDL 权限，但 Flyway 13 还会读取
`performance_schema.user_variables_by_thread`，该最小权限账号被正确拒绝。未扩大
app 权限；V6 使用已演练 SQL执行，并写入与 Flyway 算法一致的 checksum
`-195082576`。该算法先与生产 V4/V5 checksum 完全比对。迁移后 V6 成功记录为 1，
两张新表均为 0 行，四项关键约束齐全。

## 3. 提交与 CI 门禁

本阶段所有独立功能点均完成聚焦验证后立即提交并推送。最终门禁包括：

- client-server：GitHub Actions run `31272655190`，`mvn clean verify` 成功；
  JAR SHA-256 `89ea55d52220b1da95254661272422c8a24e1244e4840df87a2d7748c3a7a59a`。
- client-ui：GitHub Actions run `31273993618`，lint、TypeScript 和 standalone
  build 成功；制品 `client-ui-build` 为 30,190,290 bytes。
- admin-server：GitHub Actions run `31274390821`，完整 `mvn clean verify` 成功；
  JAR SHA-256 `7b6a051d8013184c43ac2eb1ffbfa85b29807a96a66f74826727924d3acec967`。
- 本机 Maven 因 Spring Boot 3.5.16 parent 未缓存且 NAS 无 Maven Central 网络而
  无法运行；没有把该失败冒充测试结果，Java 完整验证由上述 CI 提供。

## 4. 备份、发布与回滚

发布前一致性备份：

- 路径：`deploy/backups/20260809-phase4-9d27af9-6a74162-2ccd64b-2ea0c67/film_forest-predeploy.sql`
- 大小：102,113,434 bytes；
- SHA-256：`4f139791440eb249d7e311ccd4bc2bee0525fb9fe2abb860671548890d5031b8`。

最终不可变 release：

`deploy/releases/20260809-phase4-9d27af9-6a74162-2ccd64b-065bf5e`

release 内 `SHA256SUMS` 共 4,397 项，manifest SHA-256：

`c91d5427480d96186505334f650f9f42b41de51b22cf44738a39157c4e139ef2`

`deploy/current` 已原子指向该 release。应用回滚可切回保留的 Phase 3 release 后
重建受影响服务；V6 仅新增空表，对 Phase 3 向后兼容，可保留。只有确认需要恢复
数据库时才使用上述备份，不把应用回滚扩大成数据覆盖。

## 5. 生产运行验证

- 四个 Compose 容器均为 `running`，`RestartCount=0`；
- 五类内容 API 均返回真实分页结构：电影 270、剧集 168、综艺 15、动漫 41、
  短剧 0；电影第 2 页的 `current=2`、`size=2`、`pages=135`；
- 标签 API 返回真实空分页：`total=0`、`current=1`、`pages=0`，不再返回 500；
- 首页聚合结构正确，同类型 hot/latest 无重复 ID；
- 真实标题搜索在 `typeFilter=movie` 下返回 1 条电影结果和真实分页；
- 五类列表路由、带 URL 筛选的电影列表、搜索页、共享详情页、用户首页及管理登录页
  均返回 200；
- 未认证 mixed status batch 与管理端标签 API 均返回 401；
- Flyway V6 成功记录为 1，启用 schedule 为 0，活动 Job 为 0；
- 部署后四容器近期日志均为 0 个 ERROR/启动失败/未处理 Promise 错误。

## 6. 外部浏览器门禁

当前 NAS 和本会话没有浏览器、Playwright、Computer Use 或可回读截图的工具。
本阶段包含显著用户端交互变化，因此 HTTP、构建和静态检查不能替代真实浏览器验收。

按已确认约定，仍需在独立 `film-forest-browser-audit` 环境验证：

- URL 刷新、复制、back/forward 与页码恢复；
- 桌面 5–6 列和移动端 2 列、空态与中性占位图；
- 搜索建议键盘操作、请求切换与无结果恢复；
- 五类详情、资源局部失败和登录后 mixed status 回显；
- 浅色/深色、响应式布局和 reduced-motion。

在截图与交互证据返回前，不将该外部门禁标记为已完成。
