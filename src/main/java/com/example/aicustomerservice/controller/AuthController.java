package com.example.aicustomerservice.controller;

import com.example.aicustomerservice.common.ApiResponse;
import com.example.aicustomerservice.entity.User;
import com.example.aicustomerservice.repository.UserRepository;
import com.example.aicustomerservice.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestParam String username,
                                                  @RequestParam String password,
                                                  HttpServletRequest request) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(CurrentUser.SESSION_USER_ID, user.getId());
        session.setAttribute(CurrentUser.SESSION_USERNAME, user.getUsername());
        return ApiResponse.success(Map.of("userId", user.getId(), "username", user.getUsername(),
                "role", user.getRole() == null ? "USER" : user.getRole()));
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.success("已退出登录");
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(CurrentUser.SESSION_USER_ID);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        User u = userRepository.findById((Long) userId).orElse(null);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("userId", userId);
        m.put("username", session.getAttribute(CurrentUser.SESSION_USERNAME));
        m.put("role", u == null || u.getRole() == null ? "USER" : u.getRole());
        // 加法：客户资料真值，服务端脱敏（手机号中间四位 / 邮箱首字符后掩码）
        m.put("phone", maskPhone(u == null ? null : u.getPhone()));
        m.put("email", maskEmail(u == null ? null : u.getEmail()));
        m.put("tags", u == null || u.getTags() == null || u.getTags().isBlank()
                ? java.util.List.of()
                : java.util.Arrays.asList(u.getTags().split(",")));
        return ApiResponse.success(m);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf('@');
        String head = email.substring(0, at);
        String maskedHead = head.isEmpty() ? head : head.charAt(0) + "***";
        return maskedHead + email.substring(at);
    }
}