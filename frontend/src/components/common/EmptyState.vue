<script setup lang="ts">
import { ChatDotRound } from '@element-plus/icons-vue'

defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center px-6 py-14 text-center">
    <span class="flex h-14 w-14 items-center justify-center rounded-2xl bg-surface text-2xl text-ink-3">
      <el-icon><ChatDotRound /></el-icon>
    </span>
    <p class="mt-4 text-sm font-medium text-ink-2">{{ title }}</p>
    <p v-if="description" class="mt-1 max-w-xs text-xs leading-relaxed text-ink-3">
      {{ description }}
    </p>
    <div class="mt-4">
      <slot />
    </div>
  </div>
</template>