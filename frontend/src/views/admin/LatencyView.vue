<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { notifyError } from '@/api/http'
import * as metrics from '@/api/admin/metrics'
import BaseChart from '@/components/charts/BaseChart.vue'
import PageHead from '@/components/admin/PageHead.vue'
import type { LatencyStats } from '@/types/admin'
import { fmtTime } from '@/utils/admin-format'

const router = useRouter()
const days = ref<7 | 30>(7)
const loading = ref(false)
const data = ref<LatencyStats | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    data.value = await metrics.latency(days.value)
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}

const cards = computed(() => {
  const o = data.value?.overall
  if (!o) return []
  return [
    { label: '平均(ms)', value: o.avgMs },
    { label: 'P50(ms)', value: o.p50Ms },
    { label: 'P95(ms)', value: o.p95Ms },
    { label: '最大(ms)', value: o.maxMs },
  ]
})

const lineOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['平均', 'P95'] },
  grid: { left: 56, right: 24, top: 40, bottom: 28 },
  xAxis: {
    type: 'category',
    data: (data.value?.byDay ?? []).map((t) => t.date.slice(5)),
    axisLabel: { hideOverlap: true },
  },
  yAxis: { type: 'value', name: 'ms' },
  series: [
    {
      name: '平均',
      type: 'line',
      smooth: true,
      data: (data.value?.byDay ?? []).map((t) => t.avgMs),
      itemStyle: { color: '#6d5bd0' },
    },
    {
      name: 'P95',
      type: 'line',
      smooth: true,
      data: (data.value?.byDay ?? []).map((t) => t.p95Ms),
      itemStyle: { color: '#f79009' },
    },
  ],
}))

function toAudit(convId: string): void {
  void router.push({ path: '/admin/audit', query: { conv: convId } })
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="flex flex-col gap-4">
    <PageHead title="模型响应时间" desc="平均 / P50 / P95 / 最大耗时分布；百分位由 Java 侧对留痕排序计算。">
      <template #actions>
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHead>

    <div class="grid grid-cols-2 gap-3 md:grid-cols-4">
      <div v-for="c in cards" :key="c.label" class="rounded-xl border border-line bg-card p-4">
        <div class="text-xs text-ink-3">{{ c.label }}</div>
        <div class="mono mt-1 text-2xl font-semibold text-ink">{{ c.value.toLocaleString() }}</div>
      </div>
    </div>

    <div class="rounded-xl border border-line bg-card p-4">
      <div class="mb-2 text-sm font-semibold text-ink">每日平均 / P95 耗时</div>
      <div class="h-72"><BaseChart :option="lineOption" /></div>
    </div>

    <div class="grid gap-4 xl:grid-cols-2">
      <div class="rounded-xl border border-line bg-card p-4">
        <div class="mb-2 text-sm font-semibold text-ink">按模型</div>
        <el-table :data="data?.byModel ?? []" size="small" empty-text="暂无数据">
          <el-table-column prop="model" label="模型" min-width="120" />
          <el-table-column label="请求" width="70" align="right">
            <template #default="{ row }"><span class="mono">{{ row.requests }}</span></template>
          </el-table-column>
          <el-table-column label="平均" width="80" align="right">
            <template #default="{ row }"><span class="mono">{{ row.avgMs }}</span></template>
          </el-table-column>
          <el-table-column label="P95" width="80" align="right">
            <template #default="{ row }"><span class="mono">{{ row.p95Ms }}</span></template>
          </el-table-column>
        </el-table>
      </div>
      <div class="rounded-xl border border-line bg-card p-4">
        <div class="mb-2 text-sm font-semibold text-ink">最慢请求 Top 10</div>
        <el-table :data="data?.slowest ?? []" size="small" empty-text="暂无数据">
          <el-table-column label="耗时(ms)" width="90" align="right">
            <template #default="{ row }"><span class="mono font-semibold text-warning">{{ row.latencyMs }}</span></template>
          </el-table-column>
          <el-table-column prop="username" label="用户" min-width="80" />
          <el-table-column label="时间" min-width="130">
            <template #default="{ row }"><span class="mono text-ink-3">{{ fmtTime(row.createdAt) }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="toAudit(row.conversationId)">会话</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>