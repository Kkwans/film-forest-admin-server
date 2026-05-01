# TOOLS.md -- 环境配置笔记

Skills 定义工具怎么用，这个文件记录你的具体环境信息。

## NAS 环境

- **型号**: 绿联 DH4300Plus（ARM64）
- **内网 IP**: 192.168.5.110
- **Tailscale IP**: 100.106.29.60
- **用户**: Kkwans（admin 组，可 sudo 免密码）
- **SSH**: `sshpass -p 'Ncu8117_' ssh Kkwans@192.168.5.110`

### 已安装组件

| 组件 | 版本 | 备注 |
|------|------|------|
| Docker | 26.1.0 | |
| Docker Compose | 1.29.2 | |
| JDK | OpenJDK 17.0.18 | ARM |
| Maven | 3.8.7 | |
| MySQL 8 | Docker 容器 | 端口 3306 |

### MySQL（共享实例）

- **容器名**: mysql8
- **端口**: 3306
- **root 密码**: Root2026
- **数据卷**: /volume1/docker/mysql
- **已有数据库**: film_forest（9 张表）
- **原则**: 所有项目共用此 MySQL 实例，不同项目用不同 database

### Docker 端口规划

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL | 3306 | 共享数据库 |
| OpenClaw | 18789 | J.A.R.V.I.S. |
| film-forest-backend | 8080 | 影视森林后端 ✅ 已部署 |
| film-forest-frontend | 3000 | 影视森林前端 ✅ 已部署 |

### NAS 运行时环境

- **nodejs**: v18.20.4（已安装）
- **npm**: **无**（未安装，前端用 standalone 模式 node server.js 直接运行）
- **部署目录**: `/volume1/docker/film-forest/`（**红线：不能放 /home/Kkwans/ 用户个人目录**）

### 网络

- **Tailscale**: 部署在 NAS 本机（非容器内）
- **外网访问**: 100.106.29.60:端口号
- **Docker 网络**: 项目容器使用宿主机 IP（192.168.5.110:3306）连接 MySQL，或创建自定义 bridge 网络

## 交互方式

- **平台**: QQ Bot
- **部署**: Docker 容器运行在 NAS 上
- **设备**: 红米 K80 Pro（HyperOS 3.0.6.0）

## Android 编译

- ARM64 容器无法直接编译（aapt2 是 x86_64）
- 解决方案: GitHub Actions（ubuntu-latest x86_64 编译 ARM64 APK）

## GitHub

- **账号**: Kkwans
- **film-forest 仓库**: Kkwans/film-forest-server（后端）、Kkwans/film-forest-client（前端）
- **注意**: interview-master 仓库是旧的废弃项目，主人计划归档/删除

---

_环境有变化就更新。这是你的速查手册。_
