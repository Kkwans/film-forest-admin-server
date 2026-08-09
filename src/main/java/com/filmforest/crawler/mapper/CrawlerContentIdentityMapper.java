package com.filmforest.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.crawler.entity.CrawlerContentIdentity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CrawlerContentIdentityMapper extends BaseMapper<CrawlerContentIdentity> {

    @Insert("""
            INSERT INTO crawler_content_identity (
              content_type, canonical_key, normalized_title, release_year
            ) VALUES (
              #{contentType}, #{canonicalKey}, #{normalizedTitle}, #{releaseYear}
            )
            ON DUPLICATE KEY UPDATE
              id = LAST_INSERT_ID(id),
              normalized_title = VALUES(normalized_title),
              release_year = VALUES(release_year)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int reserve(CrawlerContentIdentity identity);

    @Select("""
            SELECT * FROM crawler_content_identity
            WHERE content_type = #{contentType} AND canonical_key = #{canonicalKey}
            LIMIT 1
            """)
    CrawlerContentIdentity selectByCanonicalKey(@Param("contentType") String contentType,
                                                 @Param("canonicalKey") String canonicalKey);
}
