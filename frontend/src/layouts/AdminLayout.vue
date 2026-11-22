<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Back,
  ChatLineSquare,
  Coin,
  Collection,
  Document,
  List,
  Money,
  Monitor,
  Odometer,
  Service,
  Timer,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const router = useRouter()
const route = useRoute()

/** 统一菜单：切片 2-10 已交付，全部就绪 */
const menus = [
  { path: '/admin', title: '数据仪表盘', icon: Odometer, ready: true },
  { path: '/admin/orders', title: '订单管理', icon: List, ready: true },
  { path: '/admin/refunds', title: '退款管理', icon: Money, ready: true },
  { path: '/admin/tickets', title: '人工客服工单', icon: Service, ready: true },
  { path: '/admin/knowledge', title: 'RAG 知识库', icon: Collection, ready: true },
  { path: '/admin/audit', title: 'AI 对话审计', icon: ChatLineSquare, ready: true },
  { path: '/admin/logs', title: 'AI 请求日志', icon: Document, ready: true },
  { path: '/admin/tokens', title: '词元消耗统计', icon: Coin, ready: true },
  { path: '/admin/latency', title: '模型响应时间', icon: Timer, ready: true },
  { path: '/admin/monitor', title: '系统运行监控', icon: Monitor, ready: true },
]

const active = computed(() => route.path)

async function onLogout(): Promise<void> {
  await user.logout()
  ElMessage.success('已退出登录')
  void router.push('/login')
}
</script>

<template>
  <div class="flex h-screen w-full overflow-hidden bg-surface">
    <aside class="flex w-56 shrink-0 flex-col border-r border-line bg-card">
      <div class="flex h-14 shrink-0 items-center gap-2 border-b border-line px-4">
        <span class="h-7 w-7 shrink-0 rounded-lg bg-brand-500" />
        <span class="truncate text-sm font-semibold text-ink">管理后台</span>
        <el-tag class="ml-auto" size="small" type="danger" effect="plain">ADMIN</el-tag>
      </div>
      <el-menu :default-active="active" router class="flex-1 overflow-y-auto !border-r-0">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path" :disabled="!m.ready">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
          <el-tag v-if="!m.ready" class="ml-auto" size="small" type="info" effect="plain">
            待开发
          </el-tag>
        </el-menu-item>
      </el-menu>
      <div class="shrink-0 border-t border-line p-3">
        <el-button class="w-full" :icon="Back" @click="router.push('/chat')">返回用户端</el-button>
      </div>
    </aside>
    <div class="flex min-w-0 flex-1 flex-col">
      <header class="flex h-14 shrink-0 items-center gap-2 border-b border-line bg-card px-5">
        <span class="text-sm font-semibold text-ink">
          {{ (route.meta.title as string) || '管理后台' }}
        </span>
        <div class="ml-auto flex items-center gap-3">
          <span class="text-sm text-ink-2">{{ user.profile?.username }}</span>
          <el-button text type="danger" @click="onLogout">退出</el-button>
        </div>
      </header>
      <main class="flex-1 overflow-y-auto p-4 md:p-6">
        <router-view />
      </main>
    </div>
  </div>
</template>