-- Phase 10: 为评分来源保留独立人数、补充 TMDB 匹配评分，并建立观看时间语义。
-- 回滚边界：执行前完成 film_forest 全量备份；MySQL DDL 会隐式提交；本迁移不提供 down migration。

ALTER TABLE `movie`
  ADD COLUMN `score_douban_count` int unsigned DEFAULT NULL AFTER `score_douban`,
  ADD COLUMN `score_imdb_count` int unsigned DEFAULT NULL AFTER `score_imdb`,
  ADD COLUMN `score_rt_critic_count` int unsigned DEFAULT NULL AFTER `score_rt`,
  ADD COLUMN `score_rt_audience_count` int unsigned DEFAULT NULL AFTER `score_rt_critic_count`,
  ADD CONSTRAINT `chk_movie_score_douban_count`
    CHECK (`score_douban_count` IS NULL OR `score_douban_count` >= 0),
  ADD CONSTRAINT `chk_movie_score_imdb_count`
    CHECK (`score_imdb_count` IS NULL OR `score_imdb_count` >= 0),
  ADD CONSTRAINT `chk_movie_score_rt_critic_count`
    CHECK (`score_rt_critic_count` IS NULL OR `score_rt_critic_count` >= 0),
  ADD CONSTRAINT `chk_movie_score_rt_audience_count`
    CHECK (`score_rt_audience_count` IS NULL OR `score_rt_audience_count` >= 0);

ALTER TABLE `drama`
  ADD COLUMN `score_douban_count` int unsigned DEFAULT NULL AFTER `score_douban`,
  ADD COLUMN `score_imdb_count` int unsigned DEFAULT NULL AFTER `score_imdb`,
  ADD CONSTRAINT `chk_drama_score_douban_count`
    CHECK (`score_douban_count` IS NULL OR `score_douban_count` >= 0),
  ADD CONSTRAINT `chk_drama_score_imdb_count`
    CHECK (`score_imdb_count` IS NULL OR `score_imdb_count` >= 0);

ALTER TABLE `variety`
  ADD COLUMN `score_douban_count` int unsigned DEFAULT NULL AFTER `score_douban`,
  ADD COLUMN `score_imdb_count` int unsigned DEFAULT NULL AFTER `score_imdb`,
  ADD CONSTRAINT `chk_variety_score_douban_count`
    CHECK (`score_douban_count` IS NULL OR `score_douban_count` >= 0),
  ADD CONSTRAINT `chk_variety_score_imdb_count`
    CHECK (`score_imdb_count` IS NULL OR `score_imdb_count` >= 0);

ALTER TABLE `anime`
  ADD COLUMN `score_douban_count` int unsigned DEFAULT NULL AFTER `score_douban`,
  ADD COLUMN `score_imdb_count` int unsigned DEFAULT NULL AFTER `score_imdb`,
  ADD CONSTRAINT `chk_anime_score_douban_count`
    CHECK (`score_douban_count` IS NULL OR `score_douban_count` >= 0),
  ADD CONSTRAINT `chk_anime_score_imdb_count`
    CHECK (`score_imdb_count` IS NULL OR `score_imdb_count` >= 0);

ALTER TABLE `short_drama`
  ADD COLUMN `writer` json DEFAULT NULL AFTER `director`,
  ADD COLUMN `score_douban_count` int unsigned DEFAULT NULL AFTER `score_douban`,
  ADD COLUMN `score_imdb_count` int unsigned DEFAULT NULL AFTER `score_imdb`,
  ADD CONSTRAINT `chk_short_drama_score_douban_count`
    CHECK (`score_douban_count` IS NULL OR `score_douban_count` >= 0),
  ADD CONSTRAINT `chk_short_drama_score_imdb_count`
    CHECK (`score_imdb_count` IS NULL OR `score_imdb_count` >= 0);

ALTER TABLE `content_poster_match`
  ADD COLUMN `tmdb_score` decimal(3,1) DEFAULT NULL AFTER `tmdb_id`,
  ADD COLUMN `tmdb_vote_count` int unsigned DEFAULT NULL AFTER `tmdb_score`,
  ADD KEY `idx_content_poster_tmdb_score` (`tmdb_score`, `tmdb_vote_count`),
  ADD CONSTRAINT `chk_content_poster_tmdb_score`
    CHECK (`tmdb_score` IS NULL OR (`tmdb_score` >= 0 AND `tmdb_score` <= 10)),
  ADD CONSTRAINT `chk_content_poster_tmdb_vote_count`
    CHECK (`tmdb_vote_count` IS NULL OR `tmdb_vote_count` >= 0);

ALTER TABLE `user_movie_list_item`
  ADD COLUMN `watched_at` datetime DEFAULT NULL AFTER `added_at`,
  ADD KEY `idx_item_list_watched_at` (`list_id`, `watched_at`, `id`);

UPDATE `user_movie_list_item` item
JOIN `user_movie_list` list ON list.`id` = item.`list_id`
SET item.`watched_at` = item.`added_at`
WHERE list.`type` = 'watched'
  AND item.`watched_at` IS NULL
  AND item.`added_at` IS NOT NULL;
