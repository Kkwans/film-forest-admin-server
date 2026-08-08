# Phase 2 来源适配、Fetcher、Parser 与 TMDB 基础验证

> 状态：已实现、已迁移、已部署
>
> 记录日期：2026-08-09
>
> 代码发布提交：`7737a15`
>
> 权威需求 SHA-256：`cc5120b9b106baadcd104f9a9c75d3ffe6eae805fca20e0a710419912d37b4df`

## 1. 交付范围

- `sourceSite` 先经 `SourceAdapterRegistry` 选择来源 Adapter；现有“七味网”名称与稳定代码 `pkmp4` 指向同一个生产适配器，未知来源在发出请求前失败。
- 新 Fetcher 返回请求/最终 URL、状态码、内容类型、正文、耗时、分类、重试性与有限响应头，支持取消、配置化超时/代理、有限退避和 `Retry-After`。
- pkmp4 列表、详情和资源解析返回类型对象；Fixture 测试直接调用生产 Parser。
- 在线资源无法确定数字集号时仍保存原始标题和 `sourceOrder`，覆盖期数、EP、正片、先导片、特别篇、上下集和日期期数语义。
- 连续三条核心结构失效会终止当前来源 Job；有限诊断只含 external ID、缺失字段、分类和页面指纹，不保存整页内容。
- 新增 `content_poster_match`，来源原图与 TMDB 稳定 ID、poster path、语言、置信度和诊断并存，不覆盖五类内容现有 `poster_url`。
- TMDB 适配器按 Movie/TV 分流，使用 Search、Images 和 Configuration；中文、英文、无语言依次回退，低于 `0.8500` 的候选只保存诊断。
- 支持 v3 API key 与 Read Access Token；敏感查询参数在 FetchResult、日志与异常边界中统一脱敏。

## 2. 小提交与远端验证

本阶段每个独立功能点均在 `master` 精确提交并立即推送：

- `6dea2f7`：可取消分类 Fetcher；
- `fb4666a`：pkmp4 类型化来源 Adapter/Parser；
- `fd592de`：生产调用链与事务持久化；
- `7d19480`：双海报存储迁移；
- `fd64558`：敏感查询凭据脱敏；
- `20b5ac5`：TMDB 智能匹配适配器；
- `3342a0a`：错误页面类型拒绝；
- `6bf925a`：连续结构失效熔断；
- `3303ba8`：阶段末完整验证开关；
- `7737a15`：真实启动发现的 Fetcher 构造器修复。

普通 push 使用 GitHub Actions JDK 17 轻量编译；Phase 末通过 workflow dispatch 执行一次 `mvn clean verify`。修复后完整回归 run `31269718272` 成功，JAR artifact SHA-256 为：

`c4ad96cae499663d57e27dd36d77a19f4b7aebadb5f983fb28647b75fdc6ec08`

## 3. 数据库备份与恢复演练

生产迁移前备份：

- 路径：`deploy/backups/20260809-phase2-09aeb72-19953a5-2ccd64b-3303ba8/film_forest-predeploy.sql`
- 大小：90,951,587 bytes；
- SHA-256：`a4fc760cf8bd384c82d32d8884aa2c3d5b292d5d886ba7c589095fbd407babe0`。

隔离演练使用固定临时容器 `film-forest-phase2-restore-20260809`、固定临时卷及仅回环地址 `127.0.0.1:43307`。首次绑定目录因 NAS ACL 不可用；改用临时卷后又发现 MySQL 官方镜像临时初始化 server 的就绪竞态。等待正式 `mysqld` 成为 PID 1 后，重新创建唯一隔离 schema 并完成：

- 恢复生产备份；
- Flyway 从 v3 升至 v4；
- `content_poster_match` 为 1 张表、14 列；
- 12 个 schedule 仍全部关闭；
- 修复后的 CI JAR 完整 Spring Context 启动成功；
- Flyway 再次 validate 报告 schema 已是 v4。

临时容器、临时卷、演练目录和 CI 暂存 JAR 已按授权精确清理；生产备份保留。

## 4. 生产迁移与发布

生产使用受保护 MySQL root 只执行经过演练的 V4，未扩大 `film_forest_app` 权限。迁移后：

- Flyway V1–V4 均 `success=1`；
- `content_poster_match` 存在且为 14 列；
- 表内记录为 0；
- 启用 schedule 为 0；
- `queued/running/cancel_requested` Job 为 0。

最终 release：

`deploy/releases/20260809-phase2-09aeb72-19953a5-2ccd64b-7737a15`

只替换 `backend/film-forest-admin.jar`，`deploy/current` 原子切换后仅重建 `admin-server`；失败路径会切回保留的 Phase 1 release。Phase 1 release 与生产备份均未删除。

## 5. 运行验收

- 四个 Compose 容器均为 `running`，`RestartCount=0`；
- 常态 `admin-server` 为 `SPRING_FLYWAY_ENABLED=false`；
- 用户首页与管理登录页返回 200；
- client-server、admin-server 及 admin-ui 代理的受保护 API 均稳定返回 401；
- 部署后 admin-server 日志无 ERROR，无爬虫 fetch，无 TMDB 调用；
- 未使用、保存或部署本次会话中暴露的 TMDB 凭据。

## 6. 浏览器与外部门禁

Phase 2 没有前端代码或样式变更。当前 NAS 和本会话没有可控制并回读截图的浏览器/Computer Use 工具，因此不能把 HTTP 200、构建或静态检查冒充真实视觉验收。独立 `film-forest-browser-audit` 的真实浏览器截图与交互仍是外部门禁；本阶段运行发布不依赖该门禁，但后续 UI 阶段完成声明必须补齐。

## 7. 官方 TMDB 契约

- [Application Authentication](https://developer.themoviedb.org/docs/authentication-application)
- [Search and Query for Details](https://developer.themoviedb.org/docs/search-and-query-for-details)
- [Image Basics](https://developer.themoviedb.org/docs/image-basics)
- [Image Languages](https://developer.themoviedb.org/docs/image-languages)
- [Rate Limiting](https://developer.themoviedb.org/docs/rate-limiting)
- [FAQ / Attribution](https://developer.themoviedb.org/docs/faq)
