import axios, { AxiosError } from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/models'
import { ApiError } from '@/utils/errors'
import { useWorkbenchStore } from '@/stores/workbench'

/**
 * 统一请求封装。
 * 错误归一（过渡期双通道）：HTTP 401/429 与 body code 均归一为 ApiError；
 * 后端审查阶段一（错误单通道）落地后删除 body code 分支即可。
 */
const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/ai',
  withCredentials: true,
  timeout: 30000,
  headers: { 'X-Requested-With': 'XMLHttpRequest' },
})

let unauthorizedHandler: (() => void) | null = null
let unauthorizedFired = false
let lastRateLimitToast = 0

export function setUnauthorizedHandler(fn: () => void): void {
  unauthorizedHandler = fn
}

export function resetUnauthorizedFlag(): void {
  unauthorizedFired = false
}

function fireUnauthorized(): void {
  if (unauthorizedFired) return
  unauthorizedFired = true
  unauthorizedHandler?.()
}

instance.interceptors.response.use(
  (res) => {
    const body = res.data as ApiResponse<unknown> | undefined
    if (body && typeof body.code === 'number') {
      if (body.code === 401) {
        // body 401 含「登录密码错误」等业务语义，不触发全局过期跳转；
        // 真正的会话过期由拦截器 HTTP 401 分支统一处理
        throw new ApiError(401, body.message || '未登录或登录已过期', res.status)
      }
      if (body.code === 429) {
        toastRateLimit(body.message)
        throw new ApiError(429, body.message || '请求过于频繁', res.status)
      }
      if (body.code !== 200) {
        throw new ApiError(body.code, body.message || '请求失败', res.status)
      }
      return body.data as never
    }
    return res.data as never
  },
  (err: AxiosError<ApiResponse<unknown>>) => {
    if (err.response) {
      const status = err.response.status
      const body = err.response.data
      const msg = body?.message || err.message
      if (status === 401) {
        fireUnauthorized()
        throw new ApiError(401, msg || '登录已过期', status)
      }
      if (status === 429) {
        toastRateLimit(msg)
        throw new ApiError(429, msg || '请求过于频繁', status)
      }
      if (body && typeof body.code === 'number' && body.code !== 200) {
        throw new ApiError(body.code, body.message || msg, status)
      }
      throw new ApiError(status, msg || `服务异常（HTTP ${status}）`, status)
    }
    workbenchBanner('网络连接不稳定，请检查网络后重试')
    throw new ApiError(0, '网络异常：请确认后端已启动且代理配置正确')
  },
)

/** 断网/超时全局提示条（工作台顶部可关闭） */
function workbenchBanner(msg: string): void {
  try {
    useWorkbenchStore().setBanner(msg)
  } catch {
    // pinia 尚未初始化（极早期请求）时忽略
  }
}

function toastRateLimit(msg?: string): void {
  const now = Date.now()
  if (now - lastRateLimitToast < 3000) return
  lastRateLimitToast = now
  ElMessage.warning(msg || '请求过于频繁，请稍后再试')
}

/** 全局兜底错误提示（组件内 catch 后调用，避免各处重复 ElMessage） */
export function notifyError(e: unknown): void {
  if (e instanceof ApiError) {
    if (e.code === 401 || e.code === 429) return // 已由拦截器统一处理
    ElMessage.error(e.message)
    return
  }
  ElMessage.error('操作失败，请稍后重试')
}

export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.get(url, config) as Promise<T>
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return instance.post(url, data, config) as Promise<T>
}

/** 加法：DELETE 请求封装（会话真删除） */
export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.delete(url, config) as Promise<T>
}

export default instance