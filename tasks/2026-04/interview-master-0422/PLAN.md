# PLAN -- 开发面试大师 (interview-master)

## 任务信息
- 任务名: 开发面试大师
- 启动日期: 2026-04-22
- 关联项目: 暂无（工程代码尚未创建）
- 状态: **暂停 -- 移交给 Hermes Agent（真维斯）**

## 目标

开发一个面试备战工具，包含安卓原生 APP + 网页版 + NAS 后端服务。
集成 AI 能力（模拟面试、简历评估、题目生成）。

## 技术栈

| 层级 | 技术选型 | 理由 |
|------|----------|------|
| APP 安卓 | Jetpack Compose + Material 3 | 现代原生 UI |
| APP 架构 | MVVM + Hilt + Coroutines | 主流 Android 架构 |
| Web 前端 | Next.js (React) + TailwindCSS | 先进、SEO 友好 |
| 后端框架 | SpringBoot 3 | 熟悉的 Java 技术栈 |
| AI 能力 | SpringAI + DeepSeek/Claude API | 面试题生成、答案优化 |
| 数据库 | MySQL 8 | NAS 已有 |
| ORM | MyBatis-Plus | 工作中常用 |
| 部署 | Docker + Docker Compose | NAS 已有 Docker |

## 当前状态

**暂停中。** 主人决定：
1. 贾维斯（J.A.R.V.I.S.）当前专注于**影视森林**项目开发
2. 面试大师项目移交给 **Hermes Agent（真维斯）**
3. 影视森林完成后，再评估是否由贾维斯接手

## 重要提醒

- GitHub 仓库 `https://github.com/Kkwans/interview-master` 是**旧的废弃项目**
- 主人计划归档或删除该仓库
- **不要基于该仓库开发**，后续需全新开始

## 核心功能优先级（供后续参考）

| 优先级 | 功能 | 说明 |
|--------|------|------|
| P0 | 用户注册/登录 | 最基础 |
| P0 | 面试题库浏览 | 核心内容 |
| P0 | AI 模拟面试 | 最大亮点 |
| P1 | 简历评估 | AI 辅助 |
| P1 | 收藏/错题本 | 学习闭环 |
| P2 | 排行榜 | 社交激励 |
| P2 | 每日推送 | 留存 |
