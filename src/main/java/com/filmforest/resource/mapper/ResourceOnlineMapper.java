package com.filmforest.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.resource.entity.ResourceOnline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ResourceOnlineMapper extends BaseMapper<ResourceOnline> {

    @Select("""
            SELECT * FROM resource_online
            WHERE content_type = #{contentType} AND content_id = #{contentId}
              AND source_code = #{sourceCode} AND resource_key IS NOT NULL
            ORDER BY id ASC FOR UPDATE
            """)
    List<ResourceOnline> selectManagedForUpdate(@Param("contentType") String contentType,
                                                @Param("contentId") Long contentId,
                                                @Param("sourceCode") String sourceCode);

    @Select("""
            SELECT * FROM resource_online
            WHERE content_type = #{contentType} AND content_id = #{contentId}
              AND source_code = 'legacy' AND resource_key IS NULL AND is_deleted = 0
            ORDER BY id ASC FOR UPDATE
            """)
    List<ResourceOnline> selectLegacyForUpdate(@Param("contentType") String contentType,
                                               @Param("contentId") Long contentId);

    @Update("""
            UPDATE resource_online SET
              source_code = #{resource.sourceCode}, resource_key = #{resource.resourceKey},
              raw_text = #{resource.rawText}, last_seen_at = #{resource.lastSeenAt},
              removed_at = NULL, season = #{resource.season},
              episode_number = #{resource.episodeNumber}, episode_title = #{resource.episodeTitle},
              source_name = #{resource.sourceName}, source_url = #{resource.sourceUrl},
              source_page_url = #{resource.sourcePageUrl}, playback_type = #{resource.playbackType},
              sort = #{resource.sort}, is_deleted = 0
            WHERE id = #{resource.id}
            """)
    int updateCrawlerResource(@Param("resource") ResourceOnline resource);

    @Update("""
            UPDATE resource_online
            SET last_seen_at = #{now}
            WHERE id = #{id} AND is_deleted = 0
            """)
    int touchCrawlerResource(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE resource_online
            SET removed_at = #{now}, is_deleted = 1
            WHERE id = #{id} AND is_deleted = 0
            """)
    int markCrawlerResourceRemoved(@Param("id") Long id, @Param("now") LocalDateTime now);
}
