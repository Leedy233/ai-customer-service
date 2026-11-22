package com.example.aicustomerservice.config;

import com.example.aicustomerservice.entity.User;
import com.example.aicustomerservice.repository.UserRepository;
import com.example.aicustomerservice.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理端权限拦截器：/ai/admin/** 必须登录且 role=ADMIN。
 * 后端强制校验，前端隐藏按钮仅为体验层。
 */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(CurrentUser.SESSION_USER_ID);
        if (!(userId instanceof Long)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或会话已过期\",\"data\":null}");
            return false;
        }
        User user = userRepository.findById((Long) userId).orElse(null);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无管理端权限\",\"data\":null}");
            return false;
        }
        return true;
    }
}