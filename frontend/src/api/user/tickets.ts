import { get, post } from '@/api/http'
import type { TicketVO } from '@/types/admin'

/** 用户侧工单：转人工 / 结束回执 / 取消 / 评价 / 本人列表 */
export function create(body: {
  conversationId: string
  kind: 'TRANSFER' | 'END'
  category?: string
  summary?: string
  resolved?: boolean
}): Promise<TicketVO> {
  return post<TicketVO>('/tickets', body)
}

export function cancel(id: number): Promise<TicketVO> {
  return post<TicketVO>(`/tickets/${id}/cancel`)
}

export function rate(
  id: number,
  rating: number,
  feedback?: string,
  resolved?: boolean,
): Promise<TicketVO> {
  return post<TicketVO>(`/tickets/${id}/rate`, { rating, feedback, resolved })
}

export function resolve(id: number, resolved: boolean): Promise<TicketVO> {
  return post<TicketVO>(`/tickets/${id}/resolve`, { resolved })
}

export function mine(): Promise<TicketVO[]> {
  return get<TicketVO[]>('/tickets/mine')
}