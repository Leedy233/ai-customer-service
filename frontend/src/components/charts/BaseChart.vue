<script setup lang="ts">
import { computed, ref } from 'vue'
import { useECharts } from '@/composables/useECharts'
import type { EChartsOption } from '@/composables/useECharts'

const props = defineProps<{ option: EChartsOption }>()
const el = ref<HTMLElement>()
const optionRef = computed(() => props.option)
useECharts(el, optionRef)
</script>

<template>
  <div ref="el" class="h-full w-full" />
</template>