import type { Router } from 'vue-router'
import { setUnauthorizedHandler } from '@/api/http'
import { useUserStore } from '@/stores/user'

/** 登录守卫 + 全局 401 统一跳转（Session Cookie 认证，无 token 存储） */
export function installGuards(router: Router): void {
  const user = useUserStore()

  setUnauthorizedHandler(() => {
    // 加法：会话过期用整页跳转恢复（清掉旧标签页的内存态与旧 chunk），
    // 避免 SPA push 与用户点击导航竞态导致视图/URL 错配；
    // 匿名探针 401（尚未登录）不跳转不提示，由守卫自行落登录页
    const wasLoggedIn = user.isLoggedIn
    const from = router.currentRoute.value.fullPath
    user.clear()
    if (!wasLoggedIn) return
    const redirect = from && from !== '/' ? from : '/chat'
    window.location.assign(
      `/login?redirect=${encodeURIComponent(redirect)}&reason=expired`,
    )
  })

  router.beforeEach(async (to) => {
    if (!user.loaded) {
      try {
        await user.fetchMe()
      } catch {
        user.clear()
      }
    }
    if (to.meta.public) return true
    if (!user.isLoggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    // 加法：管理端路由节流复验会话（60s 一次），防内存态角色与真实 Session 脱节
    if (to.meta.role === 'ADMIN' && user.shouldReverify()) {
      try {
        await user.fetchMe()
      } catch {
        user.clear()
      }
      if (!user.isLoggedIn) {
        return { path: '/login', query: { redirect: to.fullPath, reason: 'expired' } }
      }
    }
    // 管理端路由权限：前端守卫仅为体验层，后端 AdminInterceptor 才是安全层
    if (to.meta.role === 'ADMIN' && user.role !== 'ADMIN') {
      return { path: '/403' }
    }
    return true
  })
}