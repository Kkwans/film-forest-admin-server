package com.filmforest.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.notification.entity.SmtpSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmtpSettingMapper extends BaseMapper<SmtpSetting> {
}
