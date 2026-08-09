package com.filmforest.notification.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_notification_preference")
public class AdminNotificationPreference {
    @TableId
    private Long userId;
    private Integer emailEnabled;
    private Integer crawlerFailure;
    private Integer crawlerRecovery;
    private Integer dataAnomaly;
    private Integer crawlerSuccess;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
