package com.filmforest.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_outbox")
public class MailOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long notificationId;
    private String recipient;
    private String subject;
    private String body;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private String idempotencyKey;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
