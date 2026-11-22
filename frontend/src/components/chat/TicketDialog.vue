<script setup lang="ts">
import { computed } from 'vue'
import { useWorkbenchStore } from '@/stores/workbench'
import { fullTime } from '@/utils/conversation-group'
import EmptyState from '@/components/common/EmptyState.vue'

const props = defineProps<{
  modelValue: boolean
  convId: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'end-session'): void
}>()

const workbench = useWorkbenchStore()
const ticket = computed(() => workbench.ended[props.convId] ?? null)

function durationText(ms: number): string {
  const min = Math.floor(ms / 60000)
  const sec = Math.floor((ms % 60000) / 1000)
  return min > 0 ? `${min} 分 ${sec} 秒` : `${sec} 秒`
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="工单详情"
    width="min(440px, calc(100vw - 32px))"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div v-if="ticket" class="receipt p-4">
      <div class="flex items-center gap-2">
        <span class="mono min-w-0 flex-1 truncate text-sm font-semibold text-ink">
          {{ ticket.ticketNo }}
        </span>
        <span class="stamp text-success">已完成</span>
      </div>
      <dl class="receipt-perf mt-3 grid grid-cols-2 gap-x-4 gap-y-2 pt-3 text-xs">
        <div>
          <dt class="text-ink-3">问题分类</dt>
          <dd class="mt-0.5 text-ink-2">{{ ticket.category }}</dd>
        </div>
        <div>
          <dt class="text-ink-3">是否已解决</dt>
          <dd class="mt-0.5 text-ink-2">{{ ticket.resolved ? '已解决' : '未解决' }}</dd>
        </div>
        <div>
          <dt class="text-ink-3">处理时长</dt>
          <dd class="mono mt-0.5 text-ink-2">{{ durationText(ticket.durationMs) }}</dd>
        </div>
        <div>
          <dt class="text-ink-3">消息数</dt>
          <dd class="mono mt-0.5 text-ink-2">{{ ticket.messageCount }}</dd>
        </div>
        <div class="col-span-2">
          <dt class="text-ink-3">结束时间</dt>
          <dd class="mono mt-0.5 text-ink-2">{{ fullTime(ticket.closedAt) }}</dd>
        </div>
        <div v-if="ticket.rating" class="col-span-2">
          <dt class="text-ink-3">满意度</dt>
          <dd class="mt-0.5 text-ink-2">{{ ticket.rating }} 星</dd>
        </div>
      </dl>
    </div>
    <EmptyState
      v-else
      title="暂无工单"
      description="结束会话后会自动生成服务工单，便于后续跟进与回访"
    >
      <el-button type="primary" size="small" @click="emit('end-session')">结束会话并生成工单</el-button>
    </EmptyState>
  </el-dialog>
</template>