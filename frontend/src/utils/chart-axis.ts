import type { EChartsOption } from '@/composables/useECharts'

/**
 * 加法：趋势图公共配置工具。
 * 1) axisTooltip：confine 限制在图表区域内 + position 动态放到指针上方，
 *    保证 Tooltip 不遮挡底部 X 轴日期标签；
 * 2) niceAxisMax：把轴上限取整为 splitNumber 的整数倍（1/2/2.5/5/10 × 10^n 步长），
 *    双 Y 轴同 min=0、同 splitNumber 时水平网格线逐条对齐。
 */

export const AXIS_SPLIT = 5

/** 防遮挡 Tooltip（trigger: 'axis'）： confine + 指针上方动态定位 */
export const axisTooltip: NonNullable<EChartsOption['tooltip']> = {
  trigger: 'axis',
  confine: true,
  position: (point, _params, _dom, _rect, size) => {
    const [w, h] = size.contentSize
    const [vw] = size.viewSize
    const x = Math.max(4, Math.min(point[0] - w / 2, vw - w - 4))
    const y = Math.max(4, point[1] - h - 14)
    return [x, y]
  },
}

/** 轴上限取整：max = step × split，step 取 1/2/2.5/5/10 × 10^n，保证刻度整齐且网格线对齐 */
export function niceAxisMax(value: number, split: number = AXIS_SPLIT): number {
  if (!Number.isFinite(value) || value <= 0) return split
  const raw = value / split
  const mag = 10 ** Math.floor(Math.log10(raw))
  const norm = raw / mag
  const step = (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 2.5 ? 2.5 : norm <= 5 ? 5 : 10) * mag
  return step * split
}

/** 悬浮时 X 轴日期标签高亮（主题色底 + 白字徽章） */
export const xAxisPointerLabel = {
  label: {
    show: true,
    backgroundColor: '#6d5bd0',
    color: '#fff',
    borderRadius: 3,
    padding: [3, 6] as [number, number],
  },
}