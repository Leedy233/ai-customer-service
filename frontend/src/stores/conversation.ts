import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LocalConversation, ServerConversation } from '@/types/models'
import { readJSON, writeJSON } from '@/utils/storage'
import { truncate } from '@/utils/format'
import { defaultTitle, uniqueTitle } from '@/utils/conversation-group'
import { removeConversation } from '@/api/user/content'

const LIST_KEY = 'conversations-v3'
const CURRENT_KEY = 'current-conversation-v3'
const HIDDEN_KEY = 'hidden-conversations-v3'
const SUMMARY_KEY = 'conv-summaries-v4'

/**
 * 会话注册表：服务端会话列表为真值来源，本地补充未同步条目与摘要缓存。
 * 删除为本地隐藏（服务端删除接口待补充），隐藏项不再被服务端合并复活。
 */
export const useConversationStore = defineStore('conversation', () => {
  const list = ref<LocalConversation[]>(readJSON<LocalConversation[]>(LIST_KEY, []))
  const currentId = ref<string>(readJSON<string>(CURRENT_KEY, ''))
  const hidden = ref<string[]>(readJSON<string[]>(HIDDEN_KEY, []))
  const summaries = ref<Record<string, string>>(readJSON<Record<string, string>>(SUMMARY_KEY, {}))
  const listLoading = ref(false)

  function persist(): void {
    writeJSON(LIST_KEY, list.value)
    writeJSON(CURRENT_KEY, currentId.value)
    writeJSON(HIDDEN_KEY, hidden.value)
    writeJSON(SUMMARY_KEY, summaries.value)
  }

  function takenTitles(exceptId?: string): string[] {
    return list.value.filter((c) => c.id !== exceptId).map((c) => c.title)
  }

  function sorted(): LocalConversation[] {
    return [...list.value]
      .filter((c) => !hidden.value.includes(c.id))
      .sort((a, b) => b.updatedAt - a.updatedAt)
  }

  /** 空壳会话：仍为默认名且无任何消息摘要（创建后一条消息都没发过） */
  function isEmptyDraft(c: LocalConversation): boolean {
    return c.title.startsWith('新会话') && !summaries.value[c.id]
  }

  /** 纯本地条目移除（未发过消息的草稿服务端无记录，无需隐藏标记） */
  function dropLocal(id: string): void {
    list.value = list.value.filter((c) => c.id !== id)
    delete summaries.value[id]
  }

  function create(): LocalConversation {
    // 复用空壳：已有未发送的空会话时直接复用，避免连点新建堆积僵尸会话
    const reusable =
      list.value.find((c) => c.id === currentId.value && isEmptyDraft(c)) ??
      [...list.value].sort((a, b) => b.updatedAt - a.updatedAt).find(isEmptyDraft)
    if (reusable) {
      currentId.value = reusable.id
      persist()
      return reusable
    }
    const now = Date.now()
    const conv: LocalConversation = {
      id: `web-${now}`,
      title: uniqueTitle(defaultTitle(now), takenTitles()),
      createdAt: now,
      updatedAt: now,
    }
    list.value.push(conv)
    currentId.value = conv.id
    persist()
    return conv
  }

  function ensure(id: string): LocalConversation {
    const found = list.value.find((c) => c.id === id)
    if (found) return found
    const now = Date.now()
    const conv: LocalConversation = {
      id,
      title: uniqueTitle(defaultTitle(now), takenTitles()),
      createdAt: now,
      updatedAt: now,
    }
    list.value.push(conv)
    persist()
    return conv
  }

  function select(id: string): void {
    // 切走即销毁：离开的会话若是未发送的空壳，直接移除，不留僵尸条目
    const leaving = currentId.value
    if (leaving && leaving !== id) {
      const conv = list.value.find((c) => c.id === leaving)
      if (conv && isEmptyDraft(conv)) dropLocal(leaving)
    }
    currentId.value = id
    persist()
  }

  /** 清理历史空壳（保留当前会话，避免误删用户正在输入的空会话）；在服务端列表合并后调用 */
  function purgeEmptyDrafts(): void {
    const zombies = list.value.filter((c) => c.id !== currentId.value && isEmptyDraft(c))
    if (!zombies.length) return
    for (const z of zombies) dropLocal(z.id)
    persist()
  }

  /** 首条用户消息作为会话标题（仅替换默认名） */
  function touch(id: string, firstMessage?: string): void {
    const conv = ensure(id)
    conv.updatedAt = Date.now()
    if (firstMessage && conv.title.startsWith('新会话')) {
      conv.title = uniqueTitle(truncate(firstMessage, 20), takenTitles(id))
    }
    persist()
  }

  function rename(id: string, title: string): void {
    const conv = list.value.find((c) => c.id === id)
    if (conv) {
      const base = truncate(title.trim(), 20)
      conv.title = base ? uniqueTitle(base, takenTitles(id)) : conv.title
      persist()
    }
  }

  /** 本地隐藏删除；服务端删除接口待补充 */
  function remove(id: string): void {
    if (!hidden.value.includes(id)) hidden.value.push(id)
    dropLocal(id)
    if (currentId.value === id) currentId.value = ''
    persist()
  }

  /** 加法：真删除（服务端清消息+墓碑），失败回退本地隐藏 */
  async function removeRemote(id: string): Promise<boolean> {
    try {
      await removeConversation(id)
      remove(id)
      return true
    } catch {
      remove(id)
      return false
    }
  }

  function setSummary(id: string, text: string): void {
    summaries.value[id] = text
    persist()
  }

  /** 合并服务端会话列表（换浏览器/清缓存后历史仍可见）；隐藏项跳过 */
  function mergeServer(items: ServerConversation[]): void {
    const serverIds = new Set(items.map((s) => s.id))
    for (const s of items) {
      if (hidden.value.includes(s.id)) continue
      const ts = Date.parse(s.lastActiveAt) || Date.now()
      const found = list.value.find((c) => c.id === s.id)
      if (found) {
        if (found.title.startsWith('新会话') && s.title) {
          found.title = uniqueTitle(truncate(s.title, 20), takenTitles(found.id))
        }
        found.updatedAt = Math.max(found.updatedAt, ts)
        found.fromServer = true
      } else {
        list.value.push({
          id: s.id,
          title: uniqueTitle(truncate(s.title || s.id, 20), takenTitles()),
          createdAt: ts,
          updatedAt: ts,
          fromServer: true,
        })
      }
      // 加法：服务端真摘要优先（结束会话时生成）
      if (s.summary) summaries.value[s.id] = truncate(s.summary, 30)
    }
    // 加法：服务端真删除（墓碑）后列表缺席 → 本地同步清除，防跨设备复活；
    // 仅清除"上过服务端列表"或"已有摘要缓存（曾完成过对话）"的条目，
    // 纯本地草稿与未完成首轮的本地会话不受影响
    const removed = list.value.filter(
      (c) => !serverIds.has(c.id) && !isEmptyDraft(c) && (c.fromServer || !!summaries.value[c.id]),
    )
    if (removed.length) {
      const removedIds = new Set(removed.map((r) => r.id))
      for (const r of removed) delete summaries.value[r.id]
      list.value = list.value.filter((c) => !removedIds.has(c.id))
      if (removed.some((r) => r.id === currentId.value)) currentId.value = ''
    }
    persist()
  }

  return {
    list,
    currentId,
    hidden,
    summaries,
    listLoading,
    sorted,
    create,
    ensure,
    select,
    purgeEmptyDrafts,
    touch,
    rename,
    remove,
    removeRemote,
    setSummary,
    mergeServer,
  }
})