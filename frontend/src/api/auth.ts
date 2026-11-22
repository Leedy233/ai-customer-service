import { get, post } from './http'
import type { LoginResult, UserProfile } from '@/types/models'

/**
 * v1 契约：POST /ai/login 参数为 query（后端现状，未改动）。
 * 后端审查阶段一改 JSON body 后，仅需把 params 换成 data。
 */
export function login(username: string, password: string): Promise<LoginResult> {
  return post<LoginResult>('/login', null, { params: { username, password } })
}

export function logout(): Promise<string> {
  return post<string>('/logout')
}

export function me(): Promise<UserProfile> {
  return get<UserProfile>('/me')
}