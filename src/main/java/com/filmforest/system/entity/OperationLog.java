package com.filmforest.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作日志表
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;            // 操作用户ID
    private String username;        // 操作用户名
    private String action;          // 操作类型：CREATE/UPDATE/DELETE/LOGIN/EXPORT
    private String module;          // 操作模块：USER/CONTENT/CRAWLER/RESOURCE/SETTING
    private String target;          // 操作目标描述
    private String detail;          // 操作详情
    private String ip;              // 操作IP
    private Integer status;         // 操作状态：0=失败 1=成功
    private String errorMessage;    // 错误信息

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
