# 部署规范 -- 独立容器化部署

> 加载时机: 部署项目 / 创建 Dockerfile / 编写 docker-compose / 讨论部署架构时

## 核心原则

所有需要部署的项目必须通过**独立 Docker 容器**运行，与 OpenClaw 容器**平级、无依赖关系**。

1. **每个项目一个独立容器**（或一组 docker-compose 服务）
2. **项目容器不依赖 OpenClaw 容器**：OpenClaw 停掉不影响项目运行
3. **共享基础设施**：所有项目共用 NAS 上的 MySQL 容器，不同项目用不同 database
4. **部署配置统一放在** `projects/项目名/deploy/` 中（Dockerfile + docker-compose.yml）
5. **数据持久化**：每个容器的数据卷挂载到 `/volume1/docker/项目名/`
   - **红线**: 绝对不能放在 `/home/Kkwans/` 用户个人目录，这是底线，触碰即死

## 容器架构

```
NAS (绿联 DH4300Plus ARM64)
+-- mysql8 容器 (3306)           # 共享数据库服务
|   +-- database: film_forest    # 影视森林
|   +-- database: xxx            # 后续项目...
+-- openclaw 容器 (18789)        # OpenClaw / J.A.R.V.I.S.
+-- film-forest-backend 容器     # 影视森林后端（SpringBoot）
+-- film-forest-frontend 容器    # 影视森林前端（Nginx）
+-- [后续项目容器...]            # 每个项目独立容器
```

## 新项目部署 Checklist

1. 在 MySQL 中创建独立 database（`CREATE DATABASE xxx`）
2. 编写 Dockerfile（放在 `projects/项目名/deploy/`）
3. 编写 docker-compose.yml（定义端口映射、环境变量、数据卷）
4. 容器连接 MySQL 使用 NAS 内网 IP（`192.168.5.110:3306`）或 Docker bridge 网络
5. 配置 Tailscale 端口转发（如需外网访问）
6. 部署配置记录在对应 task 的 DESIGN.md 中

## Docker 网络方案

推荐使用自定义 bridge 网络连接项目容器和 MySQL：

```yaml
# docker-compose.yml 示例
networks:
  nas-services:
    external: true  # 预先创建: docker network create nas-services

services:
  backend:
    networks:
      - nas-services
    environment:
      - MYSQL_HOST=mysql8  # 通过容器名访问
```

如果 MySQL 容器已在默认 bridge 网络，也可以用宿主机 IP（`192.168.5.110:3306`）直连。

## 端口规划

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL | 3306 | 共享数据库（已部署） |
| OpenClaw | 18789 | J.A.R.V.I.S.（已部署） |
| film-forest-backend | 8080 | 影视森林后端（待部署） |
| film-forest-frontend | 3000 | 影视森林前端（待部署） |

> 新增项目时在此表追加端口分配，避免冲突。
