import { API_BASE_URL, getAuthHeaders, handleUnauthorized, request } from './http'
import { createSseParser } from '../utils/sseParser'

export async function createAgentSession() {
  const response = await fetch(`${API_BASE_URL}/api/agent/sessions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...getAuthHeaders(),
    },
  })

  let payload = null
  try {
    payload = await response.json()
  } catch (e) {
    payload = null
  }

  if (response.status === 401 || payload?.code === 401) {
    handleUnauthorized(payload)
  }

  if (!response.ok || payload?.code !== 0) {
    throw new Error(payload?.message || `创建会话失败: ${response.status}`)
  }

  return payload.data?.sessionId
}

export async function getAgentSession(sessionId) {
  return request(`/api/agent/sessions/${sessionId}`)
}

export async function getAgentTrace(sessionId) {
  return request(`/api/agent/sessions/${sessionId}/trace`)
}

export async function sendAgentMessageStream(sessionId, message, handlers = {}) {
  const response = await fetch(`${API_BASE_URL}/api/agent/sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ message }),
  })

  if (response.status === 401) {
    handleUnauthorized()
  }
  if (!response.ok) {
    let errBody = null
    try {
      errBody = await response.json()
    } catch (e) {
      errBody = null
    }
    throw new Error(errBody?.message || `流式请求失败: ${response.status}`)
  }
  if (!response.body) {
    throw new Error('SSE 响应流为空')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  const parser = createSseParser({
    onEvent: (event) => {
      handlers.onRawEvent?.(event)
      const payload = event?.data?.data ?? event?.data ?? {}
      switch (event.eventName) {
        case 'plan':
          handlers.onPlan?.(payload)
          break
        case 'token':
          handlers.onToken?.(payload)
          break
        case 'message':
          handlers.onMessage?.(payload)
          break
        case 'done':
          handlers.onDone?.(payload)
          break
        case 'error':
          handlers.onError?.(new Error(payload?.message || '流式执行失败'))
          break
        case 'heartbeat':
          handlers.onHeartbeat?.(payload)
          break
        default:
          break
      }
    },
    onError: (e) => handlers.onError?.(e),
  })

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      parser.push(decoder.decode(value, { stream: true }))
    }
    parser.end()
  } finally {
    reader.releaseLock()
  }
}
