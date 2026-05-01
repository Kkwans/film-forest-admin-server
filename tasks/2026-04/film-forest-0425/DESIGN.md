# DESIGN -- 影视森林 详细设计

## 架构设计

```
+----------------------------------------------+
|              NAS (ARM64 Docker)               |
|                                               |
|  +------------------+  +------------------+   |
|  | film-forest-     |  | film-forest-     |   |
|  | backend          |  | frontend         |   |
|  | (SpringBoot3)    |  | (Nginx)          |   |
|  | :8080            |  | :80/443          |   |
|  +--------+---------+  +--------+---------+   |
|           |                      |             |
|           v                      |             |
|  +------------------+            |             |
|  | mysql8           | <----------+             |
|  | :3306            |   (前端通过后端API访问)   |
|  | db: film_forest  |                          |
|  +------------------+                          |
|                                               |
|  +------------------+                          |
|  | openclaw         |   (独立，互不影响)        |
|  | :18789           |                          |
|  +------------------+                          |
+----------------------------------------------+
                |
        Tailscale (100.106.29.60)
                |
        +-------+-------+
        |               |
   用户端访问       管理端访问
```

## 模块划分

### 后端模块 (SpringBoot)

| 模块 | 包名 | 职责 |
|------|------|------|
| content | com.filmforest.content | 影视内容 CRUD（电影/剧集/综艺/动漫/短剧） |
| resource | com.filmforest.resource | 资源管理（磁力/网盘链接） |
| crawler | com.filmforest.crawler | 爬虫模块（七味网等资源站抓取） |
| search | com.filmforest.search | 搜索功能（标题/分类/年份等） |
| admin | com.filmforest.admin | 管理端 API（内容审核、数据维护） |
| common | com.filmforest.common | 公共工具（分页、响应封装、异常处理） |

### 前端模块

| 端 | 技术栈（待定） | 功能 |
|----|----------------|------|
| 用户端 | 待选，UI 潮流方向 | 资源浏览、搜索、详情、下载链接展示 |
| 管理端 | 待选 | 内容管理、爬虫任务管理、数据统计 |

## 核心接口设计（初版）

### 用户端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/movies | 电影列表（分页、筛选） |
| GET | /api/movies/{id} | 电影详情 + 资源链接 |
| GET | /api/dramas | 电视剧列表 |
| GET | /api/dramas/{id} | 电视剧详情 + 剧集列表 |
| GET | /api/varieties | 综艺列表 |
| GET | /api/animes | 动漫列表 |
| GET | /api/search | 全局搜索 |
| GET | /api/categories | 分类列表 |
| GET | /api/hot | 热门推荐 |
| GET | /api/latest | 最新上线 |

### 管理端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /admin/api/content | 新增内容 |
| PUT | /admin/api/content/{id} | 编辑内容 |
| DELETE | /admin/api/content/{id} | 删除内容 |
| POST | /admin/api/crawler/start | 启动爬虫任务 |
| GET | /admin/api/crawler/status | 爬虫状态查询 |
| GET | /admin/api/stats | 数据统计（总量、日增量等） |

## 数据模型

已完成，详见:
- projects/film-forest/doc/database-design.md
- projects/film-forest/database/init.sql

## 部署方案

### docker-compose.yml 结构（开发到部署阶段时完善）

```yaml
version: '3.8'
services:
  backend:
    image: film-forest-backend:latest
    build:
      context: ./backend
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql8:3306/film_forest
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=Root2026
    networks:
      - nas-services
    restart: unless-stopped

  frontend:
    image: film-forest-frontend:latest
    build:
      context: ./frontend
      dockerfile: Dockerfile
    ports:
      - "3000:80"
    depends_on:
      - backend
    networks:
      - nas-services
    restart: unless-stopped

networks:
  nas-services:
    external: true
```

### 端口规划

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL | 3306 | 共享数据库（已部署） |
| OpenClaw | 18789 | AI 助理（已部署） |
| film-forest-backend | 8080 | 影视森林后端 |
| film-forest-frontend | 3000 | 影视森林前端 |
