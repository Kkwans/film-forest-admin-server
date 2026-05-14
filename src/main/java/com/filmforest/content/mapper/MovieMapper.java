package com.filmforest.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.content.entity.Movie;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 电影数据访问层
 * 提供 movie 表的 CRUD 操作
 */
public interface MovieMapper extends BaseMapper<Movie> {
}
