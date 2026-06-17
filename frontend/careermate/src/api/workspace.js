import { API_BASE_URL, getAuthHeaders, request } from './http'
import { createSseParser } from '../utils/sseParser'

export const LAST_WORKSPACE_CREATE_KEY = 'careermate_last_workspace_create'

/**
 * 统一解析 POST /workspace 响应（兼容 request 已解包 data 与完整 ApiResponse）。
 * @returns {{ workspaceId: string, redirectPath: string }}
 */
export function resolveWorkspaceRedirect(resp) {
  const data = resp?.data ?? resp
  const workspaceId = data?.workspaceId ? String(data.workspaceId).trim() : ''
  const redirectPath = data?.redirectPath ? String(data.redirectPath).trim() : ''
  if (redirectPath.startsWith('/chat/')) {
    const wsFromPath = redirectPath.slice('/chat/'.length).split('/')[0]?.split('?')[0] || ''
    return {
      workspaceId: workspaceId || wsFromPath,
      redirectPath,
    }
  }
  if (workspaceId) {
    return { workspaceId, redirectPath: `/chat/${workspaceId}` }
  }
  return { workspaceId: '', redirectPath: '' }
}

/** 创建工作空间后跳转到 AI 小职，使用命名路由确保 wsId 参数正确。 */
export async function navigateToWorkspace(router, resp) {
  const { workspaceId, redirectPath } = resolveWorkspaceRedirect(resp)
  const wsId = workspaceId || redirectPath.match(/^\/chat\/([^/?#]+)/)?.[1] || ''
  if (!wsId || !wsId.startsWith('WS-')) {
    throw new Error('创建工作空间失败')
  }
  try {
    sessionStorage.setItem(LAST_WORKSPACE_CREATE_KEY, wsId)
  } catch {
    // sessionStorage 不可用时忽略
  }
  await router.push({ name: 'chat-workspace', params: { wsId } })
}

export async function createWorkspace(payload) {
  return request('/workspace', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

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

export async function postAction(sessionId, action, payload) {
  const body = { action, payload }
  if (payload != null && typeof payload === 'object' && !Array.isArray(payload)) {
    body.payload = JSON.stringify(payload)
  }
  return request(`/workspace/${encodeURIComponent(sessionId)}/action`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

/** 打开简历生成 SSE 流（必须带后端返回的 sseEndpoint，含 pendingActionId） */
export function openResumeGenerateStreamByEndpoint(sseEndpoint, handlers = {}) {
  const path = normalizeSseEndpointPath(sseEndpoint)
  const url = `${API_BASE_URL}${path}`
  return openResumeGenerateStreamUrl(url, handlers)
}

function normalizeSseEndpointPath(sseEndpoint) {
  const rawPath = sseEndpoint?.startsWith('/') ? sseEndpoint : `/${sseEndpoint || ''}`
  if (API_BASE_URL.endsWith('/api') && rawPath.startsWith('/api/')) {
    return rawPath.slice('/api'.length)
  }
  return rawPath
}

function openResumeGenerateStreamUrl(url, handlers = {}) {
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
