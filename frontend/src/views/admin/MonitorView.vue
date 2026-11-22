<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { notifyError } from '@/api/http'
import * as metrics from '@/api/admin/metrics'
import PageHead from '@/components/admin/PageHead.vue'
import StatusStamp from '@/components/admin/StatusStamp.vue'
import type { SystemInfo } from '@/types/admin'
import { fmtTime, fmtUptime } from '@/utils/admin-format'

const loading = ref(false)
const info = ref<SystemInfo | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    info.value = await metrics.system()
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}

const heapPercent = computed(() => {
  const j = info.value?.jvm
  if (!j || !j.heapMaxMb) return 0
  return Math.round((j.heapUsedMb / j.heapMaxMb) * 100)
})

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="flex flex-col gap-4">
    <PageHead title="系统运行监控" desc="JVM / 数据库 / 向量库 / 限流窗口 / actuator health 实时快照。">
      <template #actions>
        <StatusStamp v-if="info" :status="info.status" />
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHead>

    <template v-if="info">
      <div class="grid gap-4 xl:grid-cols-2">
        <div class="rounded-xl border border-line bg-card p-4">
          <div class="mb-3 text-sm font-semibold text-ink">JVM</div>
          <div class="text-xs text-ink-3">堆内存 {{ info.jvm.heapUsedMb }} / {{ info.jvm.heapMaxMb }} MB</div>
          <el-progress
            :percentage="heapPercent"
            :stroke-width="8"
            :color="heapPercent > 85 ? '#f04438' : '#6d5bd0'"
            class="mt-2"
          />
          <dl class="mt-3 space-y-2 text-xs">
            <div class="flex justify-between"><dt class="text-ink-3">非堆</dt><dd class="mono text-ink-2">{{ info.jvm.nonHeapUsedMb }} MB</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">线程数</dt><dd class="mono text-ink-2">{{ info.jvm.threads }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">运行时长</dt><dd class="mono text-ink-2">{{ fmtUptime(info.jvm.uptimeMs) }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">启动时间</dt><dd class="mono text-ink-2">{{ fmtTime(info.jvm.startedAt) }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">Java / PID</dt><dd class="mono text-ink-2">{{ info.jvm.javaVersion }} / {{ info.jvm.pid }}</dd></div>
          </dl>
        </div>

        <div class="rounded-xl border border-line bg-card p-4">
          <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-ink">
            数据库 <StatusStamp :status="info.db.status" />
            <span class="mono ml-auto text-xs font-normal text-ink-3">ping {{ info.db.pingMs }} ms</span>
          </div>
          <dl class="space-y-2 text-xs">
            <div v-for="(n, t) in info.db.tables" :key="t" class="flex justify-between">
              <dt class="mono text-ink-3">{{ t }}</dt>
              <dd class="mono text-ink-2">{{ n.toLocaleString() }}</dd>
            </div>
          </dl>
        </div>

        <div class="rounded-xl border border-line bg-card p-4">
          <div class="mb-3 text-sm font-semibold text-ink">RAG 向量库</div>
          <dl class="space-y-2 text-xs">
            <div class="flex justify-between"><dt class="text-ink-3">知识文档</dt><dd class="mono text-ink-2">{{ info.rag.docs }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">启用中</dt><dd class="mono text-ink-2">{{ info.rag.enabledDocs }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">chunk 总数</dt><dd class="mono text-ink-2">{{ info.rag.chunks }}</dd></div>
          </dl>
          <div class="mt-4 mb-2 text-sm font-semibold text-ink">限流窗口</div>
          <dl class="space-y-2 text-xs">
            <div class="flex justify-between"><dt class="text-ink-3">上限</dt><dd class="mono text-ink-2">{{ info.rateLimit.limitPerMinute }} 次/分/IP</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">跟踪 IP</dt><dd class="mono text-ink-2">{{ info.rateLimit.trackedIps }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">近一分钟命中</dt><dd class="mono text-ink-2">{{ info.rateLimit.hitsLastMinute }}</dd></div>
          </dl>
        </div>

        <div class="rounded-xl border border-line bg-card p-4">
          <div class="mb-3 flex items-center gap-2 text-sm font-semibold text-ink">
            Actuator Health <StatusStamp :status="info.health.status" />
          </div>
          <ul class="m-0 list-none space-y-2 p-0 text-xs">
            <li v-for="(s, name) in info.health.components" :key="name" class="flex items-center justify-between">
              <span class="mono text-ink-3">{{ name }}</span>
              <StatusStamp :status="s" />
            </li>
          </ul>
          <p class="mt-4 border-t border-dashed border-line pt-3 text-xs leading-5 text-ink-3">
            {{ info.redisNote }}
          </p>
          <p class="mono mt-2 text-xs text-ink-3">快照时间 {{ fmtTime(info.checkedAt) }}</p>
        </div>
      </div>
    </template>
  </div>
</template>