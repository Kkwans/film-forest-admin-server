# MEMORY.md -- 长期记忆

> 所有永久记忆的精炼入口。日常细节在 memory/YYYY-MM-DD.md 里。

## 当前任务: 影视森林 (film-forest)

- **任务路径**: tasks/2026-04/film-forest-0425/
- **工程路径**: projects/film-forest/
- **定位**: 影视资源聚合网站（类似七味网 pkmp4.xyz，但更好用）
- **两个端**:
  - **用户端**: 面向普通用户的影视资源浏览/搜索/下载网站
  - **管理端**: 管理平台，用于内容管理、爬虫管理、数据维护
- **技术栈**: SpringBoot3 + MySQL8 + 前端（用户端潮流 UI + 管理端待定）
- **数据库**: film_forest 库，9 张表已创建
  - 5 主表: movie / drama / variety / anime / short_drama
  - 3 资源表: resource_online / resource_magnet / resource_cloud
  - 1 剧集子表: episode
  - 1 字典表: content_type（仅参考）
- **关键文档**:
  - 任务规划: tasks/2026-04/film-forest-0425/PLAN.md
  - 七味网调研: tasks/2026-04/film-forest-0425/EXPLORE.md
  - 数据库设计: projects/film-forest/doc/database-design.md
  - 建表 SQL: projects/film-forest/database/init.sql
- **当前状态**: 已部署运行
  - 后端: `/volume1/docker/film-forest/backend/film-forest.jar` → 8080
  - 前端: `/volume1/docker/film-forest/frontend/` → 3000（node server.js）
  - MySQL: docker mysql8 运行中（3306）
- **后端**: SpringBoot3 + MyBatis-Plus3，9 张表实体/Mapper/Service/Controller 全部完成
- **关键文件**: 
  - pom.xml: projects/film-forest/backend/pom.xml
  - 主类: com.filmforest.FilmForestApplication
  - 配置: src/main/resources/application.yml
  - Docker: backend/Dockerfile + deploy/docker-compose.yml
- **部署架构**:
  - **NAS 部署目录**: `/volume1/docker/film-forest/`（Docker volume 路径，**绝对不能放 /home/Kkwans/ 用户个人目录**）
  - **项目源码**: `/root/.openclaw/workspace/projects/film-forest/`
  - **GitHub**: Kkwans/film-forest-server（后端）、Kkwans/film-forest-client（前端）
- **前端**: next build + standalone 模式，NAS 上 node server.js 直接运行（NAS 有 node 18 无 npm）
- **待推进**: 爬虫核心对接七味网 -> Docker 容器化 -> 管理端

## 暂停项目: 开发面试大师 (interview-master)

- **状态**: 暂停，移交给 Hermes Agent（真维斯）
- **任务路径**: tasks/2026-04/interview-master-0422/PLAN.md
- **注意**: GitHub 仓库 https://github.com/Kkwans/interview-master 是旧的废弃项目，主人计划归档或删除，**不要基于此仓库开发**
- **后续**: 等影视森林完成后，再评估是否由贾维斯接手

## NAS 环境

- **设备**: 绿联 DH4300Plus（ARM64）
- **网络**: 内网 192.168.5.110 / Tailscale 100.106.29.60
- **Docker**: 26.1.0 / Compose 1.29.2 / JDK 17 / Maven 3.8.7
- **MySQL**: mysql8 容器，3306 端口，root/Root2026
  - 所有项目共用此 MySQL 实例，不同项目用不同 database
- **mariadbd**: 已停用
- **Tailscale**: 部署在 NAS 本机
- **部署原则**: 每个项目独立容器，与 OpenClaw 平级无依赖

## Agent 分工

- **J.A.R.V.I.S.（贾维斯）**: 主人的私人 AI 助理，当前专注影视森林开发
- **Hermes（真维斯）**: 另一个 Agent（自进化Agent，在另一个独立docker容器部署），负责面试大师项目

## Skills 安装记录 (2026-04-22)

通过 clawhub CLI 安装，详见 skills/ 目录。
注意：docx / pdf / xlsx 已移除（主人不需要办公文档处理类 Skill）。

## 重要教训

- APK 编译: ARM64 容器无法运行 x86_64 build-tools，用 GitHub Actions 解决
- Expo 配置曾破坏 bare RN 项目，04-18 成功构建(fce56d1c)才是正确基准
- 收到任务必须立即执行，不能只回"收到"无下文
- 每次对话有新信息必须实时更新记忆，不得以"稍后更新"推迟
- NAS 文件浏览器需要 UTF-8 BOM 才能正确显示中文（仅 .md 文件）
- 不要用自己运行环境（大象）推断用户环境（QQ Bot）
- 用户要求"完美"就是逐字节审查级别
- 不要自作主张加用户不需要的 Skill（如 docx/pdf/xlsx），要了解用户身份再选
- **底线**: 部署目录必须是 `/volume1/docker/<项目名>/`，绝对不能放 `/home/Kkwans/` 用户个人目录。触碰即死。
- **底线**: 每30分钟 heartbeat 汇报只记该窗口内做的事，不把之前的工作算进来。如实汇报，有事说事，无事说无事。
- **底线**: 重要教训和规范必须同时写入 MEMORY.md + 对应规范文件（AGENTS.md / guides/），不能只写当日记忆。当日记忆容易丢失，长期记忆和规范才是永久的。
