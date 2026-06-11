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

export async function listAgentSessions() {
  return request('/agent/sessions')
}

export async function getAgentSession(sessionId) {
  return request(`/agent/sessions/${sessionId}`)
}

export async function getAgentTrace(sessionId) {
  return request(`/agent/sessions/${sessionId}/trace`)
}

const DEFAULT_STREAM_TIMEOUT_MS = Number(import.meta.env.VITE_AGENT_STREAM_TIMEOUT_MS || 300000)

/** 从 SSE data 块解析业务 payload（兼容 SseEvent 包装与扁平结构） */
function resolveSsePayload(event) {
  const raw = event?.data
  if (raw == null) return {}
  if (typeof raw !== 'object') return raw
  if (raw.toolName != null || raw.success != null || raw.content != null || raw.message != null) {
    return raw
  }
  if (raw.data != null && typeof raw.data === 'object') {
    return raw.data
  }
  return raw
}

export async function sendAgentMessageStream(sessionId, message, handlers = {}, options = {}) {
  const timeoutMs = Number(options.timeoutMs || DEFAULT_STREAM_TIMEOUT_MS)
  const controller = new AbortController()
  let timeoutId = null
  let terminalEvent = ''
  let abortMessage = ''

  if (Number.isFinite(timeoutMs) && timeoutMs > 0) {
    timeoutId = window.setTimeout(() => {
      abortMessage = `Agent 流式响应超过 ${Math.round(timeoutMs / 1000)} 秒未结束`
      controller.abort(new Error(abortMessage))
    }, timeoutMs)
  }

  if (options.signal) {
    if (options.signal.aborted) {
      abortMessage = options.signal.reason?.message || 'Agent 流式请求已取消'
      controller.abort(options.signal.reason)
    } else {
      options.signal.addEventListener('abort', () => {
        abortMessage = options.signal.reason?.message || 'Agent 流式请求已取消'
        controller.abort(options.signal.reason)
      }, { once: true })
    }
  }

  let reader = null
  let receivedMessage = false
  let streamText = ''
  try {
    const response = await fetch(`${API_BASE_URL}/agent/sessions/${sessionId}/messages/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream, application/json',
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
        const payload = resolveSsePayload(event)
        const eventName = event.eventName || payload?.type || 'message'
        switch (eventName) {
          case 'plan':
            handlers.onPlan?.(payload)
            break
          case 'token': {
            const token = payload?.content || ''
            if (token) {
              streamText += token
            }
            handlers.onToken?.(payload)
            break
          }
          case 'message':
            receivedMessage = true
            if (payload?.content) {
              streamText = payload.content
            }
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
          case 'trace':
            handlers.onTrace?.(payload)
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
      // 后端在 onComplete 中依次发送 message→done 后立即 complete()，
      // 真实 LLM 较长流式场景下，最后的 done 帧偶发会在连接关闭前未被刷新到客户端。
      // 若已收到完整 message 或 token 累积文本，则按正常完成处理，并由 onDone 触发服务端 trace 对账，
      // 避免误报“未收到 done/error 结束事件”。
      if (receivedMessage || streamText) {
        if (!receivedMessage && streamText) {
          handlers.onMessage?.({ content: streamText, degraded: true })
        }
        terminalEvent = 'done'
        handlers.onDone?.({ degraded: true, totalLatencyMs: 0 })
        return
      }
      throw new Error('流式响应未收到 done/error 结束事件')
    }
  } catch (e) {
    if (terminalEvent === 'done') {
      return
    }
    if (e?.name === 'AbortError' || controller.signal.aborted) {
      throw new Error(abortMessage || e?.message || 'Agent 流式响应超时')
    }
    throw e
  } finally {
    if (timeoutId) {
      window.clearTimeout(timeoutId)
    }
    if (reader) {
      try {
        if (controller.signal.aborted) {
          await reader.cancel()
        }
      } catch (e) {
        // 请求已经结束或被浏览器关闭时，无需继续处理。
      } finally {
        reader.releaseLock()
      }
    }
  }
}
