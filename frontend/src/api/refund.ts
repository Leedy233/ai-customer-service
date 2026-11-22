import { post } from './http'
import type { RefundApplyResult, RefundConfirmResult } from '@/types/models'

/** 退款第一步：申请（生成 PENDING_CONFIRM，不改订单） */
export function applyRefund(conversationId: string, orderNo: string): Promise<RefundApplyResult> {
  return post<RefundApplyResult>('/refund/apply', null, {
    params: { conversationId, orderNo },
  })
}

/** 退款第二步：用户显式确认（订单置退款中） */
export function confirmRefund(refundId: number): Promise<RefundConfirmResult> {
  return post<RefundConfirmResult>('/refund/confirm', null, {
    params: { refundId },
  })
}