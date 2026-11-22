import { get } from '@/api/http'
import type { LatencyStats, SystemInfo, TokenStats } from '@/types/admin'

/** 管理端聚合指标：Token / 耗时 / 系统监控 */
export function tokens(days: 7 | 30): Promise<TokenStats> {
  return get<TokenStats>('/admin/stats/tokens', { params: { days } })
}

export function latency(days: 7 | 30): Promise<LatencyStats> {
  return get<LatencyStats>('/admin/stats/latency', { params: { days } })
}

export function system(): Promise<SystemInfo> {
  return get<SystemInfo>('/admin/system')
}