package com.filmforest.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.filmforest.system.entity.OperationLog;
import com.filmforest.system.mapper.OperationLogMapper;
import com.filmforest.system.service.OperationLogService;
import org.springframework.stereotype.Service;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperationLogService {

    @Override
    public void log(Long userId, String username, String action, String module,
                    String target, String detail, String ip, Integer status, String errorMessage) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setModule(module);
        log.setTarget(target);
        log.setDetail(detail);
        log.setIp(ip);
        log.setStatus(status);
        log.setErrorMessage(errorMessage);
        save(log);
    }

    @Override
    public Page<OperationLog> listLogs(int page, int size, String action, String module,
                                        Integer status, String keyword) {
        Page<OperationLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();

        if (action != null && !action.isBlank()) {
            wrapper.eq(OperationLog::getAction, action);
        }
        if (module != null && !module.isBlank()) {
            wrapper.eq(OperationLog::getModule, module);
        }
        if (status != null) {
            wrapper.eq(OperationLog::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(OperationLog::getUsername, keyword)
                .or().like(OperationLog::getTarget, keyword)
                .or().like(OperationLog::getDetail, keyword)
            );
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);
        return page(pageParam, wrapper);
    }
}
