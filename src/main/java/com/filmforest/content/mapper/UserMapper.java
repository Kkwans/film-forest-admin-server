package com.filmforest.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.content.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 用户数据访问层
 * 提供 user 表的 CRUD 操作
 */
public interface UserMapper extends BaseMapper<User> {
}
