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

## 回滚边界

- Flyway `clean` 已禁用，禁止用删除 schema 的方式回滚。
- MySQL DDL 会隐式提交，V2 不能视为可事务回滚；若生产迁移失败，先停止两端服务，再从迁移前一致性备份恢复到隔离验证通过的新 schema，并按部署切换方案恢复服务。
- 不提供自动 `undo` 或直接删除安全列的脚本；恢复前必须保留失败现场和 `flyway_schema_history`，用于判断迁移停留位置。
