# Flyway baseline 说明

## 所有权与来源

- `admin-server` 是 `film_forest` schema 的唯一 Flyway 迁移所有者。
- V1 于 2026-08-08 从 NAS `mysql8` 容器中的实际 `film_forest` schema 只读提取。
- 提取时共有 18 张基础表，且不存在 `flyway_schema_history`。
- V1 保留表、列、主键、唯一键、普通索引、外键、字符集和排序规则；有意省略生产自增计数与已乱码的说明注释。

## 接管语义

- 对当前非空生产 schema：使用 `baseline-on-migrate=true` 和 `baseline-version=1`，首次迁移只登记 V1，然后执行 V2。
- 对全新空 schema：按 V1、V2 顺序完整创建。
- V2 只向 `user` 表添加 `role`、`password_algorithm`、`must_change_password` 三列与取值约束。
- V2 将现有 `admin` 用户回填为 `ADMIN`，其余账号保持 `USER`；根据哈希格式识别现有 BCrypt，其余保持 `LEGACY_SHA256`。

## 执行闸门

- Phase 0 不对生产数据库执行迁移。
- 首次生产迁移前必须完成一致性备份和隔离恢复演练，并再次确认仍只有一个未删除的 `admin` 用户。
- 首次生产迁移前必须确认 `user` 表尚不存在 `role`、`password_algorithm`、`must_change_password` 三列及 V2 中的三个同名约束。
- 若实际 schema 与 V1 结构漂移、已有同名列/约束、或共享 MySQL 使用方受影响，停止迁移并升级确认。

## 隔离演练验证器

- `FlywayRestoreDrillIT` 不进入默认 Surefire 测试集合，只能通过 `-Dtest=FlywayRestoreDrillIT` 显式运行。
- 验证器只接受 `127.0.0.1`/`localhost`、非 3306 端口，以及 `film_forest_phase0_restore` 或 `film_forest_phase0_empty` schema。
- 运行前必须设置 `FILM_FOREST_DRILL_CONFIRM=isolated-restore-only`，连接用户名和密码只通过环境变量注入。
- `RESTORED` 模式要求迁移前已有 18 张业务表、没有 Flyway history 和安全字段；迁移后验证 V1 baseline、V2、三列、三个约束、用户总数、哈希分类和管理员回填。
- `EMPTY` 模式要求迁移前为空；迁移后验证 V1、V2、18 张业务表和空用户表。
- 实际演练必须按两个模式分别运行一次；编译成功不能代替真实隔离 MySQL 验证。

## 首次发布顺序

1. 在 V2 完成前保持 client-server 与 admin-server 停止；两端新代码都按新增安全字段映射 `user` 表，不能先于迁移发布。
2. 通过备份与隔离恢复演练后，仅为 admin-server 启用 Flyway，并先启动 admin-server。
3. 确认 `flyway_schema_history` 中 V2 成功、三列及三个约束存在、且唯一未删除的 `admin` 已回填为 `ADMIN`。
4. 管理端健康检查与登录授权验证通过后，再启动 client-server；任一步失败都不得继续启动客户端后端。

## 回滚边界

- Flyway `clean` 已禁用，禁止用删除 schema 的方式回滚。
- MySQL DDL 会隐式提交，V2 不能视为可事务回滚；若生产迁移失败，先停止两端服务，再从迁移前一致性备份恢复到隔离验证通过的新 schema，并按部署切换方案恢复服务。
- 不提供自动 `undo` 或直接删除安全列的脚本；恢复前必须保留失败现场和 `flyway_schema_history`，用于判断迁移停留位置。
