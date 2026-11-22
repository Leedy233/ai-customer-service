import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import UserLayout from '@/layouts/UserLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  { path: '/', redirect: '/chat' },
  {
    path: '/chat',
    component: UserLayout,
    children: [
      {
        path: '',
        name: 'Chat',
        component: () => import('@/views/user/ChatView.vue'),
        meta: { title: '智能客服' },
      },
      {
        path: ':conversationId',
        name: 'ChatConv',
        component: () => import('@/views/user/ChatView.vue'),
        meta: { title: '智能客服' },
      },
    ],
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { role: 'ADMIN' },
    children: [
      {
        path: '',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/DashboardView.vue'),
        meta: { title: '数据仪表盘', role: 'ADMIN' },
      },
      {
        path: 'orders',
        name: 'AdminOrders',
        component: () => import('@/views/admin/OrdersView.vue'),
        meta: { title: '订单管理', role: 'ADMIN' },
      },
      {
        path: 'refunds',
        name: 'AdminRefunds',
        component: () => import('@/views/admin/RefundsView.vue'),
        meta: { title: '退款管理', role: 'ADMIN' },
      },
      {
        path: 'tickets',
        name: 'AdminTickets',
        component: () => import('@/views/admin/TicketsView.vue'),
        meta: { title: '人工客服工单', role: 'ADMIN' },
      },
      {
        path: 'knowledge',
        name: 'AdminKnowledge',
        component: () => import('@/views/admin/KnowledgeView.vue'),
        meta: { title: 'RAG 知识库', role: 'ADMIN' },
      },
      {
        path: 'audit',
        name: 'AdminAudit',
        component: () => import('@/views/admin/AuditView.vue'),
        meta: { title: 'AI 对话审计', role: 'ADMIN' },
      },
      {
        path: 'logs',
        name: 'AdminLogs',
        component: () => import('@/views/admin/LogsView.vue'),
        meta: { title: 'AI 请求日志', role: 'ADMIN' },
      },
      {
        path: 'tokens',
        name: 'AdminTokens',
        component: () => import('@/views/admin/TokensView.vue'),
        meta: { title: '词元消耗统计', role: 'ADMIN' },
      },
      {
        path: 'latency',
        name: 'AdminLatency',
        component: () => import('@/views/admin/LatencyView.vue'),
        meta: { title: '模型响应时间', role: 'ADMIN' },
      },
      {
        path: 'monitor',
        name: 'AdminMonitor',
        component: () => import('@/views/admin/MonitorView.vue'),
        meta: { title: '系统运行监控', role: 'ADMIN' },
      },
    ],
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { public: true, title: '无权限' },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { public: true, title: '页面不存在' },
  },
  { path: '/:pathMatch(.*)*', redirect: '/404' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || 'AI 智能客服'
  document.title = `${title} · AI 智能客服`
  sessionStorage.removeItem('chunk-reload:' + to.fullPath)
})

/**
 * 加法：旧标签页缓存的懒加载 chunk 在重新构建后 404，导航会静默中断（点击无反应）。
 * 识别动态导入失败时强制整页刷新到目标路由一次（sessionStorage 标记防循环）。
 */
router.onError((error, to) => {
  const msg = String((error as Error)?.message ?? '')
  if (/dynamically imported module|module script failed|Loading chunk|ChunkLoadError/i.test(msg)) {
    const key = 'chunk-reload:' + to.fullPath
    if (!sessionStorage.getItem(key)) {
      sessionStorage.setItem(key, '1')
      window.location.assign(to.fullPath)
    }
  }
})

export default router