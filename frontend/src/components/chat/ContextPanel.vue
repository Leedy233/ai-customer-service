<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { DArrowRight } from '@element-plus/icons-vue'
import * as contentApi from '@/api/user/content'
import * as ticketsApi from '@/api/user/tickets'
import { useChatStore } from '@/stores/chat'
import { useConversationStore } from '@/stores/conversation'
import { useUserStore } from '@/stores/user'
import { useWorkbenchStore } from '@/stores/workbench'
import type { TicketVO } from '@/types/admin'
import type { OrderVO } from '@/types/models'
import { formatMoney } from '@/utils/format'
import { fullTime } from '@/utils/conversation-group'

defineProps<{
  inDrawer?: boolean
}>()

const emit = defineEmits<{
  (e: 'collapse'): void
}>()

const user = useUserStore()
const conversations = useConversationStore()
const chat = useChatStore()
const workbench = useWorkbenchStore()

const orders = ref<OrderVO[]>([])
const ordersLoading = ref(true)

const initial = computed(() => (user.profile?.username ?? '?').slice(0, 1).toUpperCase())
const conv = computed(() => conversations.list.find((c) => c.id === conversations.currentId))
const messageCount = computed(() =>
  conversations.currentId ? chat.messages(conversations.currentId).length : 0,
)
const transferred = computed(() => (workbench.queue.active ? '排队中' : '否'))

/** 历史工单：服务端真值（/ai/tickets/mine） */
const tickets = ref<TicketVO[]>([])
const ticketsLoading = ref(true)

const TICKET_LABEL: Record<string, string> = {
  OPEN: '待处理',
  PROCESSING: '处理中',
  CLOSED: '已完成',
  CANCELLED: '已取消',
}

function ticketStampClass(status: string): string {
  if (status === 'CLOSED') return 'text-success'
  if (status === 'OPEN' || status === 'PROCESSING') return 'text-warning'
  return 'text-ink-3'
}

function ticketTime(t: string): string {
  return t.slice(5, 16).replace('T', ' ')
}

function stampClass(status: string): string {
  if (status.includes('已发货') || status.includes('已完成')) return 'text-success'
  if (status.includes('待') || status.includes('退款中')) return 'text-warning'
  return 'text-ink-2'
}

onMounted(async () => {
  // 订单列表服务端真值（GET /ai/orders）
  try {
    orders.value = await contentApi.orders()
  } catch {
    orders.value = []
  }
  ordersLoading.value = false
  try {
    tickets.value = await ticketsApi.mine()
  } catch {
    tickets.value = []
  }
  ticketsLoading.value = false
})
</script>

<template>
  <div class="flex h-full flex-col bg-surface">
    <div class="flex h-12 shrink-0 items-center gap-2 border-b border-line px-4">
      <h2 class="min-w-0 flex-1 truncate text-sm font-semibold text-ink">客户与业务上下文</h2>
      <button
        v-if="!inDrawer"
        class="tap-target flex h-7 w-7 items-center justify-center rounded-md text-ink-3 hover:bg-card hover:text-ink-2"
        aria-label="收起上下文面板"
        @click="emit('collapse')"
      >
        <el-icon :size="14"><DArrowRight /></el-icon>
      </button>
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto p-4">
      <!-- 单面板 + 发丝分隔：结构编码分组，不做同款卡堆叠 -->
      <div class="overflow-hidden rounded-xl border border-line bg-card">
        <!-- 客户 -->
        <section class="p-4">
          <div class="flex items-center gap-3">
            <span
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-brand-50 text-base font-semibold text-brand-600"
            >
              {{ initial }}
            </span>
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span class="truncate text-sm font-medium text-ink">{{ user.profile?.username }}</span>
                <span v-if="(user.profile?.tags ?? []).includes('VIP')" class="stamp text-brand-600">VIP</span>
              </div>
              <div class="mono mt-0.5 truncate text-xs text-ink-3">
                {{ user.profile?.phone ?? '--' }} · {{ user.profile?.email ?? '--' }}
              </div>
              <div v-if="(user.profile?.tags ?? []).length" class="mt-1 flex flex-wrap gap-1">
                <span
                  v-for="t in user.profile?.tags ?? []"
                  :key="t"
                  class="rounded bg-surface px-1.5 py-0.5 text-xs text-ink-3"
                >
                  {{ t }}
                </span>
              </div>
            </div>
          </div>
        </section>

        <!-- 最近订单：面单行 -->
        <section class="border-t border-line p-4">
          <h3 class="text-base font-semibold leading-6 text-ink">最近订单</h3>
          <div v-if="ordersLoading" class="mt-3 space-y-2">
            <div v-for="i in 3" :key="i" class="skeleton h-12 w-full" />
          </div>
          <ul v-else class="m-0 mt-3 list-none space-y-3 p-0">
            <li v-for="o in orders" :key="o.orderNo">
              <div class="flex items-center gap-2">
                <span class="mono min-w-0 flex-1 truncate text-xs text-ink-2">{{ o.orderNo }}</span>
                <span class="stamp" :class="stampClass(o.status)">{{ o.status }}</span>
              </div>
              <div class="mt-1 flex items-baseline gap-2">
                <span class="min-w-0 flex-1 truncate text-xs text-ink-3">{{ o.productName }}</span>
                <span class="mono shrink-0 text-sm font-semibold text-ink">{{ formatMoney(o.amount) }}</span>
              </div>
            </li>
            <li
              v-if="orders.length < 3"
              class="border-t border-dashed border-line pt-2 text-center text-xs text-ink-3"
            >
              暂无更多订单
            </li>
          </ul>
        </section>

        <!-- 历史工单 -->
        <section class="border-t border-line p-4">
          <h3 class="text-base font-semibold leading-6 text-ink">历史工单</h3>
          <div v-if="ticketsLoading" class="mt-3 space-y-2">
            <div v-for="i in 2" :key="i" class="skeleton h-6 w-full" />
          </div>
          <ul v-else-if="tickets.length" class="m-0 mt-3 list-none space-y-2 p-0">
            <li v-for="t in tickets.slice(0, 5)" :key="t.id" class="flex items-center gap-2">
              <span class="mono min-w-0 flex-1 truncate text-xs text-ink-2" :title="t.ticketNo">{{ t.ticketNo }}</span>
              <span class="stamp" :class="ticketStampClass(t.status)">{{ TICKET_LABEL[t.status] ?? t.status }}</span>
              <span class="mono shrink-0 text-xs text-ink-3">{{ ticketTime(t.createdAt) }}</span>
            </li>
          </ul>
          <p v-else class="mt-3 text-xs text-ink-3">暂无工单记录</p>
        </section>

        <!-- 会话信息 -->
        <section class="border-t border-line p-4">
          <h3 class="text-base font-semibold leading-6 text-ink">会话信息</h3>
          <dl class="mt-3 space-y-2 text-xs">
            <div class="flex justify-between gap-2">
              <dt class="text-ink-3">渠道</dt>
              <dd class="text-ink-2">Web</dd>
            </div>
            <div class="flex justify-between gap-2">
              <dt class="text-ink-3">开始时间</dt>
              <dd class="mono text-ink-2">{{ conv ? fullTime(conv.createdAt) : '--' }}</dd>
            </div>
            <div class="flex justify-between gap-2">
              <dt class="text-ink-3">消息数</dt>
              <dd class="mono text-ink-2">{{ messageCount }}</dd>
            </div>
            <div class="flex justify-between gap-2">
              <dt class="text-ink-3">是否已转人工</dt>
              <dd class="text-ink-2">{{ transferred }}</dd>
            </div>
          </dl>
        </section>
      </div>

      <p class="mt-3 text-xs leading-5 text-ink-3">客户资料（脱敏）、订单与工单均为服务端真值。</p>
    </div>
  </div>
</template>