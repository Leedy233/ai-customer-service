import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { resetUnauthorizedFlag } from '@/api/http'
import type { UserProfile } from '@/types/models'
import { isApiError } from '@/utils/errors'

export const useUserStore = defineStore('user', () => {
  const profile = ref<UserProfile | null>(null)
  const loaded = ref(false)
  const verifiedAt = ref(0)

  const role = computed<'USER' | 'ADMIN'>(() => profile.value?.role ?? 'USER')
  const isLoggedIn = computed(() => profile.value !== null)

  /** 加法：管理端导航节流复验（60s），防内存态与真实 Session 脱节 */
  function shouldReverify(): boolean {
    return Date.now() - verifiedAt.value > 60_000
  }

  async function fetchMe(): Promise<UserProfile | null> {
    try {
      profile.value = await authApi.me()
      verifiedAt.value = Date.now()
    } catch (e) {
      if (isApiError(e) && e.code === 401) profile.value = null
      else throw e
    } finally {
      loaded.value = true
    }
    return profile.value
  }

  async function login(username: string, password: string): Promise<void> {
    await authApi.login(username, password)
    resetUnauthorizedFlag()
    await fetchMe()
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } finally {
      clear()
    }
  }

  function clear(): void {
    profile.value = null
    loaded.value = true
  }

  return { profile, loaded, role, isLoggedIn, shouldReverify, fetchMe, login, logout, clear }
})