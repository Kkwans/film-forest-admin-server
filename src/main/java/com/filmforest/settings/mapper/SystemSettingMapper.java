package com.filmforest.settings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.settings.entity.SystemSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 系统设置数据访问层
 * 提供 system_setting 表的 CRUD 操作
 */
public interface SystemSettingMapper extends BaseMapper<SystemSetting> {
}
