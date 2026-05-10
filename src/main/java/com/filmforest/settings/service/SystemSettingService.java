package com.filmforest.settings.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.settings.entity.SystemSetting;

import java.util.Map;

public interface SystemSettingService extends IService<SystemSetting> {

    /** 获取所有设置（key-value 形式） */
    Map<String, String> getAllSettings();

    /** 批量保存设置 */
    void saveSettings(Map<String, String> settings);

    /** 获取单个设置值 */
    String getValue(String key, String defaultValue);
}
