export function formatMoney(v: number | string | undefined): string {
  if (v === undefined || v === '') return '--'
  const n = typeof v === 'string' ? Number(v) : v
  if (Number.isNaN(n)) return String(v)
  return `¥${n.toFixed(2)}`
}

export function formatTime(ts: number): string {
  const d = new Date(ts)
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  const hh = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  if (sameDay) return hh
  return `${d.getMonth() + 1}/${d.getDate()} ${hh}`
}

export function truncate(s: string, max = 24): string {
  return s.length > max ? `${s.slice(0, max)}…` : s
}

let seq = 0
export function uid(prefix = 'm'): string {
  seq += 1
  return `${prefix}-${Date.now()}-${seq}`
}