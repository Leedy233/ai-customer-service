import { defineStore } from 'pinia'
import { ref } from 'vue'
import { readJSON, writeJSON } from '@/utils/storage'

export const useAppStore = defineStore('app', () => {
  const sidebarOpen = ref(false) // 移动端抽屉
  const sidebarCollapsed = ref(readJSON<boolean>('sidebar-collapsed', false))

  function toggleCollapsed(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
    writeJSON('sidebar-collapsed', sidebarCollapsed.value)
  }

  return { sidebarOpen, sidebarCollapsed, toggleCollapsed }
})