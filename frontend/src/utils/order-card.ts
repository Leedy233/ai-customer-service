import type { OrderCardData } from '@/types/models'

/**
 * v1 过渡方案：从 AI 真实回答文本中提取订单字段渲染卡片。
 * 仅当订单号 + 至少两个字段命中时才出卡片，否则保持纯 Markdown。
 * 后端 GET /ai/orders/{orderNo} / SSE v2 card 事件上线后替换本提取器。
 */
export function parseOrderCard(text: string): OrderCardData | undefined {
  // 实测模型两种真实表述：「订单号：10001」或「订单 10001 的信息如下」
  const orderNo =
    text.match(/订单号[:：]\s*([A-Za-z0-9-]+)/)?.[1] ??
    text.match(/订单\s*([A-Za-z0-9-]{3,})\s*的/)?.[1]
  if (!orderNo) return undefined
  const productName = text.match(/商品(?:名称)?[:：]\s*([^\n，,。]+)/)?.[1]?.trim()
  const amount = text.match(/金额[:：]\s*([0-9]+(?:\.[0-9]+)?)/)?.[1]
  const status = text.match(/(?:订单)?状态[:：]\s*([^\n，,。]+)/)?.[1]?.trim()
  const hits = [productName, amount, status].filter(Boolean).length
  if (hits < 2) return undefined
  return { orderNo, productName, amount, status }
}

/** 订单状态 → 标签色调 */
export function orderStatusTone(status?: string): 'success' | 'warning' | 'info' | 'danger' {
  if (!status) return 'info'
  if (status.includes('已完成')) return 'success'
  if (status.includes('已发货')) return 'warning'
  if (status.includes('退款')) return 'danger'
  return 'info'
}