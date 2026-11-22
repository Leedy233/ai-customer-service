import { get, post } from '@/api/http'
import type { PageVO, TicketVO } from '@/types/admin'

/** 管理端工单 */
export function list(params: {
  status?: string
  kind?: string
  page: number
  size: number
}): Promise<PageVO<TicketVO>> {
  return get<PageVO<TicketVO>>('/admin/tickets', { params })
}

export function detail(id: number): Promise<TicketVO> {
  return get<TicketVO>(`/admin/tickets/${id}`)
}

export function process(id: number): Promise<TicketVO> {
  return post<TicketVO>(`/admin/tickets/${id}/process`)
}

export function close(id: number, note?: string): Promise<TicketVO> {
  return post<TicketVO>(`/admin/tickets/${id}/close`, null, { params: { note } })
}