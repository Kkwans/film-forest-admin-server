package com.filmforest.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.notification.entity.MailOutbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MailOutboxMapper extends BaseMapper<MailOutbox> {
}
