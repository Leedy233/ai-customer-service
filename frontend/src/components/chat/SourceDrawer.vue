<script setup lang="ts">
import { computed } from 'vue'
import type { Citation } from '@/types/models'

const props = defineProps<{
  modelValue: boolean
  citation: Citation | null
  /** 真溯源列表（服务端 sources）；缺失时展示单条兜底 */
  sources?: Citation[]
  /** 是否服务端真值（决定标签文案） */
  live?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const items = computed<Citation[]>(() =>
  props.sources?.length ? props.sources : props.citation ? [props.citation] : [],
)
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    direction="rtl"
    size="min(420px, 92vw)"
    :with-header="false"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div v-if="items.length" class="flex h-full flex-col">
      <div class="flex items-center gap-2 border-b border-line pb-3">
        <h2 class="min-w-0 flex-1 truncate text-base font-semibold text-ink">依据原文</h2>
        <el-tag size="small" :type="live ? 'success' : 'warning'" effect="plain" round>
          {{ live ? '知识库真值' : '示例内容' }}
        </el-tag>
      </div>
      <div class="min-h-0 flex-1 overflow-y-auto pt-4">
        <section v-for="(c, i) in items" :key="i" class="border-b border-line py-4 first:pt-0 last:border-0">
          <div class="text-sm font-medium text-ink">《{{ c.doc }}》{{ c.section }}</div>
          <p class="mt-3 whitespace-pre-wrap text-sm leading-6 text-ink-2">{{ c.excerpt }}</p>
        </section>
        <p v-if="!live" class="mt-6 text-xs leading-5 text-ink-3">
          该会话无服务端引用字段（旧历史数据），片段为按主题匹配的示例内容。
        </p>
        <p v-else class="mt-6 text-xs leading-5 text-ink-3">
          引用来自本次回答实际检索命中的知识库 chunk（标题 / 片段序号 / 原文）。
        </p>
      </div>
    </div>
  </el-drawer>
</template>