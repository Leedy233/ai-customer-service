import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'
import type { MessageVM, OrderCardData } from '@/types/models'
import { uid } from '@/utils/format'

/** 聊天状态：按 conversationId 分桶，流式期间仅追加文本，结束才解析卡片 */
export const useChatStore = defineStore('chat', () => {
  const buckets = ref<Record<string, MessageVM[]>>({})
  const streamingConvId = ref<string | null>(null)
  // AbortController 含内部槽，必须 shallowRef，避免 reactive 代理导致 abort() 非法调用
  const controller = shallowRef<AbortController | null>(null)

  function messages(convId: string): MessageVM[] {
    if (!buckets.value[convId]) buckets.value[convId] = []
    return buckets.value[convId]
  }

  /** 服务端历史回填（仅空桶时调用，避免覆盖本地流式状态） */
  function loadHistory(convId: string, vms: MessageVM[]): void {
    buckets.value[convId] = vms
  }

  function pushUser(convId: string, content: string): MessageVM {
    const vm: MessageVM = {
      id: uid('u'),
      role: 'user',
      content,
      createdAt: Date.now(),
      status: 'sending',
    }
    messages(convId).push(vm)
    return vm
  }

  function markUserStatus(convId: string, vmId: string, status: 'sending' | 'sent' | 'failed'): void {
    const vm = messages(convId).find((m) => m.id === vmId)
    if (vm) vm.status = status
  }

  function pushAssistantPlaceholder(convId: string): MessageVM {
    const vm: MessageVM = {
      id: uid('a'),
      role: 'assistant',
      content: '',
      streaming: true,
      createdAt: Date.now(),
    }
    messages(convId).push(vm)
    return vm
  }

  function pushNotice(convId: string, content: string): void {
    messages(convId).push({ id: uid('n'), role: 'notice', content, createdAt: Date.now() })
  }

  function appendDelta(convId: string, vmId: string, text: string): void {
    const vm = messages(convId).find((m) => m.id === vmId)
    if (vm) vm.content += text
  }

  /** 加法：SSE v2 工具事件实时插条（插在助手占位符之前，流结束后由服务端历史合并真值） */
  function insertTool(convId: string, beforeVmId: string, vm: MessageVM): void {
    const list = messages(convId)
    const idx = list.findIndex((m) => m.id === beforeVmId)
    if (idx < 0) list.push(vm)
    else list.splice(idx, 0, vm)
  }

  /** 加法：流首帧真溯源挂到助手消息 */
  function attachSources(convId: string, vmId: string, sources: import('@/types/models').Citation[]): void {
    const vm = messages(convId).find((m) => m.id === vmId)
    if (vm) {
      vm.sources = sources
      if (sources.length) vm.citation = sources[0]
    }
  }

  function finalize(
    convId: string,
    vmId: string,
    opts: { stopped?: boolean; error?: string; card?: OrderCardData },
  ): void {
    const vm = messages(convId).find((m) => m.id === vmId)
    if (vm) {
      vm.streaming = false
      vm.stopped = opts.stopped
      vm.error = opts.error
      vm.card = opts.card
    }
    if (streamingConvId.value === convId) {
      streamingConvId.value = null
      controller.value = null
    }
  }

  function beginStream(convId: string, ctrl: AbortController): void {
    streamingConvId.value = convId
    controller.value = ctrl
  }

  function stop(): void {
    controller.value?.abort()
  }

  function isStreaming(convId: string): boolean {
    return streamingConvId.value === convId
  }

  return {
    buckets,
    streamingConvId,
    messages,
    loadHistory,
    pushUser,
    markUserStatus,
    pushAssistantPlaceholder,
    pushNotice,
    appendDelta,
    insertTool,
    attachSources,
    finalize,
    beginStream,
    stop,
    isStreaming,
  }
})