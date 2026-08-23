package com.filmforest.common.auth;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 当前管理员各个长列表的分页偏好。 */
@Data
@TableName("admin_list_page_preference")
public class AdminListPagePreference {
    @TableId
    private Long userId;
    private Integer crawlerLogsSize;
    private Integer operationLogsSize;
    private Integer resourcesSize;
    private Integer notificationsSize;
    private Integer usersSize;
    private Integer contentSize;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
