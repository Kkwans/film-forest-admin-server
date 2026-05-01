# 项目规范 -- projects 目录结构

> 加载时机: 新建项目 / 初始化工程目录 / 查看项目结构时

## projects 是什么

`projects/` 存放任务的**工程产物** -- 代码、配置、脚本、SQL 等可执行/可部署的东西。

- 每个项目一个子目录: `projects/项目名/`
- **所有 project 必须关联一个 task**（task 是"大脑"，project 是"身体"）
- project 目录只放工程文件，设计文档/调研/日志放在对应 task 目录中

## 标准目录结构

```
projects/项目名/
+-- README.md              # 项目简介（一句话说清是什么、怎么跑）
+-- doc/                   # 项目文档（数据库设计、API 文档等）
|   +-- database-design.md
|   +-- PLAN.md            # 项目级规划（可选，大型项目用）
+-- database/              # 数据库文件
|   +-- init.sql           # 建库建表脚本
|   +-- migration/         # 增量变更脚本（按日期命名）
|       +-- V001_xxx.sql
+-- backend/               # 后端代码
|   +-- src/
|   +-- pom.xml / build.gradle
|   +-- Dockerfile
+-- frontend/              # 前端代码
|   +-- src/
|   +-- package.json
|   +-- Dockerfile
+-- deploy/                # 部署配置
|   +-- docker-compose.yml
|   +-- nginx.conf
|   +-- .env.example       # 环境变量模板（不含真实密码）
+-- scripts/               # 工具脚本（爬虫、数据处理、运维）
+-- assets/                # 静态资源（图片、图标等）
```

### 目录说明

| 目录 | 必需 | 说明 |
|------|------|------|
| doc/ | 推荐 | 项目文档，数据库设计、API 文档 |
| database/ | 有数据库时 | SQL 脚本，init.sql 为初始化，migration/ 为增量 |
| backend/ | 有后端时 | 后端工程代码，含 Dockerfile |
| frontend/ | 有前端时 | 前端工程代码，含 Dockerfile |
| deploy/ | 需要部署时 | docker-compose.yml + 相关部署配置 |
| scripts/ | 按需 | 工具脚本（爬虫、批处理、数据迁移等） |
| assets/ | 按需 | 静态资源 |

## README.md 最小模板

```markdown
# 项目名

> 一句话描述

- **关联任务**: tasks/YYYY-MM/xxx/
- **技术栈**: [后端] / [前端] / [数据库]
- **状态**: 开发中 / 已上线 / 已归档

## 快速启动

[怎么跑起来，3 步以内]

## 目录结构

[简要说明各目录用途]
```

## 数据库文件规范

- `init.sql`: 完整的建库建表脚本（从零可执行）
- `migration/V001_xxx.sql`: 增量变更，按版本号递增
- SQL 文件**不加 BOM**（MySQL 客户端兼容性）
- SQL 文件开头注明: `-- 项目名 / 用途 / 日期`

## 与 task 的分工

| 内容 | 放在 task | 放在 project |
|------|-----------|--------------|
| 规划/设计/调研 | PLAN.md / DESIGN.md / EXPLORE.md | - |
| 执行日志 | LOG.md | - |
| 复盘总结 | REVIEW.md | - |
| 工程代码 | - | backend/ / frontend/ |
| 数据库脚本 | - | database/ |
| 部署配置 | - | deploy/ |
| 项目文档 | - | doc/ |
| 需求规格 | SPEC.md 或 task 目录 | - |

**原则: task 管"怎么做"，project 管"做出来的东西"。**

## 新建项目 Checklist

1. 确认已有关联 task（`tasks/YYYY-MM/任务名-MMDD/`）
2. 创建 `projects/项目名/` 目录
3. 创建 `README.md`（最小模板）
4. 按需创建子目录（doc/database/backend/frontend/deploy/scripts）
5. 在 task 的 PLAN.md 中填写 `关联项目: projects/项目名/`
6. 在 MEMORY.md 中添加项目索引
