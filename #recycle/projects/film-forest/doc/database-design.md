# DB-DESIGN -- 影视森林数据库设计文档

> 基于 https://www.pkmp4.xyz/ 七味网调研后重新设计
> 修订时间：2026-04-25

## 一、设计原则

1. 按类型分表：电影/剧集/综艺/动漫/短剧各一张主表，字段按需定制
2. 字典表只做参考：content_type 不做外键约束，仅供下拉选择
3. 剧集独立子表：仅剧集类型需要 episode 子表
4. 在线播放资源：第三方网站嵌入播放源（新增）
5. 资源统一管理：磁力表、网盘表、在线源表统一通过 content_id + episode_id 关联

## 二、字典表（仅供参考，不做外键）

content_type -- 内容类型字典
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT PK | 主键 |
| name | VARCHAR(20) | 类型名称 |
| code | VARCHAR(20) | 类型代码 |
| sort | INT | 排序 |

code: movie=电影, drama=剧集, variety=综艺, anime=动漫, short=短剧

## 三、主表设计

### movie -- 电影表
id / title / alias(JSON) / poster_url / year / director(JSON) / writer(JSON) / actor(JSON) / genre(JSON) / region(JSON) / language(JSON) / release_date / duration / storyline / score_douban / score_imdb / series_name / series_order / status / is_deleted / created_at / updated_at

### drama -- 电视剧表
id / title / alias(JSON) / poster_url / year / director(JSON) / writer(JSON) / actor(JSON) / genre(JSON) / region(JSON) / language(JSON) / release_date / total_episode / storyline / score_douban / score_imdb / status / is_deleted / created_at / updated_at

### variety -- 综艺表
id / title / alias(JSON) / poster_url / year / director(JSON) / actor(JSON) / genre(JSON) / region(JSON) / language(JSON) / release_date / total_episode / storyline / score_douban / status / is_deleted / created_at / updated_at

### anime -- 动漫表
id / title / alias(JSON) / poster_url / year / director(JSON) / writer(JSON) / actor(JSON) / genre(JSON) / region(JSON) / language(JSON) / release_date / total_episode / storyline / score_douban / status / is_deleted / created_at / updated_at

### short_drama -- 短剧表
id / title / alias(JSON) / poster_url / year / director(JSON) / actor(JSON) / genre(JSON) / region(JSON) / language(JSON) / release_date / total_episode / duration / storyline / status / is_deleted / created_at / updated_at

## 四、子表设计

### episode -- 剧集子表（drama/anime/variety/short）
id / content_type / content_id / season / episode_number / title / poster_url / created_at

### resource_online -- 在线播放资源表（新增）
id / content_type / content_id / episode_id / source_name / source_url / sort / created_at

### resource_magnet -- 磁力链接表
id / content_type / content_id / episode_id / title / magnet_url / resolution / has_subtitle / is_special_sub / sort / created_at

### resource_cloud -- 网盘链接表
id / content_type / content_id / episode_id / disk_type / title / url / password / sort / created_at

## 五、任务进度

- [x] 数据库设计（ER图 + 建表SQL）v2版本完成
- [x] NAS MySQL环境准备（mariadbd停用，mysql8启动，film_forest库创建）
- [x] 建表SQL导入（9张表全部创建成功）
- [x] 七味网(pkmp4.xyz)调研完成
- [ ] SpringBoot后端脚手架 + 基础CRUD API
- [ ] 前端子系统（管理端 + 用户端）
- [ ] 爬虫开发（对接外部资源站）
