import { get, post } from '@/api/http'
import type { QueueStatus } from '@/types/models'

/** 加法：转人工排队真值接口（替代前端演示位置） */
export function join(conversationId: string): Promise<QueueStatus> {
  return post<QueueStatus>('/queue/join', { conversationId })
}

export function status(conversationId: string): Promise<QueueStatus> {
  return get<QueueStatus>('/queue/status', { params: { conversationId } })
}

export function cancel(conversationId: string): Promise<QueueStatus> {
  return post<QueueStatus>('/queue/cancel', { conversationId })
}