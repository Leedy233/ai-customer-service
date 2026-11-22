import { describe, expect, it } from 'vitest'
import { createSSEParser } from '@/utils/sse-parser'
import type { SSEFrame } from '@/types/sse'

function collect(chunks: string[], flush = false): SSEFrame[] {
  const frames: SSEFrame[] = []
  const p = createSSEParser((f) => frames.push(f))
  chunks.forEach((c) => p.push(c))
  if (flush) p.flush()
  return frames
}

describe('createSSEParser', () => {
  it('剥一个前导空格', () => {
    expect(collect(['data: 你好\n\n'])).toEqual([{ event: 'message', data: '你好' }])
  })

  it('多行 data 以换行连接', () => {
    expect(collect(['data: 第一行\ndata: 第二行\n\n'])[0].data).toBe('第一行\n第二行')
  })

  it('忽略心跳注释', () => {
    expect(collect([': ping\n\ndata: x\n\n'])).toEqual([{ event: 'message', data: 'x' }])
  })

  it('跨 chunk 分帧', () => {
    expect(collect(['data: 你', '好\n\n'])).toEqual([{ event: 'message', data: '你好' }])
  })

  it('event 字段识别', () => {
    expect(collect(['event: done\ndata: [DONE]\n\n'])[0].event).toBe('done')
  })

  it('flush 收尾残帧', () => {
    expect(collect(['data: 尾'], true)).toEqual([{ event: 'message', data: '尾' }])
  })
})