<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Operation } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useConversationStore } from '@/stores/conversation'
import { useUserStore } from '@/stores/user'

const app = useAppStore()
const conversations = useConversationStore()
const user = useUserStore()
const router = useRouter()

const title = computed(() => {
  const conv = conversations.list.find((c) => c.id === conversations.currentId)
  return conv?.title ?? '智能客服工作台'
})

const initial = computed(() => (user.profile?.username ?? '?').slice(0, 1).toUpperCase())

async function onLogout(): Promise<void> {
  await user.logout()
  ElMessage.success('已退出登录')
  void router.push('/login')
}
</script>

<template>
  <header
    class="flex h-12 shrink-0 items-center gap-2 border-b border-line bg-card px-3 md:px-4"
  >
    <button
      class="tap-target flex items-center justify-center rounded-lg text-ink-2 hover:bg-surface min-[769px]:hidden"
      aria-label="打开会话列表"
      @click="app.sidebarOpen = true"
    >
      <el-icon :size="18"><Operation /></el-icon>
    </button>

    <!-- 产品标识 -->
    <div class="flex shrink-0 items-center gap-2">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <rect x="1.5" y="1.5" width="21" height="21" rx="6" fill="#F1EFFB" />
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
      <span class="hidden text-[15px] font-semibold text-ink sm:inline">智能客服工作台</span>
    </div>

    <!-- 当前会话标题 -->
    <div class="mx-2 hidden h-4 w-px bg-line md:block" />
    <span class="min-w-0 flex-1 truncate text-center text-sm text-ink-2 md:text-left">
      {{ title }}
    </span>

    <!-- 用户菜单 -->
    <el-dropdown trigger="click">
      <button
        class="flex cursor-pointer items-center gap-2 rounded-lg px-1.5 py-1 hover:bg-surface"
        aria-label="用户菜单"
      >
        <span
          class="flex h-8 w-8 items-center justify-center rounded-full bg-brand-50 text-sm font-semibold text-brand-600"
        >
          {{ initial }}
        </span>
        <span class="hidden text-sm text-ink-2 sm:inline">{{ user.profile?.username }}</span>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item disabled>
            {{ user.profile?.username }}（{{ user.role === 'ADMIN' ? '管理员' : '客户' }}）
          </el-dropdown-item>
          <el-dropdown-item v-if="user.role === 'ADMIN'" divided @click="router.push('/admin')">
            管理后台
          </el-dropdown-item>
          <el-dropdown-item divided @click="onLogout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </header>
</template>