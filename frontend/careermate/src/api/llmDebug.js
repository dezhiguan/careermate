import { request } from './http'

export function debugLlmChat(message) {
  return request('/api/debug/llm/chat', {
    method: 'POST',
    body: JSON.stringify({ message }),
  })
}
