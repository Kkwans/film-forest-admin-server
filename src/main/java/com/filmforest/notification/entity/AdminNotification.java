package com.filmforest.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_notification")
public class AdminNotification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String eventType;
    private String severity;
    private String title;
    private String message;
    private String link;
    private String referenceType;
    private Long referenceId;
    private String idempotencyKey;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
