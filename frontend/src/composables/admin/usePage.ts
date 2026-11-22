import { ref } from 'vue'
import type { Ref } from 'vue'
import { notifyError } from '@/api/http'
import type { PageVO } from '@/types/admin'

/**
 * 管理端分页加载：fetcher 闭包捕获过滤条件 ref，过滤变化后调 reload() 回第一页。
 */
export function usePage<T>(fetcher: (page: number, size: number) => Promise<PageVO<T>>) {
  const rows: Ref<T[]> = ref([]) as Ref<T[]>
  const total = ref(0)
  const page = ref(1)
  const size = ref(10)
  const loading = ref(false)
  const meta = ref<Record<string, number>>({})

  async function load(): Promise<void> {
    loading.value = true
    try {
      const res = await fetcher(page.value, size.value)
      rows.value = res.list
      total.value = res.total
      meta.value = res.meta ?? {}
    } catch (e) {
      notifyError(e)
    } finally {
      loading.value = false
    }
  }

  function reload(): void {
    page.value = 1
    void load()
  }

  function onPage(p: number): void {
    page.value = p
    void load()
  }

  function onSize(s: number): void {
    size.value = s
    page.value = 1
    void load()
  }

  return { rows, total, page, size, loading, meta, load, reload, onPage, onSize }
}