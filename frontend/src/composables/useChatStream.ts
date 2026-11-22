import { streamUrl } from '@/api/chat'
import * as contentApi from '@/api/user/content'
import { useChatStore } from '@/stores/chat'
import { useConversationStore } from '@/stores/conversation'
import { useWorkbenchStore } from '@/stores/workbench'
import { streamSSE } from '@/composables/useSSE'
import { isAbortError, isApiError } from '@/utils/errors'
import { parseOrderCard } from '@/utils/order-card'
import { detectCitation } from '@/utils/citation'
import { mapServerMessages } from '@/utils/server-messages'
import { summarize } from '@/utils/conversation-group'
import { truncate } from '@/utils/format'

/**
 * 聊天流编排：v2 结构化帧（sources/delta/tool）+ v1 裸流兼容。
 * 流结束后拉取服务端历史合并工具真入参/真耗时/真溯源与真实时间戳。
 */
export function useChatStream() {
  const chat = useChatStore()
  const conversations = useConversationStore()
  const workbench = useWorkbenchStore()

  async function send(convId: string, text: string): Promise<void> {
    const content = text.trim()
    if (!content || chat.isStreaming(convId)) return

    conversations.touch(convId, content)
    const userVm = chat.pushUser(convId, content)
    const placeholder = chat.pushAssistantPlaceholder(convId)

    const ctrl = new AbortController()
    chat.beginStream(convId, ctrl)
    let firstFrame = true
    let liveSources: import('@/types/models').Citation[] = []

    try {
      await streamSSE(streamUrl(convId, content), {
        signal: ctrl.signal,
        onFrame: (frame) => {
          if ((frame.event === 'message' || frame.event === 'delta') && frame.data) {
            if (firstFrame) {
              firstFrame = false
              chat.markUserStatus(convId, userVm.id, 'sent')
            }
            chat.appendDelta(convId, placeholder.id, frame.data)
          } else if (frame.event === 'tool' && frame.data) {
            try {
              const t = JSON.parse(frame.data) as {
                name: string
                args?: Record<string, string>
                durationMs?: number
              }
              chat.insertTool(convId, placeholder.id, {
                id: `live-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
                role: 'tool',
                content: '',
                toolName: t.name,
                toolArgs: t.args,
                toolDurationMs: t.durationMs,
                createdAt: Date.now(),
              })
            } catch {
              // 非法工具帧忽略，不影响文本流
            }
          } else if (frame.event === 'sources' && frame.data) {
            try {
              liveSources = JSON.parse(frame.data) as import('@/types/models').Citation[]
              chat.attachSources(convId, placeholder.id, liveSources)
            } catch {
              // 非法溯源帧忽略
            }
          }
        },
      })
      const vm = chat.messages(convId).find((m) => m.id === placeholder.id)
      const answer = vm?.content ?? ''
      chat.markUserStatus(convId, userVm.id, 'sent')
      chat.finalize(convId, placeholder.id, {
        card: parseOrderCard(answer),
      })
      const finalized = chat.messages(convId).find((m) => m.id === placeholder.id)
      if (finalized && !liveSources.length) finalized.citation = detectCitation(answer)
      conversations.setSummary(convId, truncate(summarize([answer]), 30))
      workbench.clearBanner()
      // 合并服务端真值：工具真入参/真耗时/真溯源、服务端时间戳
      try {
        const ms = await contentApi.messages(convId)
        if (!chat.isStreaming(convId)) chat.loadHistory(convId, mapServerMessages(ms))
      } catch {
        // 合并失败保留本地流式结果
      }
    } catch (e) {
      if (isAbortError(e)) {
        chat.markUserStatus(convId, userVm.id, 'sent')
        chat.finalize(convId, placeholder.id, { stopped: true })
        return
      }
      chat.markUserStatus(convId, userVm.id, 'failed')
      const msg = isApiError(e) ? e.message : '连接中断，请重试'
      chat.finalize(convId, placeholder.id, { error: msg })
      if (!isApiError(e) || (isApiError(e) && e.code === 0)) {
        workbench.setBanner('网络连接不稳定，消息可能未送达，请检查网络后重试')
      }
    }
  }

  function stop(): void {
    chat.stop()
  }

  return { send, stop }
}