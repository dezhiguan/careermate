import { request } from './http'

export function debugLlmChat(message) {
  return request('/debug/llm/chat', {
    method: 'POST',
    body: JSON.stringify({ message }),
  })
}
