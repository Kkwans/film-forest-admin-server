package com.filmforest.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只允许显式运行在本机临时 MySQL 上的 Phase 0 Flyway 恢复演练。
 *
 * <p>类名以 IT 结尾，因此不会进入默认 Surefire 测试集合。必须同时提供确认短语、
 * 演练模式和隔离数据库连接信息，并通过 {@code -Dtest=FlywayRestoreDrillIT} 显式运行。</p>
 */
class FlywayRestoreDrillIT {

    private static final String CONFIRMATION = "isolated-restore-only";
    private static final Pattern ISOLATED_JDBC_URL = Pattern.compile(
            "^jdbc:mysql://(127\\.0\\.0\\.1|localhost):(\\d{1,5})/"
                    + "(film_forest_phase0_(restore|empty))(?:\\?.*)?$"
    );

    @Test
    void migratesOnlyExplicitIsolatedSchema() throws SQLException {
        DrillTarget target = DrillTarget.fromEnvironment();

        try (Connection connection = DriverManager.getConnection(
                target.jdbcUrl(), target.username(), target.password())) {
            assertPreMigrationState(connection, target.mode());
            UserSnapshot before = target.mode() == DrillMode.RESTORED
                    ? userSnapshot(connection)
                    : null;

            Flyway flyway = Flyway.configure()
                    .dataSource(target.jdbcUrl(), target.username(), target.password())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .validateOnMigrate(true)
                    .cleanDisabled(true)
                    .load();

            flyway.migrate();

            assertPostMigrationState(connection, target.mode(), before);
        }
    }

    private static void assertPreMigrationState(Connection connection, DrillMode mode)
            throws SQLException {
        assertThat(tableCount(connection, "flyway_schema_history")).isZero();
        assertThat(securityColumnCount(connection)).isZero();
        assertThat(applicationTableCount(connection))
                .isEqualTo(mode == DrillMode.RESTORED ? 18 : 0);
    }

    private static void assertPostMigrationState(
            Connection connection,
            DrillMode mode,
            UserSnapshot before
    ) throws SQLException {
        assertThat(applicationTableCount(connection)).isEqualTo(18);
        assertThat(securityColumnCount(connection)).isEqualTo(3);
        assertThat(securityConstraintCount(connection)).isEqualTo(3);
        assertThat(successfulMigrationCount(connection)).isEqualTo(2);
        assertThat(migrationType(connection, "1"))
                .isEqualTo(mode == DrillMode.RESTORED ? "BASELINE" : "SQL");
        assertThat(migrationType(connection, "2")).isEqualTo("SQL");

        if (mode == DrillMode.RESTORED) {
            assertThat(userSnapshot(connection)).isEqualTo(before);
            assertThat(passwordAlgorithmMismatchCount(connection)).isZero();
            assertThat(nonDefaultMustChangePasswordCount(connection)).isZero();
            assertThat(adminRoleCount(connection)).isEqualTo(before.undeletedAdminCount());
        } else {
            assertThat(scalar(connection, "SELECT COUNT(*) FROM `user`")).isZero();
        }
    }

    private static long applicationTableCount(Connection connection) throws SQLException {
        return scalar(connection, """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """);
    }

    private static long tableCount(Connection connection, String tableName) throws SQLException {
        return scalar(connection, """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = '%s'
                """.formatted(tableName));
    }

    private static long securityColumnCount(Connection connection) throws SQLException {
        return scalar(connection, """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'user'
                  AND column_name IN ('role', 'password_algorithm', 'must_change_password')
                """);
    }

    private static long securityConstraintCount(Connection connection) throws SQLException {
        return scalar(connection, """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'user'
                  AND constraint_name IN (
                    'chk_user_role',
                    'chk_user_password_algorithm',
                    'chk_user_must_change_password'
                  )
                """);
    }

    private static long successfulMigrationCount(Connection connection) throws SQLException {
        return scalar(connection, """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE success = 1
                  AND version IN ('1', '2')
                """);
    }

    private static String migrationType(Connection connection, String version) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT type
                     FROM flyway_schema_history
                     WHERE version = '%s' AND success = 1
                     """.formatted(version))) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private static UserSnapshot userSnapshot(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT COUNT(*) AS total_users,
                            SUM(password_hash LIKE '$2a$%'
                                OR password_hash LIKE '$2b$%'
                                OR password_hash LIKE '$2y$%') AS bcrypt_users,
                            SUM(username = 'admin' AND is_deleted = 0) AS undeleted_admins
                     FROM `user`
                     """)) {
            assertThat(resultSet.next()).isTrue();
            return new UserSnapshot(
                    resultSet.getLong("total_users"),
                    resultSet.getLong("bcrypt_users"),
                    resultSet.getLong("undeleted_admins")
            );
        }
    }

    private static long passwordAlgorithmMismatchCount(Connection connection) throws SQLException {
        return scalar(connection, """
                SELECT COUNT(*)
                FROM `user`
                WHERE ((password_hash LIKE '$2a$%'
                        OR password_hash LIKE '$2b$%'
                        OR password_hash LIKE '$2y$%')
                       AND password_algorithm <> 'BCRYPT')
                   OR ((password_hash NOT LIKE '$2a$%'
                        AND password_hash NOT LIKE '$2b$%'
                        AND password_hash NOT LIKE '$2y$%')
                       AND password_algorithm <> 'LEGACY_SHA256')
                """);
    }

    private static long nonDefaultMustChangePasswordCount(Connection connection) throws SQLException {
        return scalar(connection, """
                SELECT COUNT(*)
                FROM `user`
                WHERE must_change_password <> 0
                """);
    }

    private static long adminRoleCount(Connection connection) throws SQLException {
        return scalar(connection, """
                SELECT COUNT(*)
                FROM `user`
                WHERE username = 'admin'
                  AND is_deleted = 0
                  AND role = 'ADMIN'
                """);
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private record UserSnapshot(long totalUsers, long bcryptUsers, long undeletedAdminCount) {
    }

    private record DrillTarget(
            DrillMode mode,
            String jdbcUrl,
            String username,
            String password
    ) {

        private static DrillTarget fromEnvironment() {
            assertThat(requiredEnvironment("FILM_FOREST_DRILL_CONFIRM"))
                    .as("必须显式确认仅操作隔离恢复目标")
                    .isEqualTo(CONFIRMATION);

            DrillMode mode = DrillMode.valueOf(
                    requiredEnvironment("FILM_FOREST_DRILL_MODE").toUpperCase(Locale.ROOT)
            );
            String jdbcUrl = requiredEnvironment("FILM_FOREST_DRILL_DB_URL");
            Matcher matcher = ISOLATED_JDBC_URL.matcher(jdbcUrl);

            assertThat(matcher.matches())
                    .as("JDBC URL 必须指向本机 film_forest_phase0_restore/empty 隔离 schema")
                    .isTrue();
            int port = Integer.parseInt(matcher.group(2));
            assertThat(port).isBetween(1, 65535).isNotEqualTo(3306);
            assertThat(matcher.group(3)).isEqualTo(mode.schemaName());

            return new DrillTarget(
                    mode,
                    jdbcUrl,
                    requiredEnvironment("FILM_FOREST_DRILL_DB_USERNAME"),
                    requiredEnvironment("FILM_FOREST_DRILL_DB_PASSWORD")
            );
        }

        private static String requiredEnvironment(String name) {
            String value = System.getenv(name);
            assertThat(value).as("缺少环境变量 %s", name).isNotBlank();
            return value;
        }
    }

    private enum DrillMode {
        RESTORED("film_forest_phase0_restore"),
        EMPTY("film_forest_phase0_empty");

        private final String schemaName;

        DrillMode(String schemaName) {
            this.schemaName = schemaName;
        }

        private String schemaName() {
            return schemaName;
        }
    }
}
