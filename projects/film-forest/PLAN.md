# PLAN.md -- 影视森林 (film-forest) 任务规划

> 版本: v3.0
> 更新时间: 2026-05-01 17:39
> 工作目录: /root/.openclaw/workspace/projects/film-forest/

## 一、项目概述

### 1.1 目标
影视资源聚合网站（类似七味网 pkmp4.xyz），聚合电影/剧集/综艺/动漫/短剧资源，提供浏览、搜索、资源聚合功能。

包含两个端：
- **用户端（client-ui + client-server）**: 面向普通用户的影视资源浏览/搜索/下载网站，UI 潮流好看
- **管理端（admin-ui + admin-server）**: 管理平台，用于内容管理、爬虫管理、数据维护

### 1.2 四个仓库

| 仓库 | 技术栈 | 端口 | 说明 |
|------|--------|------|------|
| client-server | SpringBoot 3 + MyBatis-Plus 3 | 8080 | 用户端后端 API |
| client-ui | Next.js 16 + TailwindCSS + Shadcn UI | 3000 | 用户端前端 |
| admin-server | SpringBoot 3 + MyBatis-Plus 3 | 8081 | 管理端后端（爬虫调度+内容管理） |
| admin-ui | Next.js 16 + TailwindCSS + Shadcn UI | 3001 | 管理端前端 |

### 1.3 数据库

- **MySQL**: 8.x，库名 `film_forest`
- **11张表**: movie / drama / variety / anime / short_drama / episode / resource_online / resource_magnet / resource_cloud / crawler_schedule / crawler_task_log
- **字符集**: `utf8mb4`（支持 emoji），JDBC 连接显式指定 `characterEncoding=utf8`

### 1.4 部署架构

```
用户端:   client-ui (3000)  -->  client-server (8080)  -->  MySQL (3306)
管理端:   admin-ui (3001)   -->  admin-server (8081)   -->  MySQL (3306)
                                        |
                                     七味网等资源站
```

---

## 二、阶段规划

| 阶段 | 内容 | 状态 |
|------|------|------|
| 1 | 数据库设计（11张表） | ✅ 已完成 |
| 2 | MySQL 环境（film_forest 库） | ✅ 已完成 |
| 3 | client-server 后端 CRUD API | ✅ 已完成 |
| 4 | client-ui 用户端页面 | ✅ 已完成（详情页为 Mock） |
| 5 | admin-server 爬虫 API | ✅ 已完成（Content CRUD 缺失） |
| 6 | admin-ui 管理端页面 | ✅ 已完成（Mock 数据） |
| 7 | 前后端 API 真实对接 | 🔵 进行中 |
| 8 | 爬虫核心开发（七味网） | 🔵 进行中 |
| 9 | Docker 容器化部署 | 🔵 进行中 |
| 10 | 增量更新与数据维护 | ⬜ 待开始 |
| 11 | 外网访问与安全加固 | ⬜ 待开始 |

---

## 三、API 规格

### 3.1 client-server（用户端 API，端口 8080）

#### 3.1.1 电影接口

```
GET  /api/movies
  参数: page (默认1), size (默认20), year (可选), genre (可选)
  返回: { code:200, data: { records:[...], total, size, current } }
  字段: id, title, posterUrl, year, director(JSON), actor(JSON), genre(JSON),
        region(JSON), storyline, scoreDouban, scoreImdb, status, createdAt

GET  /api/movies/{id}
  返回: { code:200, data: Movie实体 }
  404: { code:500, message:"电影不存在" }
```

#### 3.1.2 剧集/综艺/动漫/短剧接口
格式同电影，只是 `/api/dramas`, `/api/varieties`, `/api/animes`, `/api/short-dramas`

#### 3.1.3 搜索接口
```
GET  /api/search?keyword=xxx&page=1&size=20
  返回: { code:200, data: { records:[ SearchResult ], total, size } }
  SearchResult: { id, type, title, cover, year, rating, summary }
```

#### 3.1.4 爬虫状态接口
```
GET  /api/crawler/status
  返回: { code:200, data: { schedules:[...] } }
```

---

### 3.2 admin-server（管理端 API，端口 8081）

#### 3.2.1 爬虫调度接口

```
GET  /api/crawler/schedules
  返回: { code:200, data: [ CrawlerSchedule, ... ] }

POST /api/crawler/start/{id}
  启动爬虫，返回: { code:200 }

POST /api/crawler/stop/{id}
  停止爬虫，返回: { code:200 }

POST /api/crawler/schedule
  Body: { name, contentType, sourceSite, cronExpression, batchSize, rateLimitMs, priority, genreFilter }
  返回: { code:200 }

DEL  /api/crawler/schedule/{id}
  删除调度配置

POST /api/crawler/toggle/{id}?enabled=true|false
  切换启用状态
```

#### 3.2.2 内容管理接口（缺失，待实现）

```
GET  /api/content/movies?page=1&size=20&year=&region=&status=
  返回: { code:200, data: { records:[...], total, size, current } }

POST /api/content/movies
  Body: Movie 实体 JSON

PUT  /api/content/movies/{id}
  Body: Movie 实体 JSON（更新）

DEL  /api/content/movies/{id}
  删除电影

# 同类接口
GET/POST/PUT/DEL  /api/content/dramas
GET/POST/PUT/DEL  /api/content/varieties
GET/POST/PUT/DEL  /api/content/animes
GET/POST/PUT/DEL  /api/content/short-dramas
```

#### 3.2.3 资源管理接口（缺失，待实现）

```
GET/POST/PUT/DEL  /api/resources/online
GET/POST/PUT/DEL  /api/resources/magnet
GET/POST/PUT/DEL  /api/resources/cloud
```

---

## 四、页面清单

### 4.1 client-ui（用户端前端）

| 路由 | 页面 | 状态 | 备注 |
|------|------|------|------|
| `/` | 首页 | ✅ 完成 | 分类导航+热门推荐 |
| `/movie` | 电影列表 | ✅ 完成 | 地区/年代筛选 |
| `/movie/[id]` | 电影详情 | ⚠️ Mock | 需对接真实数据 |
| `/drama` | 剧集列表 | ✅ 完成 | |
| `/drama/[id]` | 剧集详情 | ⚠️ Mock | 需对接真实数据 |
| `/variety` | 综艺列表 | ✅ 完成 | |
| `/variety/[id]` | 综艺详情 | ⚠️ Mock | 需对接真实数据 |
| `/anime` | 动漫列表 | ✅ 完成 | |
| `/anime/[id]` | 动漫详情 | ⚠️ Mock | 需对接真实数据 |
| `/short` | 短剧列表 | ✅ 完成 | |
| `/short/[id]` | 短剧详情 | ⚠️ Mock | 需对接真实数据 |
| `/search?q=xxx` | 搜索结果 | ✅ 完成 | 分页+类型跳转 |

### 4.2 admin-ui（管理端前端）

| 路由 | 页面 | 状态 | 备注 |
|------|------|------|------|
| `/` | 仪表盘 | ✅ 完成 | Mock 数据 |
| `/content` | 内容管理 | ⚠️ Mock | 需对接后端 CRUD |
| `/crawler` | 爬虫管理 | ✅ 完成 | 调度配置列表 |
| `/resources` | 资源管理 | ⚠️ Mock | 需对接后端 |
| `/settings` | 系统设置 | ⚠️ Mock | 需持久化 |
| `/stats` | 数据统计 | ⚠️ Mock | 需真实数据 |

---

## 五、爬虫管理设计

### 5.1 爬虫架构

```
CrawlerEngine（调度引擎）
  ├── QiweiCrawler（七味网爬虫，URL 待确认）
  ├── TmdbCrawler（TMDB API 爬虫，API Key 待配置）
  └── extensible（可扩展其他资源站）

CrawlerSchedule（调度配置）
  ├── contentType: movie/drama/variety/anime/short
  ├── sourceSite: qw/tmdb/...
  ├── cronExpression: cron 表达式
  ├── batchSize: 每批抓取数量
  ├── rateLimitMs: 请求间隔
  ├── priority: by_score / by_hot
  └── genreFilter: 类型筛选
```

### 5.2 管理端爬虫页面

- 爬虫控制台：启动/停止/暂停，显示状态总览
- 任务配置：类型选择/优先级/分类筛选
- 任务列表：历史任务/运行中任务
- 爬虫日志：实时日志/历史日志
- 资源配置：爬取间隔/并发数/重试策略

### 5.3 七味网调研（EXPLORE.md）

关键发现（待验证）:
- 站点基于 MacCMS，静态 HTML 可直接抓取
- 资源类型: 磁力 / 百度网盘 / 夸克网盘 / 迅雷 / UC 网盘 / ed2k
- 海报 CDN: https://i1.vvmp4.com/
- AJAX 搜索接口: /index.php/ajax/suggest
- 无验证码，无 JS 渲染需求

---

## 六、Docker 部署

### 6.1 docker-compose.yml（已更新）

路径: `/root/.openclaw/workspace/projects/film-forest/deploy/docker-compose.yml`

四个服务:
- `client-server`: 8080（SpringBoot JAR）
- `client-ui`: 3000（Next.js standalone）
- `admin-server`: 8081（SpringBoot JAR）
- `admin-ui`: 3001（Next.js standalone）

### 6.2 NAS 部署目录

**绝对不能放在 /home/Kkwans/ 用户个人目录，必须放在:**
```
/volume1/docker/film-forest/
├── docker-compose.yml
├── client-server/
├── client-ui/
├── admin-server/
├── admin-ui/
└── logs/
```

### 6.3 环境变量

| 服务 | 变量 | 值 |
|------|------|-----|
| client-server | SPRING_DATASOURCE_URL | jdbc:mysql://192.168.5.110:3306/film_forest?... |
| client-ui | NEXT_PUBLIC_API_URL | http://100.106.29.60:8080 |
| admin-server | SPRING_DATASOURCE_URL | 同上 |
| admin-ui | NEXT_PUBLIC_API_URL | http://100.106.29.60:8081 |

---

## 七、里程碑

- [x] 数据库设计完成（11张表）
- [x] MySQL 环境就绪（film_forest 库）
- [x] client-server 后端 API 可运行（CRUD 验证通过）
- [x] client-ui 用户端主要页面完成
- [x] admin-server 爬虫 API 完成
- [x] admin-ui 管理端主要页面完成
- [x] GitHub 四个仓库创建完成
- [x] 项目重构为四仓库结构
- [x] Docker compose 更新为四服务
- [ ] client-server 和 admin-server 手动启动并验证
- [ ] 详情页对接真实 API 数据
- [ ] 管理端内容管理 CRUD 对接
- [ ] 爬虫核心可实际抓取数据
- [ ] Docker 容器化部署上线

---

## 八、风险与依赖

1. **七味网 URL 未知**: 需调研确认真实影视站 URL，爬虫才能工作
2. **TMDB API Key**: 需要注册 themoviedb.org 获取免费 API Key
3. **手动启动依赖**: client-server 和 admin-server 需要手动 java -jar 启动
4. **NAS Docker 环境**: 需确认 NAS 上 Docker 正确运行
5. **外网访问**: Tailscale 或端口映射配置

---

## 九、相关文档索引

| 文档 | 路径 |
|------|------|
| 任务规划 | tasks/2026-04/film-forest-0425/PLAN.md |
| 七味网调研 | tasks/2026-04/film-forest-0425/EXPLORE.md |
| 数据库设计 | projects/film-forest/doc/database-design.md |
| 建表 SQL | projects/film-forest/database/init.sql |
| 自动任务 | projects/film-forest/AUTO_TASKS.md |
| 本文档 | projects/film-forest/PLAN.md |