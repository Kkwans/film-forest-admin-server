# 当日记忆 -- 2026-04-25

## 影视森林项目正式启动

### 项目信息
- 名称：影视森林（film-forest）
- 路径：projects/film-forest/
- 类型：影视资源管理系统（类似七味网 pkmp4.xyz）
- 技术栈：SpringBoot3 + MySQL8 + 前端（潮流UI方向待定）

### 数据库设计完成 v2
- 5张主表：movie / drama / variety / anime / short_drama
- 剧集子表：episode（支持剧集/综艺/动漫的多季多期）
- 资源表：resource_magnet + resource_cloud + resource_online（在线播放源）
- 字典表：content_type（仅参考，无外键约束）
- 新增字段：is_special_sub（特效字幕标记，提取"特效"关键词）
- 详情：projects/film-forest/doc/database-design.md

### NAS MySQL环境就绪
- mariadbd已停用（pkill mariadbd）
- mysql8容器已启动，3306端口正常运行
- film_forest数据库已创建，9张表全部创建成功

### 七味网调研发现
- 内容分类URL：/vt/1=电影 /vt/2=剧集 /vt/3=综艺 /vt/4=动漫
- 播放页格式：/py/{id}-{season}-{episode}.html
- 在线播放源：天堂/非凡/如意/量子/光速/红牛等多个第三方源
- 磁力标记：中字1080P / 特效字幕 等
- 综艺有多季多期多版本（如四个愿望）

### 目录结构（已创建）
```
projects/film-forest/
├── frontend/      # 前端代码
├── backend/      # 后端代码（SpringBoot）
├── database/     # 数据库SQL脚本
├── deploy/       # 部署脚本及配置
├── doc/          # 文档（数据库设计、任务规划等）
└── release/      # 产物（可选）
```

### 任务进度
- [x] 数据库设计（v2，5主表+资源表+剧集子表）
- [x] NAS MySQL环境准备（mariadbd停用，mysql8启动，film_forest库创建）
- [x] 建表SQL导入（9张表全部创建成功）
- [x] 七味网调研完成
- [ ] SpringBoot后端脚手架 + 基础CRUD API
- [ ] 前端子系统（管理端+用户端）
- [ ] 爬虫开发

### 主人质量标准
- 质量永远第一位，不求速度
- 数据库设计先于爬虫，先调研再设计
- 特效字幕字段独立于中字字段

### 主人正在重构工作区规范
- 目录结构、记忆系统、规则规范将全部重建
- 等待主人完成重构后通知，再按新规范继续

### 写作规范（rules/WRITE_NORM.md）
- 禁用全角符号（EM DASH、书名号、引号、括号等）
- 禁用非ASCII符号作为装饰
- 统一使用ASCII半角符号

---

## 教训
- 收到任务必须立即执行，不能只回"收到"无下文
- 每对话有新增信息必须更新当日记忆
- 重大任务禁止只回复"收到"后无下文
