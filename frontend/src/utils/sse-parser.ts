import type { SSEFrame } from '@/types/sse'

/**
 * SSE 帧解析状态机：按空行分帧；帧内累积 event:/data: 行；
 * data 多行以 \n 连接（保住回答换行）；忽略 ":" 心跳注释。
 * v1 裸流：Spring 写 "data: <chunk>"，剥一个前导空格。
 */
export function createSSEParser(onFrame: (frame: SSEFrame) => void) {
  let buffer = ''

  function parseFrame(raw: string): SSEFrame | null {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of raw.split('\n')) {
      if (line.startsWith(':')) continue
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        let payload = line.slice(5)
        if (payload.startsWith(' ')) payload = payload.slice(1)
        dataLines.push(payload)
      }
    }
    if (dataLines.length === 0) return null
    return { event, data: dataLines.join('\n') }
  }

  return {
    push(chunk: string): void {
      buffer += chunk
      let idx: number
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const raw = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        const frame = parseFrame(raw)
        if (frame) onFrame(frame)
      }
    },
    flush(): void {
      if (buffer.trim()) {
        const frame = parseFrame(buffer)
        if (frame) onFrame(frame)
      }
      buffer = ''
    },
  }
}