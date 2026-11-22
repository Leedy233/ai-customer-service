export interface StatsOverview {
  todayConversations: number
  todayRequests: number
  todayTokens: number
  avgLatencyMs: number
  totalOrders: number
  totalUsers: number
  pendingRefunds: number
  refundedCount: number
  ordersByStatus: Record<string, number>
}

export interface TrendPoint {
  date: string
  requests: number
  tokens: number
  avgLatencyMs: number
  conversations: number
}

/** 管理端分页（后端 PageVO） */
export interface PageVO<T> {
  total: number
  page: number
  size: number
  list: T[]
  meta: Record<string, number>
}

export interface AdminOrderVO {
  id: number
  orderNo: string
  userId: number
  username: string
  productName: string
  amount: number
  status: string
  createTime: string | null
}

export interface AdminRefundVO {
  id: number
  orderNo: string
  userId: number
  username: string
  conversationId: string
  amount: number
  status: string
  adminNote: string | null
  createdAt: string
  confirmedAt: string | null
}

export interface AdminOrderDetail extends AdminOrderVO {
  refunds: AdminRefundVO[]
}

export type TicketStatus = 'OPEN' | 'PROCESSING' | 'CLOSED' | 'CANCELLED'

export interface TicketVO {
  id: number
  ticketNo: string
  userId: number
  username: string
  conversationId: string
  kind: 'TRANSFER' | 'END'
  category: string | null
  summary: string | null
  status: TicketStatus
  rating: number | null
  feedback: string | null
  resolved: boolean | null
  assignee: string | null
  closeNote: string | null
  createdAt: string
  closedAt: string | null
}

export interface KnowledgeDocVO {
  id: number
  title: string
  source: 'CLASSPATH' | 'ADMIN'
  enabled: boolean
  chunkCount: number
  contentLength: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeDetail {
  id: number
  title: string
  content: string
  source: string
  enabled: boolean
  chunks: string[]
  updatedAt: string
}

export interface AuditConvVO {
  conversationId: string
  userId: number
  username: string
  messageCount: number
  toolCount: number
  preview: string
  lastActiveAt: string
}

export interface AuditMessageVO {
  id: number
  role: string
  content: string
  toolName: string | null
  createdAt: string
}

export interface RequestLogVO {
  id: number
  conversationId: string
  userId: number
  username: string
  model: string | null
  promptTokens: number
  completionTokens: number
  latencyMs: number
  exception: string | null
  createdAt: string
}

export interface TokenDay {
  date: string
  promptTokens: number
  completionTokens: number
  requests: number
}

export interface TokenStats {
  days: number
  totalPromptTokens: number
  totalCompletionTokens: number
  totalTokens: number
  byDay: TokenDay[]
  byModel: { model: string; requests: number; tokens: number }[]
  byUser: { userId: number; username: string; tokens: number; requests: number }[]
}

export interface LatencyPoint {
  requests: number
  avgMs: number
  p50Ms: number
  p95Ms: number
  maxMs: number
}

export interface LatencyStats {
  days: number
  overall: LatencyPoint
  byDay: (LatencyPoint & { date: string })[]
  byModel: (LatencyPoint & { model: string })[]
  slowest: {
    id: number
    conversationId: string
    username: string
    model: string
    latencyMs: number
    createdAt: string
  }[]
}

export interface SystemInfo {
  status: string
  jvm: {
    heapUsedMb: number
    heapMaxMb: number
    nonHeapUsedMb: number
    threads: number
    uptimeMs: number
    startedAt: string
    javaVersion: string
    pid: number
  }
  db: { status: string; pingMs: number; tables: Record<string, number> }
  rag: { docs: number; enabledDocs: number; chunks: number }
  rateLimit: { limitPerMinute: number; trackedIps: number; hitsLastMinute: number }
  health: { status: string; components: Record<string, string> }
  redisNote: string
  checkedAt: string
}