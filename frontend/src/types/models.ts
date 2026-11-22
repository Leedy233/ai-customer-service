/** 统一返回体（与后端 common/ApiResponse 对齐） */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 登录成功 data（v1：POST /ai/login） */
export interface LoginResult {
  userId: number
  username: string
}

/** 当前用户（GET /ai/me；phone/email 为服务端脱敏真值，tags 为真标签） */
export interface UserProfile {
  userId: number
  username: string
  role?: 'USER' | 'ADMIN'
  phone?: string | null
  email?: string | null
  tags?: string[]
}

/** 退款申请成功 data（POST /ai/refund/apply） */
export interface RefundApplyResult {
  success: boolean
  refundId: number
  orderNo: string
  amount: number
  message: string
}

/** 退款确认成功 data（POST /ai/refund/confirm） */
export interface RefundConfirmResult {
  success: boolean
  refundId: number
  orderNo: string
  message: string
}

/**
 * 订单卡片数据。
 * v1 阶段来源：AI 回答文本结构化提取（真实后端经模型返回的数据）；
 * 待后端 GET /ai/orders/{orderNo} 与 SSE v2 card 事件上线后切换为结构化来源。
 */
export interface OrderCardData {
  orderNo: string
  productName?: string
  amount?: string
  status?: string
}

export type MessageRole = 'user' | 'assistant' | 'notice' | 'tool'

/** 答案溯源（知识库原文片段；后端溯源字段待补充，当前为示例内容） */
export interface Citation {
  doc: string
  section: string
  excerpt: string
}

/** 聊天消息视图模型 */
export interface MessageVM {
  id: string
  role: MessageRole
  content: string
  streaming?: boolean
  stopped?: boolean
  error?: string
  card?: OrderCardData
  createdAt: number
  /** 用户消息发送状态 */
  status?: 'sending' | 'sent' | 'failed'
  /** 工具动作条字段 */
  toolName?: string
  toolDurationMs?: number
  /** 工具真入参（SSE v2 / 服务端落库真值） */
  toolArgs?: Record<string, string>
  /** 知识库溯源 */
  citation?: Citation
  /** 真溯源列表（服务端 sources 字段；缺失时 citation 为兜底） */
  sources?: Citation[]
}

/** 本地会话注册表条目（与服务端会话列表合并展示） */
export interface LocalConversation {
  id: string
  title: string
  createdAt: number
  updatedAt: number
  /** 曾出现在服务端列表（真删除后服务端缺席时本地同步清除的依据） */
  fromServer?: boolean
}

/** 服务端会话摘要（GET /ai/conversations；summary 为服务端真摘要） */
export interface ServerConversation {
  id: string
  title: string
  lastActiveAt: string
  summary?: string | null
}

/** 服务端历史消息（GET /ai/conversations/{id}/messages） */
export interface ServerMessage {
  id: number
  role: 'user' | 'assistant' | 'tool'
  content: string
  toolName?: string | null
  toolArgs?: string | null
  toolDurationMs?: number | null
  sources?: string | null
  createdAt: string
}

/** 订单权威数据（GET /ai/orders/{orderNo} 与 GET /ai/orders，DB 真值） */
export interface OrderVO {
  id: number
  orderNo: string
  userId: number
  productName: string
  amount: number
  status: string
  createTime: string
  shippedAt?: string | null
  eta?: string | null
}

/** 排队真值（GET /ai/queue/status） */
export interface QueueStatus {
  active: boolean
  position: number
  estimatedWaitSeconds: number
  joinedAt?: string
}