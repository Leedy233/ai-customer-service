<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { CircleCheck, Clock, RefreshRight } from '@element-plus/icons-vue'
import type { Citation, MessageVM } from '@/types/models'
import { clockTime, fullTime } from '@/utils/conversation-group'
import OrderCard from '@/components/chat/OrderCard.vue'
import ToolActionBar from '@/components/chat/ToolActionBar.vue'

const props = defineProps<{
  message: MessageVM
}>()

const emit = defineEmits<{
  (e: 'apply-refund', orderNo: string): void
  (e: 'retry'): void
  (e: 'open-source', citation: Citation): void
}>()

marked.setOptions({ breaks: true, gfm: true })

const html = computed(() => {
  if (props.message.role !== 'assistant') return ''
  const raw = marked.parse(props.message.content || '', { async: false }) as string
  return DOMPurify.sanitize(raw)
})
</script>

<template>
  <!-- 用户消息 -->
  <div v-if="message.role === 'user'" class="flex flex-col items-end">
    <div
      class="max-w-[72%] whitespace-pre-wrap rounded-xl rounded-br-sm border border-brand-100 bg-brand-50 px-4 py-2.5 text-sm leading-6 text-ink"
    >
      {{ message.content }}
    </div>
    <div class="mt-1 flex items-center gap-1.5 pr-1 text-xs text-ink-3">
      <span v-if="message.status === 'sending'" class="flex items-center gap-1">
        <el-icon :size="12"><Clock /></el-icon>发送中
      </span>
      <span v-else-if="message.status === 'failed'" class="flex items-center gap-1 text-danger">
        发送失败
        <button class="flex items-center gap-0.5 text-brand-600 hover:text-brand-700" @click="emit('retry')">
          <el-icon :size="12"><RefreshRight /></el-icon>重试
        </button>
      </span>
      <span v-else class="flex items-center gap-1">
        <el-icon :size="12"><CircleCheck /></el-icon>已发送
      </span>
      <time :title="fullTime(message.createdAt)" :datetime="new Date(message.createdAt).toISOString()">
        {{ clockTime(message.createdAt) }}
      </time>
    </div>
  </div>

  <!-- 工具动作条 -->
  <div v-else-if="message.role === 'tool'" class="flex justify-start">
    <ToolActionBar :message="message" />
  </div>

  <!-- 系统提示 -->
  <div v-else-if="message.role === 'notice'" class="flex justify-center">
    <span class="rounded-full bg-surface px-3 py-1 text-xs text-ink-3">{{ message.content }}</span>
  </div>

  <!-- AI 消息 -->
  <div v-else class="flex flex-col items-start">
    <div class="flex max-w-full items-start gap-2.5">
      <span
        class="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-50"
        aria-label="AI 助手"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M7 10.5a5 5 0 0 1 10 0v3.2a2.3 2.3 0 0 1-2.3 2.3H12"
            stroke="#6D5BD0"
            stroke-width="1.6"
            stroke-linecap="round"
          />
          <rect x="5.4" y="10.6" width="2.6" height="4.4" rx="1.3" fill="#6D5BD0" />
          <rect x="16" y="10.6" width="2.6" height="4.4" rx="1.3" fill="#6D5BD0" />
          <circle cx="11.4" cy="16" r="1.2" fill="#5847B8" />
        </svg>
      </span>
      <div
        class="min-w-0 max-w-[72%] rounded-xl rounded-tl-sm border border-line bg-card px-4 py-3"
      >
        <!-- 思考中 -->
        <div v-if="message.streaming && !message.content" class="flex items-center gap-2 py-1">
          <span class="flex items-center gap-1" aria-hidden="true">
            <span class="typing-dot" />
            <span class="typing-dot" />
            <span class="typing-dot" />
          </span>
          <span class="text-xs text-ink-3">正在输入…</span>
        </div>

        <div
          v-else
          class="md-body"
          :class="{ 'stream-cursor': message.streaming }"
          v-html="html"
        />

        <div v-if="message.stopped" class="mt-2">
          <el-tag size="small" type="info" effect="plain" round>已停止生成</el-tag>
        </div>

        <div v-if="message.error" class="mt-2 flex items-center gap-2 text-xs text-danger">
          <span>{{ message.error }}</span>
          <button
            class="flex items-center gap-0.5 text-brand-600 hover:text-brand-700"
            @click="emit('retry')"
          >
            <el-icon :size="12"><RefreshRight /></el-icon>重试
          </button>
        </div>

        <OrderCard
          v-if="message.card"
          :card="message.card"
          @apply-refund="(no: string) => emit('apply-refund', no)"
        />
      </div>
    </div>
    <div class="mt-1 flex items-center gap-2 pl-10 text-xs text-ink-3">
      <time :title="fullTime(message.createdAt)" :datetime="new Date(message.createdAt).toISOString()">
        {{ clockTime(message.createdAt) }}
      </time>
      <button
        v-if="message.citation && !message.streaming"
        class="truncate text-ink-3 hover:text-brand-600"
        @click="emit('open-source', message.citation!)"
      >
        依据：《{{ message.citation.doc }}》{{ message.citation.section }}
      </button>
    </div>
  </div>
</template>