<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { notifyError } from '@/api/http'
import * as ticketsApi from '@/api/user/tickets'
import { useChatStore } from '@/stores/chat'
import { useConversationStore } from '@/stores/conversation'
import { useWorkbenchStore, type EndedInfo } from '@/stores/workbench'

const props = defineProps<{
  modelValue: boolean
  convId: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'ended', info: EndedInfo): void
}>()

const chat = useChatStore()
const conversations = useConversationStore()
const workbench = useWorkbenchStore()

const step = ref<'summary' | 'rate' | 'done'>('summary')
const resolved = ref(true)
const rating = ref(5)
const feedback = ref('')
const info = ref<EndedInfo | null>(null)

const category = computed(() => {
  const first = chat.messages(props.convId).find((m) => m.role === 'user')?.content ?? ''
  if (/物流|快递|到哪|发货/.test(first)) return '物流查询'
  if (/退款|退货/.test(first)) return '退款售后'
  if (/订单/.test(first)) return '订单咨询'
  if (/会员|优惠|折扣/.test(first)) return '会员权益'
  if (/发票/.test(first)) return '发票服务'
  return '其他咨询'
})

function durationText(ms: number): string {
  const min = Math.floor(ms / 60000)
  const sec = Math.floor((ms % 60000) / 1000)
  return min > 0 ? `${min} 分 ${sec} 秒` : `${sec} 秒`
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    step.value = 'summary'
    resolved.value = true
    rating.value = 5
    feedback.value = ''
    info.value = null
    const conv = conversations.list.find((c) => c.id === props.convId)
    const now = Date.now()
    const firstUser = chat.messages(props.convId).find((m) => m.role === 'user')?.content ?? ''
    try {
      // 工单真实持久化：服务端生成工单号（GD+日期+序号）
      const t = await ticketsApi.create({
        conversationId: props.convId,
        kind: 'END',
        category: category.value,
        summary: firstUser.slice(0, 200),
      })
      info.value = {
        ticketNo: t.ticketNo,
        ticketId: t.id,
        closedAt: now,
        category: category.value,
        resolved: true,
        durationMs: conv ? now - conv.createdAt : 0,
        messageCount: chat.messages(props.convId).length,
      }
    } catch (e) {
      notifyError(e)
      emit('update:modelValue', false)
    }
  },
)

function toRate(): void {
  if (info.value) info.value.resolved = resolved.value
  step.value = 'rate'
}

async function submit(): Promise<void> {
  if (!info.value) return
  if (info.value.ticketId != null) {
    try {
      await ticketsApi.rate(info.value.ticketId, rating.value, feedback.value.trim(), resolved.value)
    } catch (e) {
      notifyError(e)
    }
  }
  workbench.endSession(props.convId, info.value)
  workbench.rateSession(props.convId, rating.value, feedback.value.trim())
  emit('ended', info.value)
  step.value = 'done'
}

async function skip(): Promise<void> {
  if (!info.value) return
  if (info.value.ticketId != null) {
    try {
      await ticketsApi.resolve(info.value.ticketId, resolved.value)
    } catch (e) {
      notifyError(e)
    }
  }
  workbench.endSession(props.convId, info.value)
  emit('ended', info.value)
  step.value = 'done'
}

function close(): void {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="step === 'done' ? '反馈已提交' : '结束会话'"
    width="min(480px, calc(100vw - 32px))"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <!-- 摘要：服务回执样式 -->
    <div v-if="step === 'summary' && info" class="space-y-4">
      <div class="receipt p-4">
        <div class="flex items-center gap-2">
          <span class="text-xs text-ink-3">工单号</span>
          <span class="mono text-sm font-semibold text-ink">{{ info.ticketNo }}</span>
          <span class="stamp ml-auto text-success">待评价</span>
        </div>
        <dl class="receipt-perf mt-3 grid grid-cols-2 gap-x-4 gap-y-2 pt-3 text-xs">
          <div>
            <dt class="text-ink-3">问题分类</dt>
            <dd class="mt-0.5 text-ink-2">{{ info.category }}</dd>
          </div>
          <div>
            <dt class="text-ink-3">处理时长</dt>
            <dd class="mono mt-0.5 text-ink-2">{{ durationText(info.durationMs) }}</dd>
          </div>
          <div>
            <dt class="text-ink-3">消息数</dt>
            <dd class="mono mt-0.5 text-ink-2">{{ info.messageCount }}</dd>
          </div>
          <div>
            <dt class="text-ink-3">是否已解决</dt>
            <dd class="mt-0.5">
              <el-radio-group v-model="resolved" size="small">
                <el-radio-button :value="true">已解决</el-radio-button>
                <el-radio-button :value="false">未解决</el-radio-button>
              </el-radio-group>
            </dd>
          </div>
        </dl>
      </div>
      <p class="text-xs leading-5 text-ink-3">结束后本次会话将关闭输入，可随时新建会话继续咨询。</p>
    </div>

    <!-- 满意度 -->
    <div v-else-if="step === 'rate'" class="space-y-4">
      <div class="text-center">
        <p class="text-sm text-ink-2">本次服务你还满意吗？</p>
        <el-rate v-model="rating" class="mt-2 justify-center" aria-label="满意度评分" />
      </div>
      <el-input
        v-model="feedback"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="还有什么想告诉我们的（可选）"
        aria-label="补充反馈"
      />
    </div>

    <!-- 完成 -->
    <div v-else class="py-6 text-center">
      <p class="text-base font-semibold text-ink">感谢反馈</p>
      <p class="mt-2 text-sm text-ink-3">你的评价会帮助我们把服务做得更好。</p>
    </div>

    <template #footer>
      <template v-if="step === 'summary'">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" @click="toRate">下一步：评价服务</el-button>
      </template>
      <template v-else-if="step === 'rate'">
        <el-button text @click="skip">跳过评价</el-button>
        <el-button type="primary" @click="submit">提交反馈</el-button>
      </template>
      <el-button v-else type="primary" @click="close">完成</el-button>
    </template>
  </el-dialog>
</template>