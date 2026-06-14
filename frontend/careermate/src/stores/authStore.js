import { reactive, computed } from 'vue'
import {
  getCurrentUser,
  login as loginApi,
  mobileLogin as mobileLoginApi,
  register as registerApi,
  sendSmsCode,
  updateProfile as updateProfileApi,
} from '../api/auth'
import { TOKEN_KEY, USER_KEY } from '../api/http'

const state = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  currentUser: (() => {
    try {
      const raw = localStorage.getItem(USER_KEY)
      return raw ? JSON.parse(raw) : null
    } catch (e) {
      return null
    }
  })(),
  initialized: false,
  loading: false,
  smsSending: false,
})

function persistUser(user) {
  const normalizedUser = user
    ? {
        ...user,
        // register/login 返回的 user 结构没有 authenticated 字段，这里统一补齐
        authenticated: user.authenticated ?? true,
      }
    : null
  state.currentUser = normalizedUser
  if (normalizedUser) {
    localStorage.setItem(USER_KEY, JSON.stringify(normalizedUser))
  } else {
    localStorage.removeItem(USER_KEY)
  }
}

function persistToken(token) {
  state.token = token || ''
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

function clearAuth() {
  persistToken('')
  persistUser(null)
}

async function fetchCurrentUser() {
  const user = await getCurrentUser()
  persistUser(user)
  return user
}

async function init() {
  if (state.initialized) return
  state.loading = true
  try {
    if (!state.token) {
      clearAuth()
      return
    }
    await fetchCurrentUser()
  } catch (e) {
    clearAuth()
  } finally {
    state.initialized = true
    state.loading = false
  }
}

async function login(username, password) {
  state.loading = true
  try {
    const result = await loginApi({ username, password })
    persistToken(result?.token || '')
    persistUser(result?.user || null)
    return result
  } finally {
    state.loading = false
  }
}

async function register(username, password, email) {
  state.loading = true
  try {
    const result = await registerApi({ username, password, email })
    persistToken(result?.token || '')
    persistUser(result?.user || null)
    return result
  } finally {
    state.loading = false
  }
}

async function sendMobileSmsCode(phone) {
  state.smsSending = true
  try {
    return await sendSmsCode(phone)
  } finally {
    state.smsSending = false
  }
}

async function mobileLogin(phone, verifyCode, challengeId) {
  state.loading = true
  try {
    const result = await mobileLoginApi(phone, verifyCode, challengeId)
    persistToken(result?.token || '')
    persistUser(result?.user || null)
    return result
  } finally {
    state.loading = false
  }
}

function logout() {
  clearAuth()
  state.initialized = false
  window.location.hash = '/login'
}

async function updateProfile(payload) {
  const user = await updateProfileApi(payload)
  persistUser({
    ...state.currentUser,
    ...user,
    authenticated: true,
  })
  return user
}

function applyUserProfile(user) {
  if (!user) return
  persistUser({
    ...state.currentUser,
    ...user,
    authenticated: state.currentUser?.authenticated ?? true,
  })
}

const isAuthenticated = computed(() => !!state.token && !!state.currentUser?.authenticated)

export const authStore = {
  state,
  init,
  login,
  register,
  sendMobileSmsCode,
  mobileLogin,
  logout,
  updateProfile,
  applyUserProfile,
  fetchCurrentUser,
  isAuthenticated: () => isAuthenticated.value,
}
