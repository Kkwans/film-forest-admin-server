package com.filmforest.settings.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.settings.entity.SystemSetting;
import com.filmforest.settings.mapper.SystemSettingMapper;
import com.filmforest.settings.service.SystemSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemSettingServiceImpl extends ServiceImpl<SystemSettingMapper, SystemSetting>
        implements SystemSettingService {

    @Override
    public Map<String, String> getAllSettings() {
        List<SystemSetting> list = list();
        Map<String, String> map = new HashMap<>();
        for (SystemSetting s : list) {
            map.put(s.getSettingKey(), s.getSettingValue());
        }
        return map;
    }

    @Override
    @Transactional
    public void saveSettings(Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            LambdaQueryWrapper<SystemSetting> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SystemSetting::getSettingKey, entry.getKey());
            SystemSetting existing = getOne(wrapper);
            if (existing != null) {
                existing.setSettingValue(entry.getValue());
                updateById(existing);
            } else {
                SystemSetting setting = new SystemSetting();
                setting.setSettingKey(entry.getKey());
                setting.setSettingValue(entry.getValue());
                save(setting);
            }
        }
    }

    @Override
    public String getValue(String key, String defaultValue) {
        LambdaQueryWrapper<SystemSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemSetting::getSettingKey, key);
        SystemSetting setting = getOne(wrapper);
        return setting != null ? setting.getSettingValue() : defaultValue;
    }
}
