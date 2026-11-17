import { ref } from 'vue'
import { ApiError, request } from './client'
export interface User { id: string; account: string; nickname: string; contact: string; volunteerState: string; roles: string[]; version: number }
export interface Application { id: string; userId: string; nickname: string; material: string; state: string; reason: string; reviewerId: string | null; version: number; createdAt: string; reviewedAt: string | null }
export interface Page<T> { items: T[]; total: number; page: number; pageSize: number }
export interface ResponsibilityReport { openResponsibilities: { type: string; objectId: string; description: string; path: string }[]; unavailableModules: string[] }
export const currentUser = ref<User | null>(null)
export const hasRole = (role: string) => currentUser.value?.roles.includes(role) || false
export async function refreshMe() {
  try { currentUser.value = (await request<User>('/accounts/me')).data }
  catch (error) { if (error instanceof ApiError && error.status === 401) currentUser.value = null; else throw error }
  return currentUser.value
}
if (typeof window !== 'undefined') window.addEventListener('sar:session-expired', () => { currentUser.value = null })
export const post = <T>(path: string, data: unknown) => request<T>(path, { method: 'POST', body: JSON.stringify(data) })
export async function signOut() { await request('/sessions/current', { method: 'DELETE' }); currentUser.value = null }
export const stateLabel = (value: string) => ({ NONE: '未取得资格', ACTIVE: '资格有效', SUSPENDED: '已暂停', PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[value] || value)
export const roleLabel = (value: string) => ({ USER: '普通用户', VOLUNTEER: '志愿者', SYSTEM_ADMIN: '系统管理员', RESCUE_ADMIN: '救助管理员', ADOPTION_ADMIN: '领养管理员' }[value] || value)
export const errorText = (error: unknown) => error instanceof ApiError ? `${error.message}${error.requestId ? `（请求号：${error.requestId}）` : ''}` : '操作失败，请稍后重试'
