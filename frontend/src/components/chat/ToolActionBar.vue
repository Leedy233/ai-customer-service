<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import type { MessageVM } from '@/types/models'
import { toolArgs } from '@/utils/server-messages'

const props = defineProps<{
  message: MessageVM
}>()

const expanded = ref(false)
const showFull = ref(false)

const VERBS: Record<string, string> = {
  queryOrder: '已查询订单',
  queryLogistics: '已查询物流',
  checkRefundEligibility: '已校验退款资格',
}

const verb = computed(() => VERBS[props.message.toolName ?? ''] ?? '已调用工具')
// 真入参优先（SSE v2 / 服务端落库），旧数据回退文本推导
const args = computed(() => props.message.toolArgs ?? toolArgs(props.message.content))
const argsText = computed(() => JSON.stringify(args.value))
const duration = computed(() => {
  const ms = props.message.toolDurationMs
  return ms === undefined ? '' : `${(ms / 1000).toFixed(1)}s`
})
const lines = computed(() => props.message.content.split('\n').filter((l) => l.trim()))
const summary = computed(() => lines.value.slice(0, 5).join('\n'))
const hasMore = computed(() => lines.value.length > 5)
</script>

<template>
  <!-- 服务回执：一次工具调用 = 一张流程凭证 -->
  <div class="receipt max-w-[72%] px-3 py-2">
    <button
      class="flex w-full items-center gap-2 text-left text-xs text-ink-2"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      <span aria-hidden="true">🔧</span>
      <span class="min-w-0 flex-1 truncate">
        {{ verb }} · <span class="mono">{{ message.toolName }}({{ argsText }})</span>
      </span>
      <span v-if="duration" class="mono shrink-0 text-ink-3">{{ duration }}</span>
      <el-icon :size="12" class="shrink-0 text-ink-3">
        <ArrowUp v-if="expanded" />
        <ArrowDown v-else />
      </el-icon>
    </button>

    <div class="reveal" :class="{ open: expanded }">
      <div>
        <div class="receipt-perf mt-2 space-y-2 pt-2">
          <div class="flex items-baseline gap-2">
            <span class="w-14 shrink-0 text-xs text-ink-3">工具名</span>
            <span class="mono min-w-0 truncate text-xs text-ink">{{ message.toolName }}</span>
          </div>
          <div class="flex items-baseline gap-2">
            <span class="w-14 shrink-0 text-xs text-ink-3">入参</span>
            <pre class="mono min-w-0 flex-1 overflow-x-auto rounded-md bg-surface p-2 text-xs leading-5 text-ink-2">{{ argsText }}</pre>
          </div>
          <div class="flex items-start gap-2">
            <span class="w-14 shrink-0 text-xs text-ink-3">返回摘要</span>
            <div class="min-w-0 flex-1">
              <pre class="mono whitespace-pre-wrap rounded-md bg-surface p-2 text-xs leading-5 text-ink-2">{{ showFull ? message.content : summary }}</pre>
              <button
                v-if="hasMore"
                class="mt-1 text-xs text-brand-600 hover:text-brand-700"
                @click="showFull = !showFull"
              >
                {{ showFull ? '收起完整结果' : '查看完整结果' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>