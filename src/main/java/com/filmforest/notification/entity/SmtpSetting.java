package com.filmforest.notification.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("smtp_setting")
public class SmtpSetting {
    @TableId
    private Integer id;
    private String host;
    private Integer port;
    private String username;
    @JsonIgnore private byte[] passwordCiphertext;
    @JsonIgnore private byte[] passwordIv;
    @JsonIgnore private Integer passwordKeyVersion;
    private String fromEmail;
    private String fromName;
    private String securityMode;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
