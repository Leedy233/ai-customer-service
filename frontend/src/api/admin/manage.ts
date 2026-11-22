import { get, post } from '@/api/http'
import type {
  AdminOrderDetail,
  AdminOrderVO,
  AdminRefundVO,
  AuditConvVO,
  AuditMessageVO,
  PageVO,
  RequestLogVO,
} from '@/types/admin'

/** 管理端订单 / 退款 / 审计 / 日志（AdminInterceptor 强制 ADMIN） */
export function orders(params: {
  orderNo?: string
  status?: string
  page: number
  size: number
}): Promise<PageVO<AdminOrderVO>> {
  return get<PageVO<AdminOrderVO>>('/admin/orders', { params })
}

export function orderDetail(orderNo: string): Promise<AdminOrderDetail> {
  return get<AdminOrderDetail>(`/admin/orders/${orderNo}`)
}

export function ship(orderNo: string): Promise<{ orderNo: string; status: string }> {
  return post(`/admin/orders/${orderNo}/ship`)
}

export function refunds(params: {
  status?: string
  orderNo?: string
  page: number
  size: number
}): Promise<PageVO<AdminRefundVO>> {
  return get<PageVO<AdminRefundVO>>('/admin/refunds', { params })
}

export function rejectRefund(id: number, note?: string): Promise<{ id: number; status: string }> {
  return post(`/admin/refunds/${id}/reject`, null, { params: { note } })
}

export function completeRefund(id: number, note?: string): Promise<{ id: number; status: string }> {
  return post(`/admin/refunds/${id}/complete`, null, { params: { note } })
}

export function auditConversations(params: {
  keyword?: string
  username?: string
  page: number
  size: number
}): Promise<PageVO<AuditConvVO>> {
  return get<PageVO<AuditConvVO>>('/admin/audit/conversations', { params })
}

export function auditMessages(conversationId: string): Promise<AuditMessageVO[]> {
  return get<AuditMessageVO[]>(`/admin/audit/conversations/${conversationId}/messages`)
}

export function logs(params: {
  userId?: number
  model?: string
  onlyError?: boolean
  page: number
  size: number
}): Promise<PageVO<RequestLogVO>> {
  return get<PageVO<RequestLogVO>>('/admin/logs', { params })
}