<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { notifyError } from '@/api/http'
import * as statsApi from '@/api/admin/stats'
import BaseChart from '@/components/charts/BaseChart.vue'
import { AXIS_SPLIT, axisTooltip, niceAxisMax, xAxisPointerLabel } from '@/utils/chart-axis'
import type { StatsOverview, TrendPoint } from '@/types/admin'

const loading = ref(false)
const overview = ref<StatsOverview | null>(null)
const trends = ref<TrendPoint[]>([])
const days = ref<7 | 30>(7)

async function load(): Promise<void> {
  loading.value = true
  try {
    const [o, t] = await Promise.all([statsApi.overview(), statsApi.trends(days.value)])
    overview.value = o
    trends.value = t
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)

const cards = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { label: '今日会话', value: o.todayConversations },
    { label: '今日请求', value: o.todayRequests },
    { label: '今日词元', value: o.todayTokens },
    { label: '今日均耗时(ms)', value: o.avgLatencyMs },
    { label: '待确认退款', value: o.pendingRefunds },
    { label: '订单总数', value: o.totalOrders },
  ]
})

const lineOption = computed(() => {
  const requests = trends.value.map((t) => t.requests)
  const tokens = trends.value.map((t) => t.tokens)
  return {
    // 防遮挡：confine + 指针上方动态定位，不再盖住 X 轴日期
    tooltip: axisTooltip,
    legend: { data: ['请求数', '词元'], top: 0, left: 'center' },
    grid: { left: 48, right: 56, top: 40, bottom: 28 },
    xAxis: {
      type: 'category',
      data: trends.value.map((t) => t.date.slice(5)),
      // 自动隐藏重叠标签；悬浮时日期标签以主题色徽章高亮
      axisLabel: { hideOverlap: true, interval: 'auto' },
      axisPointer: xAxisPointerLabel,
    },
    // 双 Y 轴同 min=0、同 splitNumber、上限取整为分片整数倍 → 水平网格线逐条对齐；
    // 右轴不画网格线，避免两套线叠影
    yAxis: [
      {
        type: 'value',
        name: '请求',
        min: 0,
        splitNumber: AXIS_SPLIT,
        max: niceAxisMax(Math.max(...requests, 0)),
      },
      {
        type: 'value',
        name: '词元',
        min: 0,
        splitNumber: AXIS_SPLIT,
        max: niceAxisMax(Math.max(...tokens, 0)),
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '请求数',
        type: 'line',
        smooth: true,
        data: requests,
        itemStyle: { color: '#6d5bd0' },
      },
      {
        name: '词元',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: tokens,
        itemStyle: { color: '#f79009' },
      },
    ],
  }
})

const pieOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [
    {
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '45%'],
      data: Object.entries(overview.value?.ordersByStatus ?? {}).map(([name, value]) => ({
        name,
        value,
      })),
    },
  ],
}))

const barOption = computed(() => ({
  tooltip: axisTooltip,
  grid: { left: 48, right: 24, top: 32, bottom: 28 },
  xAxis: {
    type: 'category',
    data: trends.value.map((t) => t.date.slice(5)),
    axisLabel: { hideOverlap: true, interval: 'auto' },
    axisPointer: xAxisPointerLabel,
  },
  yAxis: { type: 'value', name: 'ms' },
  series: [
    {
      name: '平均耗时',
      type: 'bar',
      barMaxWidth: 28,
      data: trends.value.map((t) => t.avgLatencyMs),
      itemStyle: { color: '#12b76a', borderRadius: [4, 4, 0, 0] },
    },
  ],
}))
</script>

<template>
  <div v-loading="loading" class="flex flex-col gap-4">
    <div class="flex flex-wrap items-center gap-3">
      <span class="text-sm text-ink-3">统计范围</span>
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
      <el-button class="ml-auto" size="small" :icon="Refresh" :loading="loading" @click="load">
        刷新
      </el-button>
    </div>

    <div class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
      <div
        v-for="c in cards"
        :key="c.label"
        class="rounded-xl border border-line bg-card p-4"
      >
        <div class="text-xs text-ink-3">{{ c.label }}</div>
        <div class="mt-1 text-2xl font-semibold text-ink">
          {{ c.value.toLocaleString() }}
        </div>
      </div>
    </div>

    <div class="grid gap-4 xl:grid-cols-3">
      <div class="rounded-xl border border-line bg-card p-4 xl:col-span-2">
        <div class="mb-2 text-sm font-semibold text-ink">请求与词元趋势</div>
        <div class="h-72">
          <BaseChart :option="lineOption" />
        </div>
      </div>
      <div class="rounded-xl border border-line bg-card p-4">
        <div class="mb-2 text-sm font-semibold text-ink">订单状态分布</div>
        <div class="h-72">
          <BaseChart :option="pieOption" />
        </div>
      </div>
    </div>

    <div class="rounded-xl border border-line bg-card p-4">
      <div class="mb-2 text-sm font-semibold text-ink">模型平均响应耗时（ms/日）</div>
      <div class="h-64">
        <BaseChart :option="barOption" />
      </div>
    </div>
  </div>
</template>