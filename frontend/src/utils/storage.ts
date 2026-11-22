const PREFIX = 'cs:'

export function readJSON<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(PREFIX + key)
    return raw ? (JSON.parse(raw) as T) : fallback
  } catch {
    return fallback
  }
}

export function writeJSON(key: string, value: unknown): void {
  try {
    localStorage.setItem(PREFIX + key, JSON.stringify(value))
  } catch {
    /* 隐私模式等写入失败时静默降级为内存态 */
  }
}

export function remove(key: string): void {
  localStorage.removeItem(PREFIX + key)
}