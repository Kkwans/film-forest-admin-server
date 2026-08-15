package com.filmforest.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.resource.entity.ResourceMagnet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ResourceMagnetMapper extends BaseMapper<ResourceMagnet> {

    @Select("""
            SELECT * FROM resource_magnet
            WHERE content_type = #{contentType} AND content_id = #{contentId}
              AND source_code = #{sourceCode} AND resource_key IS NOT NULL
            ORDER BY id ASC FOR UPDATE
            """)
    List<ResourceMagnet> selectManagedForUpdate(@Param("contentType") String contentType,
                                                @Param("contentId") Long contentId,
                                                @Param("sourceCode") String sourceCode);

    @Select("""
            SELECT * FROM resource_magnet
            WHERE content_type = #{contentType} AND content_id = #{contentId}
              AND source_code = 'legacy' AND resource_key IS NULL AND is_deleted = 0
            ORDER BY id ASC FOR UPDATE
            """)
    List<ResourceMagnet> selectLegacyForUpdate(@Param("contentType") String contentType,
                                               @Param("contentId") Long contentId);

    @Update("""
            UPDATE resource_magnet SET
              source_code = #{resource.sourceCode}, resource_key = #{resource.resourceKey},
              raw_text = COALESCE(#{resource.rawText}, raw_text), last_seen_at = #{resource.lastSeenAt},
              removed_at = NULL, title = COALESCE(#{resource.title}, title),
              magnet_url = COALESCE(#{resource.magnetUrl}, magnet_url),
              resolution = COALESCE(#{resource.resolution}, resolution),
              has_subtitle = COALESCE(#{resource.hasSubtitle}, has_subtitle),
              is_special_sub = COALESCE(#{resource.isSpecialSub}, is_special_sub),
              sort = #{resource.sort}, is_deleted = 0
            WHERE id = #{resource.id}
            """)
    int updateCrawlerResource(@Param("resource") ResourceMagnet resource);

    @Update("""
            UPDATE resource_magnet
            SET last_seen_at = #{now}
            WHERE id = #{id} AND is_deleted = 0
            """)
    int touchCrawlerResource(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE resource_magnet
            SET removed_at = #{now}, is_deleted = 1
            WHERE id = #{id} AND is_deleted = 0
            """)
    int markCrawlerResourceRemoved(@Param("id") Long id, @Param("now") LocalDateTime now);
}
