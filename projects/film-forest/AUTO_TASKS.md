# AUTO_TASKS.md -- 影视森林自动开发任务

> 最后更新: 2026-05-01 17:39
> 定时任务: film-forest-continuous-dev (每10分钟, 超时30分钟)
> 工作目录: /root/.openclaw/workspace/projects/film-forest/

---

## 一、项目结构（四个仓库）

| 仓库 | 技术栈 | 端口 | 路径 |
|------|--------|------|------|
| client-server | SpringBoot 3 + MyBatis-Plus | 8080 | projects/film-forest/client-server/ |
| client-ui | Next.js 16 + TailwindCSS + Shadcn UI | 3000 | projects/film-forest/client-ui/ |
| admin-server | SpringBoot 3 + MyBatis-Plus | 8081 | projects/film-forest/admin-server/ |
| admin-ui | Next.js 16 + TailwindCSS + Shadcn UI | 3001 | projects/film-forest/admin-ui/ |

---

## 二、四个仓库代码现状

### 2.1 client-server（用户端后端） ✅ 完整

**代码完整性:**
- Controller 7个: MovieController, DramaController, VarietyController, AnimeController, ShortDramaController, SearchController, CrawlerController, HealthController
- Entity/Mapper/Service 完整（movie/drama/variety/anime/short_drama/episode/resource_online/resource_magnet/resource_cloud/crawler_schedule/crawler_task_log）
- 构建产物: `target/film-forest-backend-0.0.1-SNAPSHOT.jar`
- 启动命令: `cd /root/.openclaw/workspace/projects/film-forest/client-server && java -jar target/film-forest-backend-0.0.1-SNAPSHOT.jar`

**API 路由:**
```
GET  /api/movies          -- 电影列表（page/size/year/genre参数）
GET  /api/movies/{id}     -- 电影详情
POST /api/movies          -- 新增电影
PUT  /api/movies/{id}     -- 更新电影
DEL  /api/movies/{id}     -- 删除电影
GET  /api/dramas          -- 剧集列表
GET  /api/dramas/{id}     -- 剧集详情
GET  /api/varieties       -- 综艺列表
GET  /api/varieties/{id}  -- 综艺详情
GET  /api/animes          -- 动漫列表
GET  /api/animes/{id}     -- 动漫详情
GET  /api/short-dramas    -- 短剧列表
GET  /api/short-dramas/{id} -- 短剧详情
GET  /api/search?keyword=xxx&page=1&size=20 -- 全局搜索（合并5类内容）
GET  /api/crawler/status  -- 爬虫调度状态
GET  /api/crawler/schedules -- 爬虫调度配置列表
POST /api/crawler/start/{id} -- 启动爬虫
POST /api/crawler/stop/{id}  -- 停止爬虫
```

**数据库:** film_forest 库，11张表（见数据库小节）

**运行状态:** 需要手动启动

---

### 2.2 client-ui（用户端前端） ✅ 完整

**页面清单:**
- `/` -- 首页（分类导航 + 热门推荐）
- `/movie` -- 电影列表（地区/年代筛选 UI，后端参数已对接）
- `/movie/[id]` -- 电影详情（在线播放Tab + 磁力下载Tab）
- `/drama` -- 剧集列表
- `/drama/[id]` -- 剧集详情（含剧集选择器）
- `/variety` -- 综艺列表
- `/variety/[id]` -- 综艺详情
- `/anime` -- 动漫列表
- `/anime/[id]` -- 动漫详情
- `/short` -- 短剧列表
- `/short/[id]` -- 短剧详情
- `/search` -- 搜索结果页（keyword 参数，分页）

**组件库:** shadcn/ui（Card/Badge/Button/Input/Select/tabs）
**状态管理:** Zustand (movieStore)
**HTTP:** Axios，baseURL = NEXT_PUBLIC_API_URL (http://100.106.29.60:8080)

**已知问题:**
- 前端 build 产物 `.next/` 存在，但运行时需要 `node server.js` 在 3000 端口
- 详情页播放资源为 Mock 数据（在线播放源/磁力链接需后端真实数据）

---

### 2.3 admin-server（管理端后端） ⚠️ 部分完整

**代码完整性:**
- Controller: CrawlerController（爬虫管理 API）
- Entity/Mapper/Service: CrawlerSchedule, CrawlerTaskLog
- 缺少: 内容管理 API（ContentController/ResourceController 等）
- 启动命令: `cd /root/.openclaw/workspace/projects/film-forest/admin-server && java -jar target/film-forest-admin-0.0.1-SNAPSHOT.jar`

**API 路由（爬虫相关）:**
```
GET  /api/crawler/status      -- 爬虫状态概览
GET  /api/crawler/schedules   -- 所有调度配置
GET  /api/crawler/schedule/{id} -- 单个调度配置
POST /api/crawler/schedule    -- 创建/更新调度
DEL  /api/crawler/schedule/{id} -- 删除调度
POST /api/crawler/start/{id}  -- 启动爬虫
POST /api/crawler/stop/{id}   -- 停止爬虫
POST /api/crawler/toggle/{id}?enabled=true -- 切换启用状态
GET  /api/crawler/logs        -- 爬虫日志
```

**API 路由（内容管理，缺失）:**
```
GET  /api/content/movies      -- 电影列表（需要实现）
POST /api/content/movies       -- 新增电影
PUT  /api/content/movies/{id} -- 更新电影
DEL  /api/content/movies/{id} -- 删除电影
同类接口: /api/content/dramas, /api/content/varieties, /api/content/animes, /api/content/short-dramas
```

**运行状态:** 需要手动启动（端口 8081）

---

### 2.4 admin-ui（管理端前端） ✅ 完整

**页面清单:**
- `/` -- 仪表盘（统计数据 + 最近内容 + 爬虫状态）
- `/content` -- 内容管理（表格，CRUD Mock 数据）
- `/crawler` -- 爬虫管理（调度配置列表 + 启动/停止/切换启用）
- `/resources` -- 资源管理（来源列表 + 磁力/云盘统计 Mock 数据）
- `/settings` -- 系统设置（5 个配置卡片，Mock 数据）
- `/stats` -- 数据统计（图表占位，Mock 数据）

**已知问题:**
- `/content` 页面 CRUD 为 Mock 数据，需要后端 `/api/content/*` 对接
- `/resources` 页面为 Mock 数据，需要后端资源 API
- `/settings` 页面为 Mock 配置，需持久化
- `/stats` 图表占位，无真实统计数据

---

## 三、数据库

**库名:** film_forest
**表结构（11张）:**

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| movie | 电影主表 | id, title, poster_url, year, director(JSON), actor(JSON), genre(JSON), region(JSON), storyline, score_douban, status |
| drama | 剧集主表 | id, title, poster_url, year, total_episode, storyline, score_douban, status |
| variety | 综艺主表 | id, title, poster_url, year, total_episode, storyline, score_douban, status |
| anime | 动漫主表 | id, title, poster_url, year, total_episode, storyline, score_douban, status |
| short_drama | 短剧主表 | id, title, poster_url, year, total_episode, duration, storyline, status |
| episode | 剧集子表 | id, content_type, content_id, season, episode_number, title, poster_url |
| resource_online | 在线播放源 | id, content_type, content_id, episode_id, source_name, source_url, sort |
| resource_magnet | 磁力链接 | id, content_type, content_id, episode_id, title, magnet_url, resolution |
| resource_cloud | 网盘链接 | id, content_type, content_id, episode_id, disk_type, title, url, password |
| crawler_schedule | 爬虫调度配置 | id, name, content_type, source_site, enabled, cron_expression, batch_size, status, last_run_time, next_run_time |
| crawler_task_log | 爬虫任务日志 | id, schedule_id, schedule_name, status, items_crawled, started_at, finished_at |

**注意:** movie/drama/variety/anime/short_drama 表中 director/actor/genre/region 为 JSON 数组格式（MySQL 5.7+ JSON 类型）

---

## 四、部署状态

**服务状态（2026-05-01 17:39 实际检测）:**

| 服务 | 期望端口 | 运行状态 | 备注 |
|------|----------|----------|------|
| client-server | 8080 | ❌ 未运行 | 需手动 java -jar 启动 |
| client-ui | 3000 | ✅ 运行中 | node server.js |
| admin-server | 8081 | ❌ 未运行 | 需手动 java -jar 启动 |
| admin-ui | 3001 | ✅ 运行中 | node server.js |
| MySQL | 3306 | ✅ 运行中 | docker mysql8 容器 |

**启动命令:**

```bash
# 启动 client-server
cd /root/.openclaw/workspace/projects/film-forest/client-server
java -jar target/film-forest-backend-0.0.1-SNAPSHOT.jar &

# 启动 admin-server
cd /root/.openclaw/workspace/projects/film-forest/admin-server
java -jar target/film-forest-admin-0.0.1-SNAPSHOT.jar &

# 重启 client-ui
cd /root/.openclaw/workspace/projects/film-forest/client-ui
pkill -f "node server.js" || true
node server.js &

# 重启 admin-ui
cd /root/.openclaw/workspace/projects/film-forest/admin-ui
pkill -f "node server.js" || true
node server.js &
```

**Docker 部署（docker-compose）:**
```bash
cd /root/.openclaw/workspace/projects/film-forest/deploy
docker-compose up -d
```
注意: docker-compose.yml 已更新为四个服务（client-server:8080, client-ui:3000, admin-server:8081, admin-ui:3001）

---

## 五、待完成任务（按优先级）

### P0 -- 服务运行（阻塞所有功能）

- [ ] **手动启动 client-server**（`java -jar film-forest-backend-0.0.1-SNAPSHOT.jar`，端口 8080）
- [ ] **手动启动 admin-server**（`java -jar film-forest-admin-0.0.1-SNAPSHOT.jar`，端口 8081）
- [ ] **验证 client-server API 正常**（`curl localhost:8080/api/movies?page=1&size=2`，当前 500 错误：NoClassDefFoundError，可能 JAR 打包问题，需重新 mvn package）
- [ ] **验证 admin-server API 正常**（`curl localhost:8081/api/crawler/status`）

### P1 -- 用户端功能完善

- [ ] **详情页真实数据**: client-ui 的 `/movie/[id]` 等详情页目前是 Mock 数据，需要调用 client-server API 获取真实数据
- [ ] **搜索功能验证**: `GET /api/search?keyword=xxx` 需验证返回结构
- [ ] **分类筛选**: 电影/剧集列表页的地区/年代筛选已传参后端，需验证过滤效果

### P1 -- 管理端功能完善

- [x] **内容管理 API**: ContentController 已实现 `src/main/java/com/filmforest/content/controller/ContentController.java`，commit: ec9cd5a（未 push，需手动 mvn package + 重启 admin-server）
- [ ] **内容管理对接**: admin-ui `/content` 页面对接后端真实 API
- [ ] **资源管理对接**: admin-ui `/resources` 页面需要后端 ResourceController
- [ ] **设置持久化**: admin-ui `/settings` 配置需保存到数据库
- [ ] **统计真实数据**: admin-ui `/stats` 需对接真实统计 API

### P2 -- 爬虫开发

- [ ] **七味网 URL 确认**: 目前七味网目标站点 URL 未知，需要调研确认
- [ ] **爬虫核心实现**: QiweiCrawler 或其他爬虫核心，采集影视数据
- [ ] **增量更新策略**: 磁力/网盘链接有时效性，需实现增量更新
- [ ] **爬虫可视化**: admin-ui 爬虫页面已有 UI，需对接后端真实状态

### P3 -- Docker 部署

- [ ] **编写四个 Dockerfile**: 分别为 client-server, client-ui, admin-server, admin-ui
- [ ] **更新 docker-compose.yml**: 已更新，需确认四个服务完整
- [ ] **NAS 部署**: 将 docker-compose 部署到 NAS `/volume1/docker/film-forest/`
- [ ] **外网访问**: 配置 Tailscale 或端口映射

---

## 六、定时任务配置

**任务 ID:** 9e2c69d6-86b6-4a51-8904-f9705f5d3bad
**任务名:** film-forest-continuous-dev
**执行周期:** 每 10 分钟
**超时时间:** 30 分钟（1800 秒）
**Session:** isolated（独立会话）
**Delivery:** none（不推送结果）
**描述提示词:** 见上方 cron add 命令的完整 prompt

**管理命令:**
```bash
# 查看状态
openclaw cron list --token hk123456

# 查看运行历史
openclaw cron runs --id 9e2c69d6-86b6-4a51-8904-f9705f5d3bad --token hk123456

# 删除任务
openclaw cron rm 9e2c69d6-86b6-4a51-8904-f9705f5d3bad --token hk123456

# 手动触发一次
openclaw cron run 9e2c69d6-86b6-4a51-8904-f9705f5d3bad --token hk123456
```

---

## 七、GitHub 仓库

| 仓库 | GitHub URL | 本地路径 |
|------|-----------|----------|
| film-forest-client-server | https://github.com/Kkwans/film-forest-client-server | client-server/ |
| film-forest-client-ui | https://github.com/Kkwans/film-forest-client-ui | client-ui/ |
| film-forest-admin-server | https://github.com/Kkwans/film-forest-admin-server | admin-server/ |
| film-forest-admin-ui | https://github.com/Kkwans/film-forest-admin-ui | admin-ui/ |

**同步命令:**
```bash
git -C /root/.openclaw/workspace/projects/film-forest/client-server pull origin main
git -C /root/.openclaw/workspace/projects/film-forest/client-ui pull origin main
git -C /root/.openclaw/workspace/projects/film-forest/admin-server pull admin-server main
git -C /root/.openclaw/workspace/projects/film-forest/admin-ui pull origin main
```

---

## 八、重要教训（永久记忆）

1. **不要编造数据和进度**: 所有汇报必须有源码或运行状态支撑
2. **项目重构后更新路径**: 目录结构变更后，所有引用路径需同步更新
3. **手动操作任务需注明**: 无法自动完成的任务在 AUTO_TASKS.md 中注明，不阻塞
4. **cron 任务超时问题**: cron 默认 60s 超时，大任务需设置 `--timeout-seconds 1800`
5. **数据库 JSON 字段**: MySQL JSON 类型字段返回的是字符串，前端需 JSON.parse()