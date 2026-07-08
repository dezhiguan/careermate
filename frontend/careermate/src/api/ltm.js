import { request } from './http'

// A4：长期记忆（"小职认识你这些"）
export async function listLongTermMemory() {
  return request('/profile/long-term-memory')
}

export async function forgetLongTermMemory(id) {
  return request(`/profile/long-term-memory/${id}`, { method: 'DELETE' })
}
