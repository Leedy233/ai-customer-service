import { get } from './http'

/** 同步问答（备用能力，聊天工作台默认走 SSE） */
export function chatSync(conversationId: string, message: string): Promise<string> {
  return get<string>('/chat', {
    params: { conversationId, message },
    timeout: 120000,
  })
}

/** SSE 地址：v2 = 结构化帧（sources/delta/tool），v1 = 裸文本流（兼容保留） */
export function streamUrl(conversationId: string, message: string): string {
  const base = import.meta.env.VITE_API_BASE || '/ai'
  const qs = new URLSearchParams({ conversationId, message })
  const path = import.meta.env.VITE_SSE_VERSION === 'v2' ? '/chat/stream/v2' : '/chat/stream'
  return `${base}${path}?${qs.toString()}`
}