<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { Money, Promotion, RefreshRight, Service, VideoPause } from '@element-plus/icons-vue'

const props = defineProps<{
  streaming: boolean
  disabled?: boolean
  canRegenerate?: boolean
}>()

const emit = defineEmits<{
  (e: 'send', text: string): void
  (e: 'stop'): void
  (e: 'open-refund'): void
  (e: 'open-queue'): void
  (e: 'regenerate'): void
}>()

const MAX = 2000
const text = ref('')
const cooldown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const count = computed(() => text.value.length)
const blocked = computed(() => props.disabled || props.streaming || cooldown.value > 0)

function startCooldown(): void {
  cooldown.value = 3
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function submit(): void {
  const value = text.value.trim()
  if (!value || blocked.value) return
  emit('send', value)
  text.value = ''
  startCooldown()
}

function onKeydown(e: Event | KeyboardEvent): void {
  if (!(e instanceof KeyboardEvent)) return
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    submit()
  }
}
</script>

<template>
  <div class="rounded-xl border border-line bg-card p-3 shadow-[0_-2px_12px_rgba(31,35,41,0.06)]">
    <p class="pb-2 text-xs leading-5 text-ink-3">AI 生成内容仅供参考，退款等资金操作需二次确认</p>
    <el-input
      v-model="text"
      type="textarea"
      :autosize="{ minRows: 2, maxRows: 6 }"
      :maxlength="MAX"
      resize="none"
      :disabled="disabled"
      :placeholder="disabled ? '本次会话已结束，可新建会话' : '输入消息，Enter 发送，Shift+Enter 换行'"
      aria-label="消息输入框"
      @keydown="onKeydown"
    />
    <div class="mt-2 flex flex-wrap items-center gap-1">
      <el-button text size="small" :icon="Money" :disabled="disabled" @click="emit('open-refund')">
        退款申请
      </el-button>
      <el-button text size="small" :icon="Service" :disabled="disabled" @click="emit('open-queue')">
        转人工
      </el-button>
      <el-button
        text
        size="small"
        :icon="RefreshRight"
        :disabled="disabled || streaming || !canRegenerate"
        @click="emit('regenerate')"
      >
        重新生成
      </el-button>
      <span class="mono ml-auto mr-2 text-xs text-ink-3">{{ count }}/{{ MAX }}</span>
      <el-button
        v-if="streaming"
        size="small"
        type="danger"
        plain
        :icon="VideoPause"
        @click="emit('stop')"
      >
        停止
      </el-button>
      <el-button
        v-else
        size="small"
        type="primary"
        :icon="Promotion"
        :disabled="blocked || !text.trim()"
        @click="submit"
      >
        {{ cooldown > 0 ? '请稍候…' : '发送' }}
      </el-button>
    </div>
  </div>
</template>