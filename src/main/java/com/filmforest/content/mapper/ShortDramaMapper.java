package com.filmforest.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.content.entity.ShortDrama;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 短剧数据访问层
 * 提供 short_drama 表的 CRUD 操作
 */
public interface ShortDramaMapper extends BaseMapper<ShortDrama> {
}
