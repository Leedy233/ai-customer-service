<script setup lang="ts">
import { useRouter } from 'vue-router'
import EmptyState from '@/components/common/EmptyState.vue'

const router = useRouter()
</script>

<template>
  <div class="flex min-h-dvh items-center justify-center bg-surface">
    <EmptyState title="页面不存在" description="你访问的地址可能已失效">
      <el-button round type="primary" @click="router.push('/chat')">回到聊天工作台</el-button>
    </EmptyState>
  </div>
</template>