# PLAN -- 影视森林项目工程规划

## 项目概述

项目名：影视森林（film-forest）
定位：影视资源聚合网站
功能：聚合七味网等资源站的影视资源（电影/电视剧/综艺/动漫/短剧），提供资源浏览、搜索、下载链接

包含两个端：
- **用户端**: 面向用户的影视资源浏览/搜索/下载网站（UI 潮流方向）
- **管理端**: 管理平台（内容管理、爬虫任务管理、数据维护）

> 详细任务规划和执行记录在 tasks/2026-04/film-forest-0425/

## 工程目录

```
projects/film-forest/
+-- frontend/      # 前端代码（用户端 + 管理端）
+-- backend/       # 后端代码（SpringBoot3）
+-- database/      # 数据库 SQL 脚本
+-- deploy/        # 部署配置（Dockerfile / docker-compose.yml）
+-- doc/           # 项目文档（本文件、数据库设计等）
+-- release/       # 构建产物
```

## 技术栈

| 层级 | 选型 |
|------|------|
| 后端 | SpringBoot 3 + MyBatis-Plus + MySQL 8 |
| 用户端前端 | 待定（潮流 UI 方向） |
| 管理端前端 | 待定 |
| 部署 | Docker 独立容器（与 OpenClaw 平级） |

## 数据库

已设计 5 张主表 + 3 张资源表 + 1 张剧集子表，共 9 张表。
- 设计文档: projects/film-forest/doc/database-design.md
- 建表脚本: projects/film-forest/database/init.sql
- MySQL 库: film_forest（已创建）

## 部署方式

独立 Docker 容器部署在 NAS 上：
- film-forest-backend 容器（SpringBoot，端口 8080）
- film-forest-frontend 容器（Nginx，端口 3000）
- 共用 NAS mysql8 容器（film_forest 数据库）
- 通过 Tailscale 可外网访问
