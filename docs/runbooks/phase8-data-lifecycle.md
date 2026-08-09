# Phase 8 数据归档、恢复与清空运行手册

本流程只服务于 Phase 8 的一次性“完整归档后从空业务表重新爬取”。脚本不会自动创建、删除 Docker 容器，也不会删除归档包。生产清空必须同时满足：归档文件校验通过、隔离恢复行数一致、归档后数据没有变化、无活动爬虫 Job、无启用计划、显式确认短语正确。

## 清空与保留边界

清空范围是五类内容、内容题材关系、海报匹配与补全 Job、用户片单条目、三类资源、旧剧集备份、爬虫计划/题材、任务日志和来源条目。保留用户、片单定义、内容类型定义、系统设置、标准题材及别名、来源与适配器定义、每用户海报偏好和加密凭据。

实际表名以 `scripts/phase8-data-lifecycle.sh` 的只读常量为唯一执行清单。任何范围变化都必须先修改脚本、测试和本手册并重新确认，不能在部署窗口临时追加 SQL。

## 0. 部署窗口顺序

生产库当前结构可能早于本阶段迁移。进入窗口后必须先按部署流程创建一份**迁移前回滚备份**并记录旧镜像、数据库版本和恢复命令；随后停止四个应用容器的业务写入、执行并验证本阶段 Flyway 迁移。只有迁移成功且脚本要求的全部表都存在后，才执行下述 Phase 8 业务数据归档。

迁移前回滚备份与下述 Phase 8 归档用途不同：前者用于把旧镜像和旧结构整体回滚；后者用于验证迁移后的完整数据可恢复，并作为清空业务数据的唯一授权依据。两个归档都必须保留。

## 1. 完整逻辑归档

在所有计划保持禁用且确认没有运行中 Job 后，指定新的归档目录和生产 MySQL 容器：

```bash
export FILM_FOREST_MYSQL_CONTAINER='<production-mysql-container>'
export FILM_FOREST_ARCHIVE_DIR='/volume2/Project/film-forest/deploy/archives/phase8/<timestamp>'
scripts/phase8-data-lifecycle.sh backup
scripts/phase8-data-lifecycle.sh verify
```

归档产物包括压缩 SQL、全表精确行数、清空范围精确行数、保留范围精确行数、Flyway 版本等元信息、SHA-256 和 `READY` 标记。密码只从 MySQL 容器内部环境变量读取，不写入命令行、归档或日志。归档命令失败后不得复用残留目录；保留现场供排查，并使用新的时间戳目录重试。

## 2. 隔离恢复演练

先由运维人员创建一次性 MySQL 8 容器，容器名必须以 `film-forest-phase8-restore-` 开头，且不能是生产 MySQL 容器。脚本只会在该容器内重建固定 schema `film_forest_phase8_restore`。

```bash
export FILM_FOREST_DRILL_MYSQL_CONTAINER='film-forest-phase8-restore-<timestamp>'
export FILM_FOREST_DRILL_CONFIRM='restore-film-forest-phase8-isolated-copy'
scripts/phase8-data-lifecycle.sh restore-drill
```

只有恢复库的**全部表**、清空范围和保留范围逐表行数都与归档一致，且恢复报告自身 SHA-256 校验通过时，脚本才写入 `RESTORE_VERIFIED`。演练容器的创建和清理由部署记录单独记载；脚本不会自动清理容器。

## 3. 部署窗口清空

清空前再次停止业务写入并确认归档目录没有变更：

```bash
export FILM_FOREST_CLEAR_CONFIRM='clear-film-forest-phase8-archived-data'
scripts/phase8-data-lifecycle.sh clear
```

脚本在一个事务中使用显式 `DELETE` 清空固定表，不执行 `TRUNCATE`、`DROP` 或禁用外键。执行前要求**全库逐表行数**与归档时完全一致；完成后核对清空范围全部为零、所有非目标表逐表未变化，并写入 `CLEARED`。

## 回滚

清空前失败：不继续部署，保留原镜像和数据库。

清空后或新版本启动失败：停止四个影视森林应用容器，在隔离库再次校验归档，按发布记录恢复归档 SQL 和上一发布镜像。镜像与数据库必须作为同一个回滚点，不允许只回滚其中一项。
