package com.example.aicustomerservice;

import com.example.aicustomerservice.entity.RequestLog;
import com.example.aicustomerservice.repository.OrderRepository;
import com.example.aicustomerservice.repository.RequestLogRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验收集成测试：真实 MySQL（独立 accept schema）+ MockMvc 全拦截器链。
 * 不调用 /ai/chat（避免依赖外部模型 API）；模型相关行为由 eval 脚本实测覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class AcceptanceIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RequestLogRepository requestLogRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 每轮运行前重置 accept schema 业务数据，保证测试幂等可重复 */
    @org.junit.jupiter.api.BeforeAll
    void resetTestData() {
        jdbc.execute("DELETE FROM ai_refund");
        jdbc.execute("DELETE FROM ai_request_log");
        jdbc.execute("DELETE FROM ai_chat_message");
        jdbc.execute("UPDATE t_order SET status='已发货' WHERE order_no='10001'");
        jdbc.execute("UPDATE t_order SET status='待发货' WHERE order_no='10002'");
        jdbc.execute("UPDATE t_order SET status='已完成' WHERE order_no='10003'");
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/ai/login").param("username", username).param("password", password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    @Test
    @Order(1)
    void anonMeReturnsReal401() throws Exception {
        mvc.perform(get("/ai/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void missingParamReturnsBody400() throws Exception {
        mvc.perform(post("/ai/login").param("username", "zhangsan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @Order(3)
    void wrongPasswordReturnsBody401NotHttp401() throws Exception {
        mvc.perform(post("/ai/login").param("username", "zhangsan").param("password", "wrong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @Order(4)
    void adminStatsThreeStates() throws Exception {
        mvc.perform(get("/ai/admin/stats/overview")).andExpect(status().isUnauthorized());
        MockHttpSession user = login("zhangsan", "123456");
        mvc.perform(get("/ai/admin/stats/overview").session(user)).andExpect(status().isForbidden());
        MockHttpSession admin = login("admin", "Admin@123");
        mvc.perform(get("/ai/admin/stats/overview").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(3));
        mvc.perform(get("/ai/admin/stats/trends").param("days", "9").session(admin))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @Order(5)
    void refundTwoStepAndOwnership() throws Exception {
        MockHttpSession lisi = login("lisi", "123456");
        MockHttpSession zhangsan = login("zhangsan", "123456");
        MvcResult apply = mvc.perform(post("/ai/refund/apply")
                        .param("conversationId", "it-ref").param("orderNo", "10002").session(lisi))
                .andExpect(jsonPath("$.data.success").value(true))
                .andReturn();
        String body = apply.getResponse().getContentAsString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"refundId\":(\\d+)").matcher(body);
        assertThat(m.find()).isTrue();
        long refundId = Long.parseLong(m.group(1));
        // 越权确认被拒
        mvc.perform(post("/ai/refund/confirm").param("refundId", String.valueOf(refundId)).session(zhangsan))
                .andExpect(jsonPath("$.code").value(400));
        // 本人确认成功
        mvc.perform(post("/ai/refund/confirm").param("refundId", String.valueOf(refundId)).session(lisi))
                .andExpect(jsonPath("$.data.success").value(true));
        // 重复确认被拒
        mvc.perform(post("/ai/refund/confirm").param("refundId", String.valueOf(refundId)).session(lisi))
                .andExpect(jsonPath("$.code").value(400));
        assertThat(orderRepository.findByOrderNo("10002").orElseThrow().getStatus()).isEqualTo("退款中");
    }

    @Test
    @Order(6)
    void orderReadOwnership() throws Exception {
        MockHttpSession zhangsan = login("zhangsan", "123456");
        MockHttpSession lisi = login("lisi", "123456");
        mvc.perform(get("/ai/orders/10002").session(zhangsan))
                .andExpect(jsonPath("$.code").value(404));
        mvc.perform(get("/ai/orders/10002").session(lisi))
                .andExpect(jsonPath("$.data.productName").value("机械键盘"));
    }

    @Test
    @Order(7)
    void conversationMessagesOwnership() throws Exception {
        MockHttpSession lisi = login("lisi", "123456");
        MockHttpSession zhangsan = login("zhangsan", "123456");
        RequestLog log = new RequestLog();
        log.setConversationId("it-conv");
        log.setUserId(2L);
        log.setModel("test");
        log.setPromptTokens(0L);
        log.setCompletionTokens(0L);
        log.setLatencyMs(1L);
        log.setCreatedAt(LocalDateTime.now());
        requestLogRepository.save(log);
        mvc.perform(get("/ai/conversations/it-conv/messages").session(zhangsan))
                .andExpect(jsonPath("$.code").value(403));
        // 加入攻击：zhangsan 向同会话发请求留下日志，仍不得获得读取权（所有权=创建者）
        RequestLog join = new RequestLog();
        join.setConversationId("it-conv");
        join.setUserId(1L);
        join.setModel("test");
        join.setPromptTokens(0L);
        join.setCompletionTokens(0L);
        join.setLatencyMs(1L);
        join.setCreatedAt(LocalDateTime.now());
        requestLogRepository.save(join);
        mvc.perform(get("/ai/conversations/it-conv/messages").session(zhangsan))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/ai/conversations/it-conv/messages").session(lisi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/ai/conversations").session(lisi))
                .andExpect(jsonPath("$.data[0].id").value("it-conv"));
    }

    @Test
    @Order(8)
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession s = login("zhangsan", "123456");
        mvc.perform(get("/ai/me").session(s)).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/ai/logout").session(s)).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/ai/me").session(s)).andExpect(status().isUnauthorized());
    }
}