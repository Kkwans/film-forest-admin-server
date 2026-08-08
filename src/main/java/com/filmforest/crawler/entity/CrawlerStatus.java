package com.filmforest.crawler.entity;

/**
 * 爬虫 Job 状态枚举。
 * Schedule 不再保存执行结果状态；活动态与终态以 Job 为唯一事实源。
 */
public enum CrawlerStatus {

    /** 已入队，等待唯一 Worker 领取。 */
    QUEUED("queued", "排队中"),

    RUNNING("running", "运行中"),

    /** 已收到取消请求，Worker 会在安全边界退出。 */
    CANCEL_REQUESTED("cancel_requested", "正在取消"),

    SUCCESS("success", "成功"),

    /** 已处理部分内容，但存在可定位失败。 */
    PARTIAL_SUCCESS("partial_success", "部分成功"),

    FAILED("failed", "失败"),

    /** Worker 已在安全边界退出。 */
    CANCELLED("cancelled", "已取消"),

    /** 进程重启或心跳过期导致的中断。 */
    INTERRUPTED("interrupted", "已中断");

    private final String code;
    private final String label;

    CrawlerStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 根据 code 查找枚举，找不到返回 null */
    public static CrawlerStatus fromCode(String code) {
        if (code == null) return null;
        for (CrawlerStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        return null;
    }

    /** 判断是否为终态（任务已结束） */
    public boolean isTerminal() {
        return this == SUCCESS || this == PARTIAL_SUCCESS || this == FAILED
                || this == CANCELLED || this == INTERRUPTED;
    }

    /** 判断是否可以重试 */
    public boolean isRetryable() {
        return this == FAILED || this == PARTIAL_SUCCESS
                || this == CANCELLED || this == INTERRUPTED;
    }

    public boolean isActive() {
        return this == QUEUED || this == RUNNING || this == CANCEL_REQUESTED;
    }
}
