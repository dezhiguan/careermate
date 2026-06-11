import { API_BASE_URL, getAuthHeaders, request } from './http'
import { createSseParser } from '../utils/sseParser'

export async function getWorkspace(sessionId) {
  return request(`/workspace/${encodeURIComponent(sessionId)}`, { method: 'GET' })
}

export async function getMessages(sessionId, opts = {}) {
  const query = new URLSearchParams()
  if (opts.after != null) query.set('after', String(opts.after))
  if (opts.limit != null) query.set('limit', String(opts.limit))
  const qs = query.toString()
  return request(`/workspace/${encodeURIComponent(sessionId)}/messages${qs ? `?${qs}` : ''}`, {
    method: 'GET',
  })
}

export async function postAction(sessionId, action, payload) {
  return request(`/workspace/${encodeURIComponent(sessionId)}/action`, {
    method: 'POST',
    body: JSON.stringify({ action, payload }),
  })
}

function resolveSsePayload(event) {
  const raw = event?.data
  if (raw == null) return {}
  if (typeof raw !== 'object') return raw
  if (raw.delta != null || raw.content != null || raw.card != null || raw.message != null) {
    return raw
  }
  if (raw.data != null && typeof raw.data === 'object') {
    return raw.data
  }
  return raw
}

/** 打开简历生成 SSE 流，返回 { close, abort } */
export function openResumeGenerateStream(sessionId, handlers = {}) {
  const url = `${API_BASE_URL}/workspace/${encodeURIComponent(sessionId)}/generate-resume/stream`
  const controller = new AbortController()
  let closed = false

  async function run() {
    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: getAuthHeaders({ Accept: 'text/event-stream' }),
        signal: controller.signal,
      })
      if (!response.ok) {
        throw new Error(`SSE 连接失败: ${response.status}`)
      }
      const parser = createSseParser({
        onEvent: (event) => {
          const payload = resolveSsePayload(event)
          const name = event.eventName
          if (name === 'token') {
            handlers.onResumeDelta?.(payload.delta || payload.content || '')
          } else if (name === 'ui_action') {
            handlers.onCard?.(payload.card || payload)
          } else if (name === 'error') {
            handlers.onError?.(payload.message || '生成失败')
          } else if (name === 'done') {
            handlers.onDone?.(payload)
            close()
          }
        },
        onError: (err) => handlers.onError?.(err?.message || 'SSE 解析失败'),
      })

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        parser.push(decoder.decode(value, { stream: true }))
      }
      parser.end()
      handlers.onDone?.({})
    } catch (e) {
      if (!controller.signal.aborted) {
        handlers.onError?.(e?.message || 'SSE 连接中断')
      }
    } finally {
      closed = true
    }
  }

  function close() {
    if (!controller.signal.aborted) {
      controller.abort()
    }
  }

  run()
  return { close, get closed() { return closed } }
}
