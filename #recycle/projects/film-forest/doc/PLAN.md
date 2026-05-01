# PLAN -- 影视森林项目规划

## 一、项目概述

项目名：影视森林（film-forest）
类型：影视资源管理系统（电影/电视剧/综艺/动漫/短剧）
类比：类似七味网，功能更完善

## 二、技术栈

| 层级 | 技术选型 |
|------|----------|
| 后端 | SpringBoot 3 + MyBatis-Plus + MySQL 8 |
| 前端 | 待选（潮流UI方向） |
| 数据库 | MySQL 8 |
| 部署 | Docker + Docker Compose |

## 三、数据库设计

已设计5张主表 + 3张资源表 + 1张剧集子表
详见：projects/film-forest/doc/database-design.md

## 四、任务进度

- [x] 数据库设计（ER图 + 建表SQL）v2版本完成
- [x] NAS MySQL环境准备（mariadbd停用，mysql8启动，film_forest库创建）
- [x] 建表SQL导入（9张表全部创建成功）
- [x] 七味网(pkmp4.xyz)调研完成
- [ ] SpringBoot后端脚手架 + 基础CRUD API
- [ ] 前端子系统（管理端 + 用户端）
- [ ] 爬虫开发（对接外部资源站）

## 五、当前状态

数据库设计完成，MySQL已就绪
暂停中，等主人重构工作区规范，完成后继续推进后端开发
