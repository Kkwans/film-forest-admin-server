# 影视森林后端 (film-forest-server)

影视森林项目的后端 API 服务，基于 **SpringBoot 3 + MyBatis-Plus 3** 构建。

## 技术栈

| 技术 | 版本 |
|------|------|
| Java | 17 |
| SpringBoot | 3.x |
| MyBatis-Plus | 3.5.x |
| MySQL | 8.x |
| Druid | 连接池 |
| Lombok | 简化代码 |

## 项目结构

```
src/main/java/com/filmforest/
├── FilmForestApplication.java     # 启动类
├── content/                       # 影视内容模块（电影/剧集/综艺/动漫/短剧）
│   ├── entity/                    # 实体类
│   ├── mapper/                    # Mapper 接口
│   ├── service/                   # 业务层
│   └── controller/                # REST API
├── resource/                      # 资源模块（磁力/网盘/在线）
├── crawler/                       # 爬虫模块（预留）
├── admin/                         # 管理端 API（预留）
└── common/                        # 公共组件
    ├── dto/Result.java            # 统一响应封装
    └── exception/                 # 全局异常处理
```

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /health | 健康检查 |
| GET | /api/movies | 电影列表 |
| POST | /api/movies | 新增电影 |
| GET | /api/movies/{id} | 电影详情 |

详见 [任务文档](../tasks/2026-04/film-forest-0425/PLAN.md)

## 本地运行

```bash
# 构建
mvn clean package -DskipTests

# 运行
java -jar target/film-forest-backend-0.0.1-SNAPSHOT.jar

# 指定端口
java -jar target/film-forest-backend-0.0.1-SNAPSHOT.jar --server.port=8888
```

## 部署

Docker 部署（已配置 Dockerfile + docker-compose.yml）：

```bash
cd deploy
docker-compose up -d
```

访问 `http://localhost:8888/health` 验证。

## 数据库

- 数据库名: `film_forest`
- 详见 [数据库设计文档](../doc/database-design.md)
- 建表脚本: [`../database/init.sql`](../database/init.sql)

## License

MIT