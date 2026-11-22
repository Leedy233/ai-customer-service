package com.example.aicustomerservice.security;

/**
 * 未登录或会话过期时抛出，由全局异常处理器转为 401。
 */
public class NotLoginException extends RuntimeException {

    public NotLoginException() {
        super("未登录或会话已过期");
    }
}