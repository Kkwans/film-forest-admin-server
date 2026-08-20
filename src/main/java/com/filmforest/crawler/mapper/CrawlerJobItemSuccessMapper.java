package com.filmforest.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.crawler.entity.CrawlerJobItemSuccess;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CrawlerJobItemSuccessMapper extends BaseMapper<CrawlerJobItemSuccess> {

    @Insert("""
            INSERT INTO crawler_job_item_success (
              job_id, source_code, content_type, external_id, source_url,
              content_id, result_type, title, alias, poster_url, year,
              directors, writers, actors, genres, regions, languages,
              release_date, duration, total_episodes, score_douban,
              score_imdb, score_rt, crawled_at
            ) VALUES (
              #{item.jobId}, #{item.sourceCode}, #{item.contentType}, #{item.externalId},
              #{item.sourceUrl}, #{item.contentId}, #{item.resultType}, #{item.title},
              #{item.alias}, #{item.posterUrl}, #{item.year}, #{item.directors},
              #{item.writers}, #{item.actors}, #{item.genres}, #{item.regions},
              #{item.languages}, #{item.releaseDate}, #{item.duration},
              #{item.totalEpisodes}, #{item.scoreDouban}, #{item.scoreImdb},
              #{item.scoreRt}, #{item.crawledAt}
            )
            ON DUPLICATE KEY UPDATE
              source_url = VALUES(source_url), content_id = VALUES(content_id),
              result_type = VALUES(result_type), title = VALUES(title),
              alias = VALUES(alias), poster_url = VALUES(poster_url), year = VALUES(year),
              directors = VALUES(directors), writers = VALUES(writers),
              actors = VALUES(actors), genres = VALUES(genres), regions = VALUES(regions),
              languages = VALUES(languages), release_date = VALUES(release_date),
              duration = VALUES(duration), total_episodes = VALUES(total_episodes),
              score_douban = VALUES(score_douban), score_imdb = VALUES(score_imdb),
              score_rt = VALUES(score_rt), crawled_at = VALUES(crawled_at)
            """)
    int upsertSuccess(@Param("item") CrawlerJobItemSuccess item);

    @Select("""
            <script>
            SELECT * FROM crawler_job_item_success
            WHERE job_id = #{jobId}
              <if test="keyword != null">AND (title LIKE CONCAT('%', #{keyword}, '%')
                OR alias LIKE CONCAT('%', #{keyword}, '%')
                OR external_id LIKE CONCAT('%', #{keyword}, '%'))</if>
            ORDER BY crawled_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CrawlerJobItemSuccess> selectSuccessPage(@Param("jobId") Long jobId,
                                                   @Param("keyword") String keyword,
                                                   @Param("limit") int limit,
                                                   @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM crawler_job_item_success
            WHERE job_id = #{jobId}
              <if test="keyword != null">AND (title LIKE CONCAT('%', #{keyword}, '%')
                OR alias LIKE CONCAT('%', #{keyword}, '%')
                OR external_id LIKE CONCAT('%', #{keyword}, '%'))</if>
            </script>
            """)
    long countSuccesses(@Param("jobId") Long jobId, @Param("keyword") String keyword);
}
