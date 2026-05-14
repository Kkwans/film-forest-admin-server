package com.filmforest.common.exception;

/**
 * 业务异常
 * 用于在 Service/Controller 层抛出可预期的错误，
 * 由 GlobalExceptionHandler 统一捕获并返回友好提示。
 *
 * 用法示例：
 *   throw new BusinessException("内容不存在");
 *   throw new BusinessException(404, "电影不存在");
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
