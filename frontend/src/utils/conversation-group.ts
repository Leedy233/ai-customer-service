/** 会话分组与时间展示规则（今天 / 昨天 / 更早） */

export type ConversationGroup = '今天' | '昨天' | '更早'

function startOfDay(ts: number): number {
  const d = new Date(ts)
  d.setHours(0, 0, 0, 0)
  return d.getTime()
}

export function groupOf(ts: number): ConversationGroup {
  const today = startOfDay(Date.now())
  const day = startOfDay(ts)
  if (day === today) return '今天'
  if (day === today - 86400000) return '昨天'
  return '更早'
}

export function listTime(ts: number): string {
  const d = new Date(ts)
  const g = groupOf(ts)
  if (g === '今天') {
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  if (g === '昨天') return '昨天'
  return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function fullTime(ts: number): string {
  const d = new Date(ts)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

export function clockTime(ts: number): string {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 无首条消息时的会话名：新会话 MM-DD HH:mm */
export function defaultTitle(ts: number): string {
  const d = new Date(ts)
  const p = (n: number) => String(n).padStart(2, '0')
  return `新会话 ${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 同名会话自动加序号，避免列表出现完全相同的标题 */
export function uniqueTitle(base: string, taken: string[]): string {
  if (!taken.includes(base)) return base
  let n = 2
  while (taken.includes(`${base} ${n}`)) n += 1
  return `${base} ${n}`
}

/** 会话摘要：最后一条非工具消息的首行 */
export function summarize(contents: string[]): string {
  for (let i = contents.length - 1; i >= 0; i--) {
    const line = (contents[i] || '').split('\n').find((l) => l.trim().length > 0)
    if (line) return line.trim()
  }
  return ''
}