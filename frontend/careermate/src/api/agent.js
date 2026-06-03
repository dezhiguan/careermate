import { API_BASE_URL, getAuthHeaders, handleUnauthorized, request } from './http'
import { createSseParser } from '../utils/sseParser'

export async function createAgentSession() {
  const response = await fetch(`${API_BASE_URL}/agent/sessions`, {
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
  return request(`/agent/sessions/${sessionId}`)
}

export async function getAgentTrace(sessionId) {
  return request(`/agent/sessions/${sessionId}/trace`)
}

const DEFAULT_STREAM_TIMEOUT_MS = Number(import.meta.env.VITE_AGENT_STREAM_TIMEOUT_MS || 120000)

export async function sendAgentMessageStream(sessionId, message, handlers = {}, options = {}) {
  const timeoutMs = Number(options.timeoutMs || DEFAULT_STREAM_TIMEOUT_MS)
  const controller = new AbortController()
  let timeoutId = null
  let terminalEvent = ''

  if (Number.isFinite(timeoutMs) && timeoutMs > 0) {
    timeoutId = window.setTimeout(() => {
      controller.abort(new Error(`Agent 流式响应超过 ${Math.round(timeoutMs / 1000)} 秒未结束`))
    }, timeoutMs)
  }

  if (options.signal) {
    if (options.signal.aborted) {
      controller.abort(options.signal.reason)
    } else {
      options.signal.addEventListener('abort', () => controller.abort(options.signal.reason), { once: true })
    }
  }

  let reader = null
  try {
    const response = await fetch(`${API_BASE_URL}/agent/sessions/${sessionId}/messages/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ message }),
      signal: controller.signal,
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

    reader = response.body.getReader()
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
            terminalEvent = 'done'
            handlers.onDone?.(payload)
            break
          case 'error':
            terminalEvent = 'error'
            handlers.onError?.(new Error(payload?.message || '流式执行失败'))
            break
          case 'heartbeat':
            handlers.onHeartbeat?.(payload)
            break
          case 'tool_start':
            handlers.onToolStart?.(payload)
            break
          case 'tool_result':
            handlers.onToolResult?.(payload)
            break
          default:
            break
        }
      },
      onError: (e) => handlers.onError?.(e),
    })

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      parser.push(decoder.decode(value, { stream: true }))
    }
    parser.end()
    if (!terminalEvent) {
      throw new Error('流式响应未收到 done/error 结束事件')
    }
  } catch (e) {
    if (terminalEvent === 'done') {
      return
    }
    if (e?.name === 'AbortError' || controller.signal.aborted) {
      throw new Error(e?.message || 'Agent 流式响应超时')
    }
    throw e
  } finally {
    if (timeoutId) {
      window.clearTimeout(timeoutId)
    }
    reader?.releaseLock()
  }
}
