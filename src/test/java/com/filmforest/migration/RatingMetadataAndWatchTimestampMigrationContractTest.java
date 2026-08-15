package com.filmforest.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RatingMetadataAndWatchTimestampMigrationContractTest {

    @Test
    void migrationAddsNullableRatingMetadataAndOnlyBackfillsWatchedItems() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V20__add_rating_metadata_and_watch_timestamps.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "ALTER TABLE `movie`",
                "`score_douban_count` int unsigned DEFAULT NULL",
                "`score_imdb_count` int unsigned DEFAULT NULL",
                "`score_rt_critic_count` int unsigned DEFAULT NULL",
                "`score_rt_audience_count` int unsigned DEFAULT NULL",
                "ALTER TABLE `drama`",
                "ALTER TABLE `drama`\n  ADD COLUMN `score_douban_count` int unsigned DEFAULT NULL AFTER `score_douban`,\n  ADD COLUMN `score_imdb_count` int unsigned DEFAULT NULL AFTER `score_imdb`",
                "ALTER TABLE `variety`",
                "ALTER TABLE `variety`\n  ADD COLUMN `score_douban_count` int unsigned DEFAULT NULL AFTER `score_douban`,\n  ADD COLUMN `score_imdb_count` int unsigned DEFAULT NULL AFTER `score_imdb`",
                "ALTER TABLE `anime`",
                "ALTER TABLE `anime`\n  ADD COLUMN `score_douban_count` int unsigned DEFAULT NULL AFTER `score_douban`,\n  ADD COLUMN `score_imdb_count` int unsigned DEFAULT NULL AFTER `score_imdb`",
                "ALTER TABLE `short_drama`",
                "ADD COLUMN `writer` json DEFAULT NULL",
                "ADD COLUMN `score_douban_count` int unsigned DEFAULT NULL AFTER `score_douban`,\n  ADD COLUMN `score_imdb_count` int unsigned DEFAULT NULL AFTER `score_imdb`",
                "ALTER TABLE `content_poster_match`",
                "`tmdb_score` decimal(3,1) DEFAULT NULL",
                "`tmdb_vote_count` int unsigned DEFAULT NULL",
                "KEY `idx_content_poster_tmdb_score` (`tmdb_score`, `tmdb_vote_count`)",
                "ALTER TABLE `user_movie_list_item`",
                "`watched_at` datetime DEFAULT NULL",
                "KEY `idx_item_list_watched_at` (`list_id`, `watched_at`, `id`)",
                "JOIN `user_movie_list` list ON list.`id` = item.`list_id`",
                "SET item.`watched_at` = item.`added_at`",
                "WHERE list.`type` = 'watched'",
                "AND item.`watched_at` IS NULL",
                "AND item.`added_at` IS NOT NULL",
                "CHECK (`tmdb_score` IS NULL OR (`tmdb_score` >= 0 AND `tmdb_score` <= 10))",
                "chk_movie_score_douban_count",
                "chk_movie_score_rt_critic_count",
                "chk_drama_score_imdb_count",
                "chk_variety_score_douban_count",
                "chk_anime_score_imdb_count",
                "chk_short_drama_score_douban_count",
                "chk_content_poster_tmdb_vote_count");
        assertThat(sql).doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM");
        assertThat(sql).contains("MySQL DDL 会隐式提交", "不提供 down migration");
    }
}
