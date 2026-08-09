package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Phase8DataLifecycleScriptContractTest {

    @Test
    void scriptRequiresVerifiedArchiveAndKeepsPreservedTablesOutsideDeleteScope() throws IOException {
        String script = Files.readString(Path.of("scripts/phase8-data-lifecycle.sh"));

        assertThat(script).contains(
                "RESTORE_VERIFIED",
                "RESTORE_SHA256SUMS",
                "clear-film-forest-phase8-archived-data",
                "restore-film-forest-phase8-isolated-copy",
                "assert_required_tables",
                "restore-all-table-counts.tsv",
                "all-table-counts.tsv\" \"$archive_dir/restore-all-table-counts.tsv",
                "status IN ('queued', 'running', 'cancel_requested')",
                "SELECT COUNT(*) FROM crawler_schedule WHERE enabled = 1",
                "diff -u \"$archive_dir/clear-scope-counts.tsv\" \"$runtime_counts\"",
                "diff -u \"$archive_dir/all-table-counts.tsv\" \"$runtime_all_counts\"",
                "post-clear-all-table-counts.tsv",
                "START TRANSACTION;",
                "COMMIT;");
        assertThat(script).doesNotContain(
                "TRUNCATE ", "DROP TABLE", "FOREIGN_KEY_CHECKS=0",
                "docker run", "docker rm", "docker compose");

        String clearScope = between(script, "readonly -a CLEAR_SCOPE_TABLES=(", ")");
        assertThat(clearScope).contains(
                "movie", "drama", "variety", "anime", "short_drama",
                "content_poster_match", "content_tag", "user_movie_list_item",
                "resource_online", "resource_magnet", "resource_cloud",
                "crawler_schedule", "crawler_task_log", "crawler_source_item",
                "crawler_content_identity");
        assertThat(clearScope).doesNotContain(
                "system_setting", "resource_source", "user_poster_setting", "user_movie_list\n");

        String preserved = between(script, "readonly -a PRESERVED_TABLES=(", ")");
        assertThat(preserved).contains(
                "user\n", "user_movie_list", "content_type", "system_setting", "tag\n",
                "resource_source", "crawler_source_adapter", "user_poster_setting");
    }

    private static String between(String value, String start, String end) {
        int from = value.indexOf(start);
        assertThat(from).isGreaterThanOrEqualTo(0);
        int to = value.indexOf(end, from + start.length());
        assertThat(to).isGreaterThan(from);
        return value.substring(from + start.length(), to);
    }
}
