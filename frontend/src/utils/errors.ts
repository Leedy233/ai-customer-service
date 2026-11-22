/** 统一业务错误：code 与后端 body code 对齐，httpStatus 为真实 HTTP 状态 */
export class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
    public httpStatus?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError
}

export function isAbortError(e: unknown): boolean {
  return e instanceof DOMException && e.name === 'AbortError'
}