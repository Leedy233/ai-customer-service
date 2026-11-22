<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Box,
  MoreFilled,
  Tickets,
  User,
  Van,
} from '@element-plus/icons-vue'
import { notifyError } from '@/api/http'
import * as contentApi from '@/api/user/content'
import * as ticketsApi from '@/api/user/tickets'
import { useChatStore } from '@/stores/chat'
import { useConversationStore } from '@/stores/conversation'
import { useWorkbenchStore } from '@/stores/workbench'
import { useChatStream } from '@/composables/useChatStream'
import type { Citation, MessageVM, OrderVO } from '@/types/models'
import { ApiError } from '@/utils/errors'
import { mapServerMessages } from '@/utils/server-messages'
import { formatMoney } from '@/utils/format'
import ChatBubble from '@/components/chat/ChatBubble.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import RefundDialog from '@/components/chat/RefundDialog.vue'
import TicketDialog from '@/components/chat/TicketDialog.vue'
import EndSessionDialog from '@/components/chat/EndSessionDialog.vue'
import SourceDrawer from '@/components/chat/SourceDrawer.vue'

const route = useRoute()
const router = useRouter()
const chat = useChatStore()
const conversations = useConversationStore()
const workbench = useWorkbenchStore()
const { send, stop } = useChatStream()

const convId = ref('')
const scrollRef = ref<HTMLElement>()
const nearBottom = ref(true)
const hydrating = ref(false)
const refundOpen = ref(false)
const ticketOpen = ref(false)
const endOpen = ref(false)
const sourceOpen = ref(false)
const sourceCitation = ref<Citation | null>(null)
const sourceSources = ref<Citation[]>([])
const sourceLive = ref(false)
const refundOrderNo = ref('')
const recentOrder = ref<OrderVO | null>(null)

const messages = computed<MessageVM[]>(() => (convId.value ? chat.messages(convId.value) : []))
const streaming = computed(() => chat.isStreaming(convId.value))
const ended = computed(() => (convId.value ? workbench.ended[convId.value] : undefined))
const convTitle = computed(
  () => conversations.list.find((c) => c.id === convId.value)?.title ?? '新会话',
)
const statusText = computed(() =>
  ended.value ? '已结束' : workbench.queue.active ? '转人工排队中' : '在线',
)
const statusClass = computed(() =>
  ended.value ? 'text-ink-3' : workbench.queue.active ? 'text-warning' : 'text-success',
)
const canRegenerate = computed(() => messages.value.some((m) => m.role === 'user'))
const showJump = computed(() => !nearBottom.value && messages.value.length > 0)
const deliveryEstimate = computed(() => {
  const o = recentOrder.value
  if (o?.status.includes('已发货')) {
    // 真值：订单 eta（发货时写入）；旧数据无 eta 回退原文案
    return o.eta ? `预计 ${o.eta.slice(0, 10)} 送达` : '预计明天送达'
  }
  return '按下单顺序发出'
})

function resolveConv(): void {
  const param = route.params.conversationId as string | undefined
  // 加法：URL 参数仅当合并后的注册表存在时才采纳；
  // 已真删除（墓碑）或越权的会话 id 不再 ensure 复活，回退正常选会话并修正 URL
  if (param && conversations.list.some((c) => c.id === param)) {
    conversations.select(param)
    convId.value = param
    return
  }
  if (param) {
    void router.replace({ name: 'Chat' }).catch(() => {})
  }
  if (conversations.currentId) {
    convId.value = conversations.currentId
    return
  }
  const first = conversations.sorted()[0]
  if (first) {
    conversations.select(first.id)
    convId.value = first.id
    return
  }
  convId.value = ''
}

onMounted(async () => {
  conversations.listLoading = true
  try {
    const server = await contentApi.conversations()
    conversations.mergeServer(server)
  } catch (e) {
    if (!(e instanceof ApiError && e.code === 401)) {
      workbench.setBanner('会话列表加载失败，请检查网络后刷新重试')
    }
  } finally {
    conversations.listLoading = false
  }
  resolveConv()
  conversations.purgeEmptyDrafts()
  void hydrate()
  try {
    recentOrder.value = await contentApi.order('10001')
  } catch {
    recentOrder.value = null
  }
})

watch(
  () => route.params.conversationId,
  () => {
    resolveConv()
    void hydrate()
  },
)

/** 空桶时从服务端回填历史（含工具动作条）；限流时退避重试一次 */
async function hydrate(): Promise<void> {
  const id = convId.value
  if (!id || chat.messages(id).length > 0) return
  hydrating.value = true
  try {
    let ms
    try {
      ms = await contentApi.messages(id)
    } catch (e) {
      if (e instanceof ApiError && e.code === 429) {
        await new Promise((r) => setTimeout(r, 3000))
        ms = await contentApi.messages(id)
      } else {
        throw e
      }
    }
    if (chat.messages(id).length === 0) chat.loadHistory(id, mapServerMessages(ms))
  } catch (e) {
    if (!(e instanceof ApiError && (e.code === 403 || e.code === 404))) {
      if (e instanceof ApiError && e.code === 0) {
        workbench.setBanner('历史消息加载失败，请检查网络后重试')
      } else {
        notifyError(e)
      }
    }
  } finally {
    hydrating.value = false
  }
}

function onScroll(): void {
  const el = scrollRef.value
  if (!el) return
  nearBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function jumpToBottom(): void {
  const el = scrollRef.value
  if (el) el.scrollTop = el.scrollHeight
  nearBottom.value = true
}

watch(
  messages,
  () => {
    if (!nearBottom.value) return
    void nextTick(() => {
      const el = scrollRef.value
      if (el) el.scrollTop = el.scrollHeight
    })
  },
  { deep: true },
)

onUnmounted(() => {
  if (streaming.value) stop()
})

function ensureConv(): string {
  if (convId.value) return convId.value
  const conv = conversations.create()
  convId.value = conv.id
  void router.push(`/chat/${conv.id}`)
  return conv.id
}

function onSend(text: string): void {
  if (ended.value) return
  void send(ensureConv(), text)
}

function onRetry(vm: MessageVM): void {
  if (vm.role === 'user') {
    onSend(vm.content)
    return
  }
  const list = messages.value
  const idx = list.findIndex((m) => m.id === vm.id)
  for (let i = idx - 1; i >= 0; i--) {
    if (list[i].role === 'user') {
      onSend(list[i].content)
      return
    }
  }
}

function onRegenerate(): void {
  const list = messages.value
  for (let i = list.length - 1; i >= 0; i--) {
    if (list[i].role === 'user') {
      onSend(list[i].content)
      return
    }
  }
}

function openRefund(orderNo = ''): void {
  refundOrderNo.value = orderNo
  refundOpen.value = true
}

function onConfirmed(): void {
  chat.pushNotice(convId.value, '退款已确认，订单进入「退款中」')
}

async function openQueue(): Promise<void> {
  if (ended.value || workbench.queue.active) return
  const conv = ensureConv()
  try {
    const t = await ticketsApi.create({ conversationId: conv, kind: 'TRANSFER' })
    await workbench.startQueue({ id: t.id, no: t.ticketNo }, conv)
    chat.pushNotice(conv, `已加入人工客服排队（工单 ${t.ticketNo}），可随时取消`)
  } catch (e) {
    notifyError(e)
  }
}

async function cancelQueue(): Promise<void> {
  const ticketId = workbench.queue.ticketId
  await workbench.cancelQueue()
  if (ticketId != null) {
    try {
      await ticketsApi.cancel(ticketId)
    } catch (e) {
      notifyError(e)
    }
  }
  chat.pushNotice(convId.value, '已取消人工排队，继续由智能客服为你服务')
}

function openSource(c: Citation): void {
  sourceCitation.value = c
  // 真溯源列表：取该引用所属消息的 sources；无则单条兜底
  const owner = messages.value.find((m) => m.sources?.some((s) => s.doc === c.doc && s.excerpt === c.excerpt))
  sourceSources.value = owner?.sources ?? []
  sourceLive.value = !!owner?.sources?.length
  sourceOpen.value = true
}

async function clearContext(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '清空上下文将开启一个新会话，当前会话仍保留在列表中。',
      '清空上下文',
      { confirmButtonText: '开启新会话', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  const conv = conversations.create()
  void router.push(`/chat/${conv.id}`)
}

function onEnded(): void {
  chat.pushNotice(convId.value, '本次会话已结束，感谢你的咨询')
}
</script>

<template>
  <div class="flex h-full min-w-0 flex-col bg-card">
    <!-- 顶部信息条 -->
    <div class="flex h-12 shrink-0 items-center gap-3 border-b border-line px-4">
      <span class="min-w-0 truncate text-sm font-medium text-ink">{{ convTitle }}</span>
      <span class="hidden items-center gap-1.5 text-xs text-ink-3 sm:flex">
        <span class="h-1.5 w-1.5 rounded-full bg-success" />
        AI 助手 · 可转人工
      </span>
      <div class="ml-auto flex items-center gap-1">
        <span class="mr-1 text-xs" :class="statusClass">{{ statusText }}</span>
        <button
          class="tap-target flex h-8 w-8 items-center justify-center rounded-lg text-ink-2 hover:bg-surface min-[1200px]:hidden"
          aria-label="打开客户信息"
          @click="workbench.rightDrawerOpen = true"
        >
          <el-icon :size="16"><User /></el-icon>
        </button>
        <button
          class="tap-target flex h-8 w-8 items-center justify-center rounded-lg text-ink-2 hover:bg-surface"
          aria-label="工单详情"
          @click="ticketOpen = true"
        >
          <el-icon :size="16"><Tickets /></el-icon>
        </button>
        <el-dropdown trigger="click">
          <button
            class="tap-target flex h-8 w-8 items-center justify-center rounded-lg text-ink-2 hover:bg-surface"
            aria-label="更多操作"
          >
            <el-icon :size="16"><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :disabled="!!ended" @click="endOpen = true">
                结束会话
              </el-dropdown-item>
              <el-dropdown-item @click="clearContext">清空上下文</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 网络提示条 -->
    <el-alert
      v-if="workbench.banner"
      class="mx-4 mt-2"
      type="warning"
      :title="workbench.banner"
      show-icon
      @close="workbench.clearBanner()"
    />

    <!-- 消息区 -->
    <div class="relative min-h-0 flex-1">
      <div
        ref="scrollRef"
        class="h-full overflow-y-auto"
        role="log"
        aria-live="polite"
        aria-label="对话消息"
        @scroll="onScroll"
      >
        <div class="mx-auto w-full max-w-3xl space-y-4 px-4 py-6 md:px-8">
          <!-- 加载中骨架 -->
          <div v-if="hydrating" class="space-y-4" aria-label="消息加载中">
            <div class="flex justify-end"><div class="skeleton h-10 w-2/5" /></div>
            <div class="flex justify-start"><div class="skeleton h-20 w-3/5" /></div>
            <div class="flex justify-end"><div class="skeleton h-10 w-1/3" /></div>
            <div class="flex justify-start"><div class="skeleton h-16 w-1/2" /></div>
          </div>

          <!-- 空态 -->
          <div v-else-if="messages.length === 0" class="pt-10">
            <h1 class="display">你好，我是你的智能客服</h1>
            <p class="mt-2 text-sm leading-6 text-ink-3">订单、物流、退款与会员问题，问我即可。</p>

            <div v-if="recentOrder" class="receipt mt-6 max-w-md p-4">
              <div class="flex items-center gap-2">
                <el-icon :size="16" class="text-brand-600"><Box /></el-icon>
                <span class="text-xs text-ink-3">最近订单</span>
                <span class="mono ml-auto text-xs text-ink-2">{{ recentOrder.orderNo }}</span>
              </div>
              <div class="receipt-perf mt-3 pt-3">
                <div class="flex items-baseline gap-3">
                  <span class="min-w-0 flex-1 truncate text-sm text-ink">{{ recentOrder.productName }}</span>
                  <span class="mono shrink-0 text-sm font-semibold text-ink">
                    {{ formatMoney(recentOrder.amount) }}
                  </span>
                </div>
                <div class="mt-2 flex items-center gap-2 text-xs text-ink-3">
                  <span
                    class="stamp"
                    :class="
                      recentOrder.status.includes('已发货') || recentOrder.status.includes('已完成')
                        ? 'text-success'
                        : 'text-warning'
                    "
                  >
                    {{ recentOrder.status }}
                  </span>
                  <el-icon :size="14"><Van /></el-icon>
                  <span>{{ deliveryEstimate }}</span>
                </div>
              </div>
            </div>

            <div class="mt-6 flex flex-wrap justify-start gap-2">
              <button
                v-if="!convId"
                class="rounded-lg bg-brand-500 px-4 py-2 text-sm font-medium text-white hover:bg-brand-600"
                @click="ensureConv()"
              >
                新建会话
              </button>
              <button
                class="rounded-lg border border-line bg-card px-4 py-2 text-sm text-ink-2 hover:border-brand-200 hover:text-brand-600"
                @click="onSend('帮我查一下最近的订单')"
              >
                查订单
              </button>
              <button
                class="rounded-lg border border-line bg-card px-4 py-2 text-sm text-ink-2 hover:border-brand-200 hover:text-brand-600"
                @click="onSend('查询订单 10001 的物流')"
              >
                查物流
              </button>
              <button
                class="rounded-lg border border-line bg-card px-4 py-2 text-sm text-ink-2 hover:border-brand-200 hover:text-brand-600"
                @click="onSend('退款政策是什么？')"
              >
                退款政策
              </button>
              <button
                class="rounded-lg border border-line bg-card px-4 py-2 text-sm text-ink-2 hover:border-brand-200 hover:text-brand-600"
                @click="openQueue()"
              >
                转人工
              </button>
            </div>
          </div>

          <ChatBubble
            v-for="vm in messages"
            :key="vm.id"
            :message="vm"
            @apply-refund="openRefund"
            @retry="onRetry(vm)"
            @open-source="openSource"
          />
        </div>
      </div>

      <button
        v-if="showJump"
        class="absolute bottom-4 right-6 flex h-9 items-center gap-1 rounded-full border border-line bg-card px-3 text-xs text-ink-2 shadow-[0_2px_12px_rgba(31,35,41,0.12)] hover:text-brand-600"
        @click="jumpToBottom"
      >
        <el-icon :size="12"><ArrowDown /></el-icon>
        回到底部
      </button>
    </div>

    <!-- 输入区 -->
    <div class="shrink-0 border-t border-line bg-card px-4 py-3 md:px-8">
      <div class="mx-auto w-full max-w-3xl space-y-2">
        <div
          v-if="workbench.queue.active"
          class="receipt flex items-center gap-3 px-3 py-2 text-xs text-ink-2"
          role="status"
        >
          <span
            class="mono flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-line bg-surface text-lg font-semibold text-ink"
          >
            {{ workbench.queue.position }}
          </span>
          <span class="min-w-0 flex-1">
            正在为你转接人工客服，当前排队 {{ workbench.queue.position }} 人
            <span v-if="workbench.queue.etaSeconds > 0" class="ml-1 text-ink-3">
              预计等待 {{ Math.ceil(workbench.queue.etaSeconds / 60) }} 分钟
            </span>
            <span class="mono ml-1 text-ink-3">{{ workbench.queue.ticketNo }}</span>
          </span>
          <button class="shrink-0 text-brand-600 hover:text-brand-700" @click="cancelQueue">
            取消排队
          </button>
        </div>
        <ChatInput
          :streaming="streaming"
          :disabled="!!ended"
          :can-regenerate="canRegenerate"
          @send="onSend"
          @stop="stop"
          @open-refund="openRefund()"
          @open-queue="openQueue"
          @regenerate="onRegenerate"
        />
      </div>
    </div>

    <RefundDialog
      v-model="refundOpen"
      :conv-id="convId"
      :initial-order-no="refundOrderNo"
      @confirmed="onConfirmed"
    />
    <TicketDialog v-model="ticketOpen" :conv-id="convId" @end-session="ticketOpen = false; endOpen = true" />
    <EndSessionDialog v-model="endOpen" :conv-id="convId" @ended="onEnded" />
    <SourceDrawer v-model="sourceOpen" :citation="sourceCitation" :sources="sourceSources" :live="sourceLive" />
  </div>
</template>