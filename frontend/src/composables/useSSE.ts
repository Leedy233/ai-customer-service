import type { StreamOptions } from '@/types/sse'
import { createSSEParser } from '@/utils/sse-parser'
import { ApiError } from '@/utils/errors'

/**
 * SSE 传输层：fetch + ReadableStream 手动解析。
 * 不用 EventSource：不支持 POST/自定义头，且自动重连会重发提问。
 * 断线不自动重试，由 UI 显式重试。
 */
export async function streamSSE(url: string, opts: StreamOptions): Promise<void> {
  const res = await fetch(url, {
    method: opts.method ?? 'GET',
    credentials: 'include',
    headers: opts.body ? { 'Content-Type': 'application/json' } : undefined,
    body: opts.body ? JSON.stringify(opts.body) : undefined,
    signal: opts.signal,
  })

  if (!res.ok) {
    let msg = `服务异常（HTTP ${res.status}）`
    try {
      const body = (await res.json()) as { message?: string; code?: number }
      if (body?.message) msg = body.message
      throw new ApiError(body?.code ?? res.status, msg, res.status)
    } catch (e) {
      if (e instanceof ApiError) throw e
      throw new ApiError(res.status, msg, res.status)
    }
  }
  if (!res.body) throw new ApiError(0, '当前浏览器不支持流式读取')

  const parser = createSSEParser(opts.onFrame)
  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      parser.push(decoder.decode(value, { stream: true }))
    }
    parser.push(decoder.decode())
    parser.flush()
  } finally {
    reader.releaseLock()
  }
}