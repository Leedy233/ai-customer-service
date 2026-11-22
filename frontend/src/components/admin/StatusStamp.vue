<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status: string
}>()

const LABELS: Record<string, string> = {
  OPEN: '待处理',
  PROCESSING: '处理中',
  CLOSED: '已完成',
  CANCELLED: '已取消',
  PENDING_CONFIRM: '待确认',
  REFUNDED: '已执行',
  COMPLETED: '已完结',
  REJECTED: '已驳回',
  CLASSPATH: '内置',
  ADMIN: '管理端',
  TRANSFER: '转人工',
  END: '结束回执',
  UP: 'UP',
  DOWN: 'DOWN',
  DEGRADED: 'DEGRADED',
}

const SUCCESS = new Set(['已发货', '已完成', 'CLOSED', 'COMPLETED', 'REFUNDED', 'UP', '已退款'])
const WARNING = new Set(['待发货', 'OPEN', 'PROCESSING', 'PENDING_CONFIRM', '退款中', '待确认'])
const DANGER = new Set(['REJECTED', 'CANCELLED', 'DOWN', 'DEGRADED'])

const label = computed(() => LABELS[props.status] ?? props.status)
const color = computed(() => {
  if (SUCCESS.has(props.status)) return 'text-success'
  if (WARNING.has(props.status)) return 'text-warning'
  if (DANGER.has(props.status)) return 'text-danger'
  return 'text-ink-2'
})
</script>

<template>
  <span class="stamp" :class="color">{{ label }}</span>
</template>