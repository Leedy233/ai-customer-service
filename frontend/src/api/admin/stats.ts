import { get } from '@/api/http'
import type { StatsOverview, TrendPoint } from '@/types/admin'

/** 管理端统计（后端 AdminInterceptor 强制 ADMIN 校验） */
export function overview(): Promise<StatsOverview> {
  return get<StatsOverview>('/admin/stats/overview')
}

export function trends(days: 7 | 30): Promise<TrendPoint[]> {
  return get<TrendPoint[]>('/admin/stats/trends', { params: { days } })
}