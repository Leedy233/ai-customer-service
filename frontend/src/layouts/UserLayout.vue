<script setup lang="ts">
import { User } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useWorkbenchStore } from '@/stores/workbench'
import ConversationList from '@/components/chat/ConversationList.vue'
import ContextPanel from '@/components/chat/ContextPanel.vue'
import TopBar from '@/components/layout/TopBar.vue'

const app = useAppStore()
const workbench = useWorkbenchStore()
</script>

<template>
  <div class="flex h-dvh overflow-hidden bg-surface">
    <!-- 左栏：会话列表（≥769px 常驻） -->
    <aside class="hidden w-[280px] shrink-0 flex-col border-r border-line min-[769px]:flex">
      <ConversationList />
    </aside>

    <!-- 左栏移动抽屉 -->
    <el-drawer
      v-model="app.sidebarOpen"
      direction="ltr"
      size="280px"
      :with-header="false"
      class="min-[769px]:hidden"
    >
      <ConversationList />
    </el-drawer>

    <!-- 中栏 -->
    <div class="flex min-w-0 flex-1 flex-col">
      <TopBar />
      <main class="min-h-0 flex-1">
        <router-view />
      </main>
    </div>

    <!-- 右栏：客户与业务上下文（≥1200px 常驻） -->
    <aside
      v-if="!workbench.contextCollapsed"
      class="hidden w-[320px] shrink-0 border-l border-line min-[1200px]:block"
    >
      <ContextPanel @collapse="workbench.toggleContext()" />
    </aside>

    <!-- 右栏抽屉（<1200px） -->
    <el-drawer
      v-model="workbench.rightDrawerOpen"
      direction="rtl"
      size="min(320px, 92vw)"
      :with-header="false"
      class="min-[1200px]:hidden"
    >
      <ContextPanel in-drawer />
    </el-drawer>

    <!-- 右栏收起后的悬浮展开按钮 -->
    <button
      v-if="workbench.contextCollapsed"
      class="fixed bottom-6 right-6 z-40 hidden h-11 items-center gap-2 rounded-full bg-brand-500 px-4 text-sm font-medium text-white shadow-[0_4px_16px_rgba(31,35,41,0.2)] hover:bg-brand-600 min-[1200px]:flex"
      aria-label="展开客户信息面板"
      @click="workbench.toggleContext()"
    >
      <el-icon :size="16"><User /></el-icon>
      客户信息
    </button>
  </div>
</template>