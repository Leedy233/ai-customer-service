/** SSE 帧（v1 裸流 event 恒为 message；v2 命名事件预留） */
export interface SSEFrame {
  event: string
  data: string
}

export type SSEVersion = 'v1' | 'v2'

export interface StreamOptions {
  signal?: AbortSignal
  method?: 'GET' | 'POST'
  body?: unknown
  onFrame: (frame: SSEFrame) => void
}