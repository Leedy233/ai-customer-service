package com.example.aicustomerservice.config;

import com.example.aicustomerservice.entity.Order;
import com.example.aicustomerservice.entity.User;
import com.example.aicustomerservice.repository.OrderRepository;
import com.example.aicustomerservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (userRepository.count() == 0) {
            userRepository.save(buildUser("zhangsan", encoder.encode("123456")));
            userRepository.save(buildUser("lisi", encoder.encode("123456")));
        }
        // 加法：旧行 role 回填 USER；种子管理员（仅演示环境）
        for (User u : userRepository.findAll()) {
            if (u.getRole() == null) {
                u.setRole("USER");
                userRepository.save(u);
            }
        }
        // 加法：客户资料真值回填（/ai/me 脱敏返回）
        java.util.Map<String, String[]> profiles = java.util.Map.of(
                "zhangsan", new String[]{"13812342046", "zhangsan@mail.com", "VIP,高频客户"},
                "lisi", new String[]{"13900001111", "lisi@mail.com", "新客"},
                "admin", new String[]{"13700000000", "admin@mail.com", "内部账号"});
        for (User u : userRepository.findAll()) {
            String[] p = profiles.get(u.getUsername());
            if (p != null && u.getPhone() == null) {
                u.setPhone(p[0]);
                u.setEmail(p[1]);
                u.setTags(p[2]);
                userRepository.save(u);
            }
        }
        // 加法：已发货旧订单回填 shippedAt/eta（发货+2天口径）
        for (Order o : orderRepository.findAll()) {
            if ("已发货".equals(o.getStatus()) && o.getEta() == null) {
                o.setShippedAt(o.getCreateTime() == null ? LocalDateTime.now().minusDays(1) : o.getCreateTime().plusDays(1));
                o.setEta(o.getShippedAt().plusDays(2));
                orderRepository.save(o);
            }
        }
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = buildUser("admin", encoder.encode("Admin@123"));
            admin.setRole("ADMIN");
            userRepository.save(admin);
        }
        if (orderRepository.count() > 0) {
            return;
        }
        orderRepository.save(build("10001", 1L, "无线蓝牙耳机", "199.00", "已发货"));
        orderRepository.save(build("10002", 2L, "机械键盘", "399.00", "待发货"));
        orderRepository.save(build("10003", 1L, "手机保护壳", "29.90", "已完成"));
    }

    private User buildUser(String username, String encodedPassword) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(encodedPassword);
        user.setRole("USER");
        return user;
    }

    private Order build(String orderNo, Long userId, String product, String amount, String status) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductName(product);
        order.setAmount(new BigDecimal(amount));
        order.setStatus(status);
        order.setCreateTime(LocalDateTime.now().minusDays(2));
        return order;
    }
}