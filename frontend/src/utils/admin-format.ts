/** 管理端表格时间展示：ISO → YYYY-MM-DD HH:mm */
export function fmtTime(t: string | null | undefined): string {
  if (!t) return '--'
  return t.replace('T', ' ').slice(0, 16)
}

/** 毫秒时长 → x 小时 y 分 */
export function fmtUptime(ms: number): string {
  const min = Math.floor(ms / 60000)
  const h = Math.floor(min / 60)
  return h > 0 ? `${h} 小时 ${min % 60} 分` : `${min} 分`
}