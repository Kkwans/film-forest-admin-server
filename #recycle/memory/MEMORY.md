# 长期记忆索引

> 所有永久记忆的入口。关键信息在此索引，具体内容在对应文件。

## 主人档案 (USER.md)
- 美团校招Java后端开发，绿联DH4300Plus NAS
- 设备：红米K80 Pro + QQ Bot
- 技术：Java/SpringBoot/接口编排/Thrift
- 核心需求：AI工作流、个人项目、技术成长

## 记忆规范 (rules/MEMORY_RULES.md)
- 每对话有新的信息点 -> 实时更新当日记忆
- 永久有效的事项 -> 写入长期记忆文档
- 主人明确要求牢牢记住的 -> 写入长期记忆
- 参考规范：rules/MEMORY_RULES.md

## 工作区规范 (rules/WORKSPACE_RULES.md)
- 正式项目 -> projects/项目名/（frontend/backend/database/deploy/doc/release）
- 任务相关 -> tasks/YYYY-MM/任务名-月日/
- 规范详情：rules/WORKSPACE_RULES.md

## 写作规范 (rules/WRITE_NORM.md)
- 标题行禁止使用 Emoji（NAS文件系统无法渲染）
- 禁用EM DASH (U+2014)和EN DASH (U+2010)
- 文件头使用 `--` 作为ASCII分隔符

## 项目：影视森林 (film-forest)
- 路径：projects/film-forest/
- 类型：影视资源管理系统（类七味网 pkmp4.xyz）
- 技术栈：SpringBoot3 + MySQL8 + 前端（潮流UI）
- 状态：数据库设计完成，MySQL已就绪，等待继续后端开发
- MySQL：film_forest库，9张表（movie/drama/variety/anime/short_drama + episode + resource_online/magnet/cloud）
- 数据库文档：projects/film-forest/doc/database-design.md
- 建表SQL：projects/film-forest/database/init.sql
- 注意：主人正在重构工作区规范，暂停推进

## 项目：开发面试大师 (tasks/2026-04/interview-master-0422/)
- 安卓原生APP + 网页 + NAS后端
- 技术栈：Kotlin/Jetpack Compose + Next.js + SpringBoot3 + MySQL8
- 进度：环境准备中（JDK/Maven已装，MySQL容器安装中）
- 详情：tasks/2026-04/interview-master-0422/PLAN.md

## NAS环境
- IP：192.168.5.110 / 100.106.29.60 (Tailscale)
- 用户：Kkwans（admin组），可sudo免密码
- SSH：sshpass -p 'Ncu8117_' ssh Kkwans@192.168.5.110
- Docker：26.1.0 | DockerCompose：1.29.2 | JDK：OpenJDK 17.0.18 | Maven：3.8.7
- MySQL：mysql8容器，3306端口，root/Root2026
- film_forest库：9张表已创建
- mariadbd已停用（pkill处理）

## 测试项目（tasks/2026-04/test-mini）
- 后端 SpringBoot：8082端口
- 前端 Next.js：3000端口
- 外网访问：100.106.29.60:3000 | 100.106.29.60:8082
