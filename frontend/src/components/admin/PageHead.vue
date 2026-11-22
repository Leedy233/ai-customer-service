<script setup lang="ts">
defineProps<{
  title: string
  desc?: string
}>()
</script>

<template>
  <div class="flex flex-wrap items-end gap-3">
    <div class="min-w-0">
      <h1 class="text-base font-semibold leading-6 text-ink">{{ title }}</h1>
      <p v-if="desc" class="mt-0.5 text-xs leading-5 text-ink-3">{{ desc }}</p>
    </div>
    <div class="ml-auto flex items-center gap-2">
      <slot name="actions" />
    </div>
  </div>
</template>