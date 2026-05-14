package com.filmforest.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.content.entity.Drama;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 电视剧数据访问层
 * 提供 drama 表的 CRUD 操作
 */
public interface DramaMapper extends BaseMapper<Drama> {
}
