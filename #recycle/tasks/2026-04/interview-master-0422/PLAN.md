# PLAN -- 开发面试大师项目规划

## 一、项目概述

产品名：开发面试大师
类型：安卓原生APP + 网页版 + NAS后端服务

架构：
- Android APP (Kotlin/Jetpack Compose) + Web (Next.js/React)
- Java后端 (SpringBoot 3 + MyBatis-Plus + MySQL 8)
- NAS ARM Docker 容器化部署

## 二、技术栈

| 层级 | 技术选型 | 理由 |
|------|----------|------|
| APP安卓 | Jetpack Compose + Material 3 | 现代原生UI |
| APP架构 | MVVM + Hilt + Coroutines | 主流Android架构 |
| Web前端 | Next.js (React) + TailwindCSS | 先进、SEO友好 |
| 后端框架 | SpringBoot 3 + SpringCloud Alibaba | 熟悉的Java技术栈 |
| AI能力 | SpringAI + DeepSeek/Claude API | 面试题生成、答案优化 |
| 数据库 | MySQL 8 | NAS上跑，存储空间大 |
| ORM | MyBatis-Plus | 工作熟悉 |
| 部署 | Docker + Docker Compose | NAS已有Docker |

## 三、环境准备清单

### NAS环境准备
- [x] 安装 OpenJDK 17 (ARM版本)
- [x] 安装 Maven (ARM版本)
- [x] 安装 MySQL 8 (Docker容器)
- [ ] 安装 Android SDK CLI (Linux aarch64)
- [ ] 安装 Docker Compose

### 宿主机准备
- [ ] 在电脑上安装 Android Studio
- [ ] 申请 DeepSeek API Key
- [ ] 申请 Claude API Key

### 项目脚手架
- [ ] 创建 SpringBoot 后端项目
- [ ] 创建 Android 项目
- [ ] 创建 Next.js Web 项目
- [ ] 编写 Docker Compose 部署文件

## 四、核心功能优先级

| 优先级 | 功能 | 说明 |
|--------|------|------|
| P0 | 用户注册/登录 | 最基础 |
| P0 | 面试题库浏览 | 核心内容 |
| P0 | AI模拟面试 | 最大亮点 |
| P1 | 简历评估 | AI辅助 |
| P1 | 收藏/错题本 | 学习闭环 |
| P2 | 排行榜 | 社交激励 |
| P2 | 每日推送 | 留存 |

## 五、当前状态

环境准备中（JDK/Maven已装，MySQL容器安装中）
暂停中，等主人重构工作区规范
