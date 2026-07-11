// Production build: VITE_API_BASE_URL=/api (paths omit /api prefix; Nginx forwards to backend /api/)
// Local dev: base must include /api, e.g. http://localhost:8080/api or /api via Vite proxy
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ||
  (import.meta.env.DEV ? '/api' : 'http://localhost:8080/api')
const TOKEN_KEY = 'careermate_token'
const USER_KEY = 'careermate_user'

let lastTraceMeta = { traceId: null, requestId: null }

function clearAuthState() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

function redirectToLogin() {
  if (window.location.hash !== '#/login') {
    window.location.hash = '/login'
  }
}

function getAuthHeaders(extraHeaders = {}) {
  const token = localStorage.getItem(TOKEN_KEY)
  return {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...extraHeaders,
  }
}

function handleUnauthorized(payload) {
  clearAuthState()
  redirectToLogin()
  throw new Error(friendlyApiMessage(payload?.code, payload?.message || '未认证'))
}

// 静默续期：access token 过期(401)时用 httpOnly refresh cookie 换发新 AT，避免闲置/合盖后被直接踢出。
// 并发的多个 401 只触发一次续期请求。
let refreshPromise = null
function trySilentRefresh() {
  if (!refreshPromise) {
    refreshPromise = fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      credentials: 'include', // 携带 httpOnly refresh cookie
      headers: { 'Content-Type': 'application/json' },
    })
      .then((r) => (r.ok ? r.json() : null))
      .then((p) => {
        const t = p?.data?.token
        if (t) {
          localStorage.setItem(TOKEN_KEY, t)
          return t
        }
        return null
      })
      .catch(() => null)
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

function readTraceHeaders(response) {
  const traceId = response.headers.get('X-Trace-Id')
  const requestId = response.headers.get('X-Request-Id')
  if (traceId || requestId) {
    lastTraceMeta = { traceId, requestId }
  }
  return { traceId, requestId }
}

export function getLastTraceMeta() {
  return { ...lastTraceMeta }
}

export async function requestWithMeta(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...getAuthHeaders(options.headers || {}),
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  })

  const { traceId, requestId } = readTraceHeaders(response)

  let payload = null
  try {
    payload = await response.json()
  } catch (e) {
    payload = null
  }

  if (response.status === 401 || payload?.code === 401) {
    // AT 过期时先静默续期并重试一次；仅在已登录(有token)、非续期端点、且未重试过时尝试
    const hasToken = !!localStorage.getItem(TOKEN_KEY)
    if (hasToken && !options._retried && path !== '/auth/refresh') {
      const newToken = await trySilentRefresh()
      if (newToken) {
        return requestWithMeta(path, { ...options, _retried: true })
      }
    }
    handleUnauthorized(payload)
  }

  if (!response.ok) {
    const error = new Error(friendlyApiMessage(payload?.code, payload?.message || `请求失败: ${response.status}`, response.status))
    error.status = response.status
    error.code = payload?.code
    error.traceId = traceId || payload?.traceId || null
    error.requestId = requestId || null
    error.payload = payload
    throw error
  }

  if (!payload || typeof payload.code === 'undefined') {
    const error = new Error('响应结构不合法')
    error.status = response.status
    error.traceId = traceId || null
    error.requestId = requestId || null
    error.payload = payload
    throw error
  }

  if (payload.code !== 0) {
    const error = new Error(friendlyApiMessage(payload.code, payload.message || '请求失败', response.status))
    error.status = response.status
    error.code = payload.code
    error.traceId = traceId || payload.traceId || null
    error.requestId = requestId || null
    error.payload = payload
    throw error
  }

  return {
    data: payload.data,
    traceId,
    requestId,
  }
}

export async function request(path, options = {}) {
  const { data } = await requestWithMeta(path, options)
  return data
}

function friendlyApiMessage(code, message, status) {
  const text = `${code || ''} ${message || ''}`.toUpperCase()
  if (text.includes('SMS_SEND_TOO_FREQUENT')) return '验证码已发送，请稍后再试'
  if (
    text.includes('SMS_PHONE_DAY_LIMITED') ||
    text.includes('SMS_IP_MINUTE_LIMITED') ||
    text.includes('SMS_PROVIDER_RATE_LIMITED') ||
    text.includes('TOO MANY') ||
    text.includes('LIMIT') ||
    status === 429
  ) {
    return '验证码发送过于频繁，请稍后再试'
  }
  if (text.includes('SMS_CODE_INVALID')) return '验证码错误或已过期，请重新获取'
  if (text.includes('BAD_CREDENTIALS')) return '账号或密码不正确'
  if (text.includes('PLATFORM_ROLE_DENIED')) return '当前账号没有访问权限，请联系管理员开通'
  if (text.includes('PASSWORD_WEAK')) return '密码至少需要 8 位'
  if (text.includes('SMS_PROVIDER') || text.includes('ALIYUN')) return '短信服务暂时不可用，请稍后再试'
  if (message && !/gateway|网关认证失败/i.test(message)) return message
  return '请求失败，请稍后再试'
}

export { API_BASE_URL, TOKEN_KEY, USER_KEY, getAuthHeaders, handleUnauthorized }
