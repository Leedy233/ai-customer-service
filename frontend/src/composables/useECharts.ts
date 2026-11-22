import { onBeforeUnmount, onMounted, watch } from 'vue'
import type { Ref } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent, AxisPointerComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  AxisPointerComponent,
  LegendComponent,
  TitleComponent,
  CanvasRenderer,
])

export type EChartsOption = EChartsCoreOption

/** 图表生命周期封装：init / setOption / resize / dispose，按需注册减小包体 */
export function useECharts(el: Ref<HTMLElement | undefined>, option: Ref<EChartsOption>): void {
  let chart: echarts.ECharts | null = null
  let ro: ResizeObserver | null = null

  onMounted(() => {
    if (!el.value) return
    chart = echarts.init(el.value)
    chart.setOption(option.value)
    ro = new ResizeObserver(() => chart?.resize())
    ro.observe(el.value)
  })

  watch(
    option,
    (v) => {
      chart?.setOption(v, true)
    },
    { deep: true },
  )

  onBeforeUnmount(() => {
    ro?.disconnect()
    ro = null
    chart?.dispose()
    chart = null
  })
}