// Production build: VITE_API_BASE_URL=/careermate-api (paths omit /api; Nginx adds it)
// Local dev: base must include /api, e.g. http://localhost:8080/api or /api via Vite proxy
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ||
  (import.meta.env.DEV ? '/api' : 'http://localhost:8080/api')
const TOKEN_KEY = 'careermate_token'
const USER_KEY = 'careermate_user'

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
  throw new Error(payload?.message || '未认证')
}

export async function request(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...getAuthHeaders(options.headers || {}),
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
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

  if (!response.ok) {
    throw new Error(payload?.message || `请求失败: ${response.status}`)
  }

  if (!payload || typeof payload.code === 'undefined') {
    throw new Error('响应结构不合法')
  }

  if (payload.code !== 0) {
    throw new Error(payload.message || '请求失败')
  }

  return payload.data
}

export { API_BASE_URL, TOKEN_KEY, USER_KEY, getAuthHeaders, handleUnauthorized }
