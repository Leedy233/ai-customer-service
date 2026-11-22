import { defineStore } from 'pinia'
import { ref } from 'vue'
import { readJSON, writeJSON } from '@/utils/storage'
import * as queueApi from '@/api/user/queue'

export interface EndedInfo {
  ticketNo: string
  ticketId?: number
  closedAt: number
  category: string
  resolved: boolean
  durationMs: number
  messageCount: number
  rating?: number
  feedback?: string
}

const CONTEXT_KEY = 'context-collapsed-v1'
const ENDED_KEY = 'ended-sessions-v1'
/** 排队状态轮询间隔（真值来自 /ai/queue/status） */
const QUEUE_POLL_MS = 5000

/**
 * 工作台 UI 状态：右栏折叠、转人工排队（位置/预计等待为服务端真值 + 5s 轮询）、
 * 会话结束档案（工单号来自服务端）、全局网络提示条。
 */
export const useWorkbenchStore = defineStore('workbench', () => {
  const contextCollapsed = ref(readJSON<boolean>(CONTEXT_KEY, false))
  const rightDrawerOpen = ref(false)
  const queue = ref<{
    active: boolean
    position: number
    etaSeconds: number
    ticketId: number | null
    ticketNo: string
    convId: string
  }>({ active: false, position: 0, etaSeconds: 0, ticketId: null, ticketNo: '', convId: '' })
  const ended = ref<Record<string, EndedInfo>>(readJSON<Record<string, EndedInfo>>(ENDED_KEY, {}))
  const banner = ref<string | null>(null)
  let pollTimer: number | null = null

  function toggleContext(): void {
    contextCollapsed.value = !contextCollapsed.value
    writeJSON(CONTEXT_KEY, contextCollapsed.value)
  }

  function stopPoll(): void {
    if (pollTimer !== null) {
      window.clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function applyStatus(s: { active: boolean; position: number; estimatedWaitSeconds: number }): void {
    queue.value.active = s.active
    queue.value.position = s.position
    queue.value.etaSeconds = s.estimatedWaitSeconds
    if (!s.active) stopPoll()
  }

  /** 加入排队：工单已建好后调用；join 幂等，随后 5s 轮询真位置 */
  async function startQueue(ticket: { id: number; no: string }, convId: string): Promise<void> {
    queue.value = {
      active: true,
      position: 0,
      etaSeconds: 0,
      ticketId: ticket.id,
      ticketNo: ticket.no,
      convId,
    }
    stopPoll()
    try {
      applyStatus(await queueApi.join(convId))
    } catch {
      // join 失败保留 active 占位，轮询兜底恢复真值
    }
    pollTimer = window.setInterval(async () => {
      if (!queue.value.active || !queue.value.convId) {
        stopPoll()
        return
      }
      try {
        applyStatus(await queueApi.status(queue.value.convId))
      } catch {
        // 单次轮询失败忽略，下轮重试
      }
    }, QUEUE_POLL_MS)
  }

  /** 取消排队：服务端退队列 + 停轮询 */
  async function cancelQueue(): Promise<void> {
    const convId = queue.value.convId
    stopPoll()
    queue.value = { active: false, position: 0, etaSeconds: 0, ticketId: null, ticketNo: '', convId: '' }
    if (convId) {
      try {
        await queueApi.cancel(convId)
      } catch {
        // 服务端可能已 SERVED/取消，忽略
      }
    }
  }

  function endSession(convId: string, info: EndedInfo): void {
    ended.value[convId] = info
    writeJSON(ENDED_KEY, ended.value)
    void cancelQueue()
  }

  function rateSession(convId: string, rating: number, feedback: string): void {
    const info = ended.value[convId]
    if (info) {
      info.rating = rating
      info.feedback = feedback
      writeJSON(ENDED_KEY, ended.value)
    }
  }

  function setBanner(msg: string): void {
    banner.value = msg
  }

  function clearBanner(): void {
    banner.value = null
  }

  return {
    contextCollapsed,
    rightDrawerOpen,
    queue,
    ended,
    banner,
    toggleContext,
    startQueue,
    cancelQueue,
    endSession,
    rateSession,
    setBanner,
    clearBanner,
  }
})