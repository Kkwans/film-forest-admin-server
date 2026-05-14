package com.filmforest.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.content.entity.Variety;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 综艺数据访问层
 * 提供 variety 表的 CRUD 操作
 */
public interface VarietyMapper extends BaseMapper<Variety> {
}
