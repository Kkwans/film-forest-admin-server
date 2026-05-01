# PLAN -- 影视森林任务规划

## 项目信息
- 项目名：影视森林（film-forest）
- 启动日期：2026-04-25
- 负责人：J.A.R.V.I.S. (AI助理)

## 目录结构
projects/film-forest/
├── frontend/          # 前端代码
├── backend/          # 后端代码（SpringBoot）
├── database/         # 数据库SQL脚本
├── deploy/           # 部署脚本及配置
├── doc/              # 文档（设计方案、任务规划等）
└── release/          # 产物（可选）

## 数据库设计
- 5张主表：movie/drama/variety/anime/short_drama
- 3张资源表：resource_online/resource_magnet/resource_cloud
- 1张剧集子表：episode
- 1张字典表：content_type（仅参考）
- 脚本：projects/film-forest/database/init.sql

## 任务阶段
| 阶段 | 内容 | 状态 |
|------|------|------|
| 1 | 数据库设计（v2，5主表+资源表+剧集子表） | 完成 |
| 2 | NAS MySQL环境（停mariadbd，启动mysql8，建film_forest库） | 完成 |
| 3 | 建表SQL导入（9张表全部创建） | 完成 |
| 4 | SpringBoot后端脚手架 + 基础CRUD | 待开始 |
| 5 | 前端子系统（管理端 + 用户端） | 待开始 |
| 6 | 爬虫开发（对接外部资源站） | 待开始 |

## 当前状态
数据库设计完成，MySQL已就绪
暂停中，等主人重构工作区规范，完成后继续推进后端开发
