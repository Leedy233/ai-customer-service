import { get, post } from '@/api/http'
import instance from '@/api/http'
import type { KnowledgeDetail, KnowledgeDocVO } from '@/types/admin'

/** 管理端 RAG 知识库：变更实时同步向量库 */
export function list(): Promise<KnowledgeDocVO[]> {
  return get<KnowledgeDocVO[]>('/admin/knowledge')
}

export function detail(id: number): Promise<KnowledgeDetail> {
  return get<KnowledgeDetail>(`/admin/knowledge/${id}`)
}

export function create(body: { title: string; content: string }): Promise<{ id: number }> {
  return post<{ id: number }>('/admin/knowledge', body)
}

export function update(id: number, body: { title: string; content: string }): Promise<{ id: number }> {
  return instance.put(`/admin/knowledge/${id}`, body) as Promise<{ id: number }>
}

export function toggle(id: number): Promise<{ id: number; enabled: boolean }> {
  return post(`/admin/knowledge/${id}/toggle`)
}

export function remove(id: number): Promise<{ id: number; deleted: boolean }> {
  return instance.delete(`/admin/knowledge/${id}`) as Promise<{ id: number; deleted: boolean }>
}