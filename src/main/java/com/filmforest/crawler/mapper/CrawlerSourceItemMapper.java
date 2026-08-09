package com.filmforest.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.crawler.entity.CrawlerSourceItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CrawlerSourceItemMapper extends BaseMapper<CrawlerSourceItem> {

    @Select("""
            SELECT * FROM crawler_source_item
            WHERE source_code = #{sourceCode}
              AND content_type = #{contentType}
              AND external_id = #{externalId}
            LIMIT 1
            """)
    CrawlerSourceItem selectBySourceKey(@Param("sourceCode") String sourceCode,
                                        @Param("contentType") String contentType,
                                        @Param("externalId") String externalId);

    @Insert("""
            INSERT INTO crawler_source_item (
              source_code, content_type, external_id, source_url,
              list_fingerprint, first_seen_at, last_seen_at, last_parse_status
            ) VALUES (
              #{sourceCode}, #{contentType}, #{externalId}, #{sourceUrl},
              #{listFingerprint}, #{now}, #{now}, 'discovered'
            )
            ON DUPLICATE KEY UPDATE
              source_url = VALUES(source_url),
              list_fingerprint = VALUES(list_fingerprint),
              last_seen_at = VALUES(last_seen_at)
            """)
    int upsertListObservation(@Param("sourceCode") String sourceCode,
                              @Param("contentType") String contentType,
                              @Param("externalId") String externalId,
                              @Param("sourceUrl") String sourceUrl,
                              @Param("listFingerprint") String listFingerprint,
                              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crawler_source_item
            SET internal_content_id = COALESCE(#{internalContentId}, internal_content_id),
                canonical_key = COALESCE(#{canonicalKey}, canonical_key),
                detail_fingerprint = COALESCE(#{detailFingerprint}, detail_fingerprint),
                last_fetched_at = COALESCE(#{fetchedAt}, last_fetched_at),
                last_parse_status = #{parseStatus},
                last_error_category = #{errorCategory}
            WHERE source_code = #{sourceCode}
              AND content_type = #{contentType}
              AND external_id = #{externalId}
            """)
    int recordOutcome(@Param("sourceCode") String sourceCode,
                      @Param("contentType") String contentType,
                      @Param("externalId") String externalId,
                      @Param("internalContentId") Long internalContentId,
                      @Param("canonicalKey") String canonicalKey,
                      @Param("detailFingerprint") String detailFingerprint,
                      @Param("fetchedAt") LocalDateTime fetchedAt,
                      @Param("parseStatus") String parseStatus,
                      @Param("errorCategory") String errorCategory);
}
