package com.filmforest.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.content.entity.Anime;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 动漫数据访问层
 * 提供 anime 表的 CRUD 操作
 */
public interface AnimeMapper extends BaseMapper<Anime> {
}
