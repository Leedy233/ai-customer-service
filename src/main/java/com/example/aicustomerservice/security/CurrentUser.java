package com.example.aicustomerservice.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 真实登录态：从当前请求的 Session 中读取用户 ID。
 * 替代早期的模拟 UserContext；工具调用链通过 ToolContext 传递 userId，
 * 以兼容流式响应中工具可能在非请求线程执行的情况。
 */
@Component
public class CurrentUser {

    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_USERNAME = "username";

    public Long requireUserId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new NotLoginException();
        }
        HttpSession session = attributes.getRequest().getSession(false);
        Object userId = session == null ? null : session.getAttribute(SESSION_USER_ID);
        if (!(userId instanceof Long)) {
            throw new NotLoginException();
        }
        return (Long) userId;
    }

    /** 当前会话用户名（加法）：管理端操作留痕 assignee 用 */
    public String username() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpSession session = attributes.getRequest().getSession(false);
        Object name = session == null ? null : session.getAttribute(SESSION_USERNAME);
        return name instanceof String s ? s : null;
    }
}