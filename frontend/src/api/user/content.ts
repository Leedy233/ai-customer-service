import { get, del } from '@/api/http'
import type { OrderVO, ServerConversation, ServerMessage } from '@/types/models'

/** 本人会话列表（服务端真值） */
export function conversations(): Promise<ServerConversation[]> {
  return get<ServerConversation[]>('/conversations')
}

/** 本人会话历史消息；他人会话后端返回 403 */
export function messages(conversationId: string): Promise<ServerMessage[]> {
  return get<ServerMessage[]>(`/conversations/${encodeURIComponent(conversationId)}/messages`)
}

/** 本人订单权威数据；不存在/他人订单后端返回 404 */
export function order(orderNo: string): Promise<OrderVO> {
  return get<OrderVO>(`/orders/${encodeURIComponent(orderNo)}`)
}

/** 加法：本人订单列表（服务端真值，替代硬编码订单号逐单取） */
export function orders(): Promise<OrderVO[]> {
  return get<OrderVO[]>('/orders')
}

/** 加法：真删除会话（服务端清消息+墓碑；审计/工单保留） */
export function removeConversation(conversationId: string): Promise<{ deleted: boolean }> {
  return del<{ deleted: boolean }>(`/conversations/${encodeURIComponent(conversationId)}`)
}