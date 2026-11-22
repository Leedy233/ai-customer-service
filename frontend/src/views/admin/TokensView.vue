<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import type { TableInstance } from 'element-plus'
import { notifyError } from '@/api/http'
import * as metrics from '@/api/admin/metrics'
import BaseChart from '@/components/charts/BaseChart.vue'
import PageHead from '@/components/admin/PageHead.vue'
import { axisTooltip, xAxisPointerLabel } from '@/utils/chart-axis'
import type { TokenStats } from '@/types/admin'

const days = ref<7 | 30>(7)
const loading = ref(false)
const data = ref<TokenStats | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    data.value = await metrics.tokens(days.value)
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}

const cards = computed(() => {
  const d = data.value
  if (!d) return []
  return [
    { label: '总词元数', value: d.totalTokens },
    { label: '提示词词元', value: d.totalPromptTokens },
    { label: '生成词元', value: d.totalCompletionTokens },
  ]
})

// 与 grid 共用左右留白常量：tooltip 按类目中心吸附时需要同一套坐标
const GRID_LEFT = 56
const GRID_RIGHT = 24

const barOption = computed(() => ({
  // 标题居左 + 图例居右同一水平线（绘图区外）；图例圆角矩形色块、加大图标/文字/条目间距
  title: {
    text: '每日词元构成',
    left: 0,
    top: 0,
    textStyle: { fontSize: 14, fontWeight: 600, color: '#1f2329' },
  },
  legend: {
    data: ['提示词', '生成'],
    right: 0,
    top: 0,
    icon: 'roundRect',
    itemWidth: 14,
    itemHeight: 8,
    itemGap: 16,
    textStyle: { color: '#4e5969', fontSize: 12, padding: [0, 0, 0, 4] },
  },
  tooltip: {
    ...axisTooltip,
    // x 吸附到类目中心（与 axisPointer 阴影带/徽章严格同列），消除鼠标原始坐标与吸附中心的错位；
    // y 仍保持在指针上方，不遮 X 轴
    position: (
      point: [number, number],
      params: any,
      _dom: unknown,
      _rect: unknown,
      size: { contentSize: [number, number]; viewSize: [number, number] },
    ) => {
      const [w, h] = size.contentSize
      const [vw, vh] = size.viewSize
      const n = (data.value?.byDay ?? []).length || 1
      const idx = Array.isArray(params) ? (params[0]?.dataIndex ?? 0) : 0
      const band = (vw - GRID_LEFT - GRID_RIGHT) / n
      const cx = GRID_LEFT + band * (idx + 0.5)
      return [
        Math.max(4, Math.min(cx - w / 2, vw - w - 4)),
        Math.max(4, Math.min(point[1] - h - 14, vh - h - 4)),
      ]
    },
    // 全 0 日不再飘两行 0：明确告知当日无数据
    formatter: (params: unknown) => {
      const list = (Array.isArray(params) ? params : [params]) as Array<{
        axisValueLabel?: string
        axisValue?: string
        marker?: string
        seriesName?: string
        value?: unknown
      }>
      const title = list[0]?.axisValueLabel ?? list[0]?.axisValue ?? ''
      if (list.every((p) => !Number(p.value))) {
        return `${title}<br/><span style="color:#86909c">无请求数据</span>`
      }
      return (
        title +
        list
          .map(
            (p) =>
              `<br/>${p.marker ?? ''} ${p.seriesName ?? ''}` +
              `<span style="float:right;margin-left:24px;font-weight:600">${Number(p.value).toLocaleString()}</span>`,
          )
          .join('')
      )
    },
  },
  grid: { left: GRID_LEFT, right: GRID_RIGHT, top: 56, bottom: 28 },
  xAxis: {
    type: 'category',
    data: (data.value?.byDay ?? []).map((t) => t.date.slice(5)),
    axisLabel: { hideOverlap: true, interval: 'auto' },
    // 柱状图用 shadow：0 数据日也有可见的高亮带，悬浮判定区不再"隐形"
    axisPointer: {
      type: 'shadow' as const,
      shadowStyle: { color: 'rgba(109,91,208,0.06)' },
      ...xAxisPointerLabel,
    },
  },
  // 轴名放在轴末端刻度正上方独立带：grid.top 56 - nameGap 12 ≈ y44，
  // 与标题行（y0-18）留出间隔，右对齐贴轴线，不再与标题重叠
  yAxis: {
    type: 'value',
    name: '词元',
    nameLocation: 'end',
    nameGap: 12,
    nameTextStyle: { color: '#86909c', fontSize: 11, align: 'right' },
  },
  series: [
    {
      name: '提示词',
      type: 'bar',
      stack: 'tokens',
      barMaxWidth: 28,
      data: (data.value?.byDay ?? []).map((t) => t.promptTokens),
      itemStyle: { color: '#6d5bd0' },
    },
    {
      name: '生成',
      type: 'bar',
      stack: 'tokens',
      barMaxWidth: 28,
      data: (data.value?.byDay ?? []).map((t) => t.completionTokens),
      itemStyle: { color: '#f79009', borderRadius: [4, 4, 0, 0] },
    },
  ],
}))

onMounted(load)

// 表格布局自愈：数据异步到达 / 路由重新激活后重算列宽，杜绝旧布局态下列重合
const modelTable = ref<TableInstance>()
const userTable = ref<TableInstance>()
const relayout = (): void => {
  void nextTick(() => {
    modelTable.value?.doLayout()
    userTable.value?.doLayout()
  })
}
watch(data, relayout)
onActivated(relayout)
</script>

<template>
  <div v-loading="loading" class="flex flex-col gap-4">
    <PageHead title="词元消耗统计" desc="按日 / 按模型 / 按用户聚合请求日志的词元留痕。">
      <template #actions>
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHead>

    <div class="grid grid-cols-3 gap-3">
      <div v-for="c in cards" :key="c.label" class="rounded-xl border border-line bg-card p-4">
        <div class="text-xs text-ink-3">{{ c.label }}</div>
        <div class="mono mt-1 text-2xl font-semibold text-ink">{{ c.value.toLocaleString() }}</div>
      </div>
    </div>

    <div class="rounded-xl border border-line bg-card p-4">
      <!-- 标题已入图表（与图例同行），容器加高补偿标题行，绘图区高度不变 -->
      <div class="h-80"><BaseChart :option="barOption" /></div>
    </div>

    <div class="grid gap-4 xl:grid-cols-2">
      <div class="rounded-xl border border-line bg-card p-4">
        <div class="mb-2 text-sm font-semibold text-ink">按模型</div>
        <el-table ref="modelTable" :data="data?.byModel ?? []" size="small" empty-text="暂无数据">
          <el-table-column prop="model" label="模型" min-width="160" show-overflow-tooltip />
          <el-table-column label="请求数" min-width="90" align="right">
            <template #default="{ row }"><span class="mono">{{ row.requests }}</span></template>
          </el-table-column>
          <el-table-column label="词元" min-width="110" align="right">
            <template #default="{ row }"><span class="mono font-semibold">{{ row.tokens.toLocaleString() }}</span></template>
          </el-table-column>
        </el-table>
      </div>
      <div class="rounded-xl border border-line bg-card p-4">
        <div class="mb-2 text-sm font-semibold text-ink">按用户 Top 10</div>
        <el-table ref="userTable" :data="data?.byUser ?? []" size="small" empty-text="暂无数据">
          <el-table-column prop="username" label="用户" min-width="120" show-overflow-tooltip />
          <el-table-column label="请求数" min-width="90" align="right">
            <template #default="{ row }"><span class="mono">{{ row.requests }}</span></template>
          </el-table-column>
          <el-table-column label="词元" min-width="110" align="right">
            <template #default="{ row }"><span class="mono font-semibold">{{ row.tokens.toLocaleString() }}</span></template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>