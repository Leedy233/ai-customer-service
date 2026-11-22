<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Box, Money } from '@element-plus/icons-vue'
import * as contentApi from '@/api/user/content'
import type { OrderCardData, OrderVO } from '@/types/models'
import { formatMoney } from '@/utils/format'

/**
 * 订单卡片：模型文本仅用于触发（提取 orderNo），
 * 展示字段一律以 GET /ai/orders/{orderNo} 的 DB 真值渲染，杜绝模型幻觉金额/日期。
 */
const props = defineProps<{
  card: OrderCardData
}>()

const emit = defineEmits<{
  (e: 'apply-refund', orderNo: string): void
}>()

const auth = ref<OrderVO | null>(null)
const failed = ref(false)

function stampTone(status: string): string {
  if (status.includes('已发货') || status.includes('已完成')) return 'text-success'
  if (status.includes('待') || status.includes('退款中')) return 'text-warning'
  return 'text-ink-2'
}

onMounted(async () => {
  try {
    auth.value = await contentApi.order(props.card.orderNo)
  } catch {
    failed.value = true
  }
})
</script>

<template>
  <div class="mt-3 rounded-xl border border-line bg-card p-4">
    <div class="flex items-center gap-3">
      <span class="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
        <el-icon :size="18"><Box /></el-icon>
      </span>
      <div class="min-w-0 flex-1">
        <div class="text-sm font-semibold text-ink">订单卡片</div>
        <div class="mono truncate text-xs text-ink-3">订单号 {{ card.orderNo }}</div>
      </div>
      <span v-if="auth?.status" class="stamp" :class="stampTone(auth.status)">{{ auth.status }}</span>
    </div>

    <el-alert
      v-if="failed"
      class="mt-3"
      type="warning"
      :closable="false"
      title="权威订单数据加载失败，仅显示订单号"
    />

    <div v-else class="receipt-perf mt-3 grid grid-cols-2 gap-x-4 gap-y-2 pt-3 text-sm">
      <div class="col-span-2">
        <span class="text-xs text-ink-3">商品</span>
        <div class="mt-0.5 text-ink-2">{{ auth?.productName ?? '--' }}</div>
      </div>
      <div>
        <span class="text-xs text-ink-3">金额</span>
        <div class="mono mt-0.5 font-semibold text-brand-600">{{ formatMoney(auth?.amount) }}</div>
      </div>
      <div>
        <span class="text-xs text-ink-3">状态</span>
        <div class="mt-0.5 text-ink-2">{{ auth?.status ?? '--' }}</div>
      </div>
    </div>

    <div class="mt-3 flex justify-end">
      <el-button size="small" round :icon="Money" @click="emit('apply-refund', card.orderNo)">
        申请退款
      </el-button>
    </div>
  </div>
</template>