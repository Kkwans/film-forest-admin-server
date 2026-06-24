package com.filmforest.crawler.entity;

/**
 * 爬虫任务/调度状态枚举
 * 统一管理所有爬虫相关状态，避免硬编码字符串
 */
public enum CrawlerStatus {

    /** 空闲 - 等待下次调度 */
    IDLE("idle", "空闲"),

    /** 运行中 - 正在执行爬取 */
    RUNNING("running", "运行中"),

    /** 成功 - 任务完成 */
    SUCCESS("success", "成功"),

    /** 失败 - 任务执行出错 */
    FAILED("failed", "失败"),

    /** 已停止 - 用户手动停止 */
    STOPPED("stopped", "已停止"),

    /** 已禁用 - 调度配置被禁用 */
    DISABLED("disabled", "已禁用"),

    /** 等待重试 - 失败后等待重试 */
    PENDING_RETRY("pending_retry", "等待重试");

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
        return this == SUCCESS || this == FAILED || this == STOPPED;
    }

    /** 判断是否可以重试 */
    public boolean isRetryable() {
        return this == FAILED || this == STOPPED;
    }
}
