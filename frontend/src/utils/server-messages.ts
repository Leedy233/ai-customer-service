import type { Citation, MessageVM, ServerMessage } from '@/types/models'
import { detectCitation } from '@/utils/citation'
import { parseOrderCard } from '@/utils/order-card'

/** 工具入参兜底：旧数据无 args 字段时从返回文本提取订单号推导 */
export function toolArgs(content: string): Record<string, string> {
  const m = content.match(/订单\s*(\d{4,})/)
  return m ? { orderNo: m[1] } : {}
}

function parseArgs(json?: string | null): Record<string, string> | undefined {
  if (!json) return undefined
  try {
    return JSON.parse(json) as Record<string, string>
  } catch {
    return undefined
  }
}

function parseSources(json?: string | null): Citation[] | undefined {
  if (!json) return undefined
  try {
    const arr = JSON.parse(json) as Citation[]
    return Array.isArray(arr) && arr.length ? arr : undefined
  } catch {
    return undefined
  }
}

/** 服务端历史 → 视图模型：工具行独立成动作条；入参/耗时/溯源优先服务端真值，旧数据回退推导 */
export function mapServerMessages(ms: ServerMessage[]): MessageVM[] {
  const vms: MessageVM[] = []
  let prevTs = 0
  for (const m of ms) {
    const ts = Date.parse(m.createdAt) || Date.now()
    if (m.role === 'tool') {
      vms.push({
        id: `srv-${m.id}`,
        role: 'tool',
        content: m.content,
        toolName: m.toolName ?? 'tool',
        toolArgs: parseArgs(m.toolArgs),
        toolDurationMs: m.toolDurationMs ?? (prevTs ? Math.max(ts - prevTs, 0) : undefined),
        createdAt: ts,
      })
    } else if (m.role === 'assistant') {
      const sources = parseSources(m.sources)
      vms.push({
        id: `srv-${m.id}`,
        role: 'assistant',
        content: m.content,
        status: 'sent',
        card: parseOrderCard(m.content),
        sources,
        citation: sources?.[0] ?? detectCitation(m.content),
        createdAt: ts,
      })
    } else {
      vms.push({
        id: `srv-${m.id}`,
        role: 'user',
        content: m.content,
        status: 'sent',
        createdAt: ts,
      })
    }
    prevTs = ts
  }
  return vms
}