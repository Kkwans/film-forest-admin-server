package com.filmforest.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.system.entity.OperationLog;

public interface OperationLogService extends IService<OperationLog> {

    /** 记录操作日志 */
    void log(Long userId, String username, String action, String module, String target, String detail, String ip, Integer status, String errorMessage);

    /** 分页查询操作日志 */
    Page<OperationLog> listLogs(int page, int size, String action, String module, Integer status, String keyword);
}
