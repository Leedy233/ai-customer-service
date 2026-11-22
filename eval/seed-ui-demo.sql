-- UI 改造配套数据清理：清空测试性质会话，种入业务示例会话（示例数据，CHANGELOG-UI 已标注）
-- 仅操作数据，不改后端代码与接口
DELETE FROM ai_chat_message;
DELETE FROM ai_request_log;
DELETE FROM ai_queue_entry;
DELETE FROM ai_conversation_meta;

-- zhangsan(uid1) 六条示例会话：今天 2 / 昨天 2 / 更早 2
INSERT INTO ai_chat_message (conversation_id, role, content, tool_name, created_at) VALUES
('seed-logistics-10001', 'user', '订单 10001 的物流到哪了？', NULL, '2026-11-22 09:12:00.000000'),
('seed-logistics-10001', 'tool', '订单 10001：顺丰快递，已发货，包裹已到达杭州中转中心，预计明天送达', 'queryLogistics', '2026-11-22 09:12:02.000000'),
('seed-logistics-10001', 'assistant', '您好，订单 10001 已通过顺丰快递发出，目前包裹已到达杭州中转中心，预计明天送达。如需改派或催件，告诉我即可～', NULL, '2026-11-22 09:12:05.000000'),
('seed-member-day', 'user', '会员日有什么优惠？', NULL, '2026-11-22 10:40:00.000000'),
('seed-member-day', 'assistant', '会员日优惠是这样的：每月 8 号是会员日，全场商品 95 折，仅限普通会员哦～另外提醒一下，优惠券不能和会员日折扣叠加使用。', NULL, '2026-11-22 10:40:04.000000'),
('seed-refund-10003', 'user', '订单 10003 可以退款吗？', NULL, '2026-11-21 16:20:00.000000'),
('seed-refund-10003', 'tool', '订单 10003：已完成，金额 29.90 元，签收未超 7 天，符合无理由退款条件', 'checkRefundEligibility', '2026-11-21 16:20:02.000000'),
('seed-refund-10003', 'assistant', '可以的～订单 10003 签收还在 7 天内，符合无理由退款条件。需要我现在帮你发起退款申请吗？资金操作会先和你二次确认。', NULL, '2026-11-21 16:20:06.000000'),
('seed-invoice', 'user', '发票抬头怎么修改？', NULL, '2026-11-21 11:05:00.000000'),
('seed-invoice', 'assistant', '电子发票抬头在开票前可修改：进入订单详情，申请发票时填写抬头即可。已开具的发票需联系人工客服作废重开，重开后 3 个工作日内发送到预留邮箱。', NULL, '2026-11-21 11:05:04.000000'),
('seed-shipping-rule', 'user', '运费和发货时效是怎么算的？', NULL, '2026-11-18 14:32:00.000000'),
('seed-shipping-rule', 'assistant', '满 99 元包邮，不足 99 元收取 8 元运费；工作日 16:00 前付款的订单当日发出，其余订单次日发出，默认顺丰快递，偏远地区时效顺延 1–3 天。', NULL, '2026-11-18 14:32:04.000000'),
('seed-ship-10002', 'user', '订单 10002 什么时候发货？', NULL, '2026-11-16 09:47:00.000000'),
('seed-ship-10002', 'tool', '订单 10002：待发货，金额 399.00 元，预计今日 16:00 前发出', 'queryOrder', '2026-11-16 09:47:02.000000'),
('seed-ship-10002', 'assistant', '订单 10002 目前待发货，预计今天 16:00 前发出，发出后由顺丰快递承运，物流单号生成后我会同步给你～', NULL, '2026-11-16 09:47:05.000000');

INSERT INTO ai_request_log (conversation_id, user_id, prompt_tokens, completion_tokens, latency_ms, model, exception, created_at) VALUES
('seed-logistics-10001', 1, 1280, 168, 1420, 'deepseek-chat', NULL, '2026-11-22 09:12:05.000000'),
('seed-member-day', 1, 1150, 142, 1180, 'deepseek-chat', NULL, '2026-11-22 10:40:04.000000'),
('seed-refund-10003', 1, 1310, 176, 1650, 'deepseek-chat', NULL, '2026-11-21 16:20:06.000000'),
('seed-invoice', 1, 1080, 150, 1240, 'deepseek-chat', NULL, '2026-11-21 11:05:04.000000'),
('seed-shipping-rule', 1, 1020, 138, 1090, 'deepseek-chat', NULL, '2026-11-18 14:32:04.000000'),
('seed-ship-10002', 1, 1260, 160, 1380, 'deepseek-chat', NULL, '2026-11-16 09:47:05.000000');

-- lisi(uid2) 两条示例会话
INSERT INTO ai_chat_message (conversation_id, role, content, tool_name, created_at) VALUES
('seed-lisi-logistics', 'user', '订单 10002 的物流到哪了？', NULL, '2026-11-22 08:50:00.000000'),
('seed-lisi-logistics', 'tool', '订单 10002：待发货，金额 399.00 元，预计今日 16:00 前发出', 'queryOrder', '2026-11-22 08:50:02.000000'),
('seed-lisi-logistics', 'assistant', '订单 10002 还在待发货状态，预计今天 16:00 前发出，发出后顺丰快递承运，有物流更新我会第一时间告诉你～', NULL, '2026-11-22 08:50:05.000000'),
('seed-lisi-return', 'user', '退货期限是多久？', NULL, '2026-11-21 15:02:00.000000'),
('seed-lisi-return', 'assistant', '您好～退货分两种情况：签收 7 天内支持无理由退货；超过 7 天如属质量问题，仍可联系人工客服申请售后处理。', NULL, '2026-11-21 15:02:04.000000');

INSERT INTO ai_request_log (conversation_id, user_id, prompt_tokens, completion_tokens, latency_ms, model, exception, created_at) VALUES
('seed-lisi-logistics', 2, 1240, 158, 1310, 'deepseek-chat', NULL, '2026-11-22 08:50:05.000000'),
('seed-lisi-return', 2, 1060, 132, 1120, 'deepseek-chat', NULL, '2026-11-21 15:02:04.000000');

-- 切片 2-10 基线：订单状态复位 + 示例工单/退款（管理端首屏有真实数据）
UPDATE t_order SET status='已发货' WHERE order_no='10001';
UPDATE t_order SET status='待发货' WHERE order_no='10002';
UPDATE t_order SET status='已完成' WHERE order_no='10003';
DELETE FROM ai_ticket;
DELETE FROM ai_refund;
DELETE FROM ai_knowledge_doc WHERE source='ADMIN';

INSERT INTO ai_ticket (ticket_no, user_id, conversation_id, kind, category, summary, status, rating, resolved, assignee, close_note, created_at, closed_at) VALUES
('GD20261120002', 1, 'seed-invoice', 'END', '发票服务', '发票抬头怎么修改？', 'CLOSED', 5, 1, 'admin', '已提供作废重开指引', '2026-11-20 15:42:00.000000', '2026-11-20 15:42:00.000000'),
('GD20261118001', 1, 'seed-shipping-rule', 'TRANSFER', '物流查询', '运费和发货时效是怎么算的？', 'CLOSED', NULL, NULL, 'admin', '已答复运费与时效规则', '2026-11-18 10:05:00.000000', '2026-11-18 10:20:00.000000');

INSERT INTO ai_refund (order_no, user_id, conversation_id, amount, status, created_at, version) VALUES
('10003', 1, 'seed-refund-10003', 29.90, 'PENDING_CONFIRM', '2026-11-21 16:21:00.000000', 0);

-- 加法：知识库文档名改业务名（溯源展示用；classpath 迁移仅在空表时跑，不受影响）
UPDATE ai_knowledge_doc SET title = '配送与运费规则' WHERE title = 'shipping.md';
UPDATE ai_knowledge_doc SET title = '退款政策 v2' WHERE title = 'refund.md';
UPDATE ai_knowledge_doc SET title = '会员权益与发票指引' WHERE title = 'faq.md';

-- 加法：种子历史 assistant 行补真溯源（doc/片段序号/原文前160字，取自知识库真内容）
UPDATE ai_chat_message m JOIN ai_knowledge_doc d ON d.title = '配送与运费规则'
SET m.sources = JSON_ARRAY(JSON_OBJECT('doc', d.title, 'section', '片段 1', 'excerpt', LEFT(d.content, 160)))
WHERE m.conversation_id = 'seed-logistics-10001' AND m.role = 'assistant';
UPDATE ai_chat_message m JOIN ai_knowledge_doc d ON d.title = '配送与运费规则'
SET m.sources = JSON_ARRAY(JSON_OBJECT('doc', d.title, 'section', '片段 1', 'excerpt', LEFT(d.content, 160)))
WHERE m.conversation_id = 'seed-shipping-rule' AND m.role = 'assistant';
UPDATE ai_chat_message m JOIN ai_knowledge_doc d ON d.title = '会员权益与发票指引'
SET m.sources = JSON_ARRAY(JSON_OBJECT('doc', d.title, 'section', '片段 1', 'excerpt', LEFT(d.content, 160)))
WHERE m.conversation_id IN ('seed-member-day', 'seed-invoice') AND m.role = 'assistant';
UPDATE ai_chat_message m JOIN ai_knowledge_doc d ON d.title = '退款政策 v2'
SET m.sources = JSON_ARRAY(JSON_OBJECT('doc', d.title, 'section', '片段 1', 'excerpt', LEFT(d.content, 160)))
WHERE m.conversation_id = 'seed-refund-10003' AND m.role = 'assistant';