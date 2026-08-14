import { reactive, computed } from 'vue'
import {
  agreeTerms as agreeTermsApi,
  completeOnboarding as completeOnboardingApi,
  confirmPasswordReset as confirmPasswordResetApi,
  getCurrentUser,
  login as loginApi,
  logout as logoutApi,
  logoutAll as logoutAllApi,
  mobileLogin as mobileLoginApi,
  register as registerApi,
  sendPasswordResetSms as sendPasswordResetSmsApi,
  sendSmsCode,
  updateProfile as updateProfileApi,
} from '../api/auth'
import { TOKEN_KEY, USER_KEY } from '../api/http'

const BROADCAST_CHANNEL_NAME = 'careermate_auth'
let bc = null
try {
  bc = new BroadcastChannel(BROADCAST_CHANNEL_NAME)
} catch (e) {
  // BroadcastChannel not supported
}

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
  onboardingCompleted: true,
  termsAgreed: false,
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
    if (state.currentUser?.authenticated) {
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

async function login(username, password, { rememberMe = false, captcha = '', captchaChallengeId = '' } = {}) {
  state.loading = true
  try {
    const result = await loginApi({ account: username, password, rememberMe, captcha, captchaChallengeId })
    persistToken(result?.token || '')
    persistUser(result?.user || null)
    state.onboardingCompleted = result?.onboardingCompleted !== false
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
  return sendSmsCode(phone)
}

async function sendPasswordResetSms(phone) {
  return sendPasswordResetSmsApi(phone)
}

async function resetPassword(payload) {
  state.loading = true
  try {
    return await confirmPasswordResetApi(payload)
  } finally {
    state.loading = false
  }
}

async function mobileLogin(phone, verifyCode, challengeId, { rememberMe = false } = {}) {
  state.loading = true
  try {
    const result = await mobileLoginApi(phone, verifyCode, challengeId, 'mobile_login', rememberMe)
    persistToken(result?.token || '')
    persistUser(result?.user || null)
    state.onboardingCompleted = result?.onboardingCompleted !== false
    return result
  } finally {
    state.loading = false
  }
}

async function logout() {
  try {
    await logoutApi()
  } catch (e) {
    // best-effort; clear local state regardless
  }
  clearAuth()
  state.initialized = false
  if (bc) bc.postMessage({ type: 'logout' })
  window.location.hash = '/login'
}

async function logoutAll() {
  try {
    await logoutAllApi()
  } catch (e) {
    // best-effort
  }
  clearAuth()
  state.initialized = false
  if (bc) bc.postMessage({ type: 'logout' })
  window.location.hash = '/login'
}

async function markOnboardingComplete() {
  try {
    await completeOnboardingApi()
  } catch (e) {
    // best-effort
  }
  state.onboardingCompleted = true
}

async function agreeTerms() {
  await agreeTermsApi('v1')
  state.termsAgreed = true
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
  const merged = {
    ...state.currentUser,
    ...user,
    authenticated: state.currentUser?.authenticated ?? true,
  }
  // /home/bootstrap 不再返回头像（内嵌 base64 约 300KB，与 /auth/me 重复下发一遍）。
  // 它比 /auth/me 后到，直接展开会把已经拿到的头像覆盖成空，所以缺省时保留原值。
  if (user.avatarUrl == null && state.currentUser?.avatarUrl) {
    merged.avatarUrl = state.currentUser.avatarUrl
  }
  persistUser(merged)
}

const isAuthenticated = computed(() => !!state.token && !!state.currentUser?.authenticated)

// 跨 tab 同步登出
if (bc) {
  bc.onmessage = (event) => {
    if (event.data?.type === 'logout') {
      clearAuth()
      state.initialized = false
      window.location.hash = '/login'
    }
  }
}

export const authStore = {
  state,
  init,
  clearAuth,
  login,
  register,
  sendMobileSmsCode,
  sendPasswordResetSms,
  resetPassword,
  mobileLogin,
  logout,
  logoutAll,
  updateProfile,
  applyUserProfile,
  fetchCurrentUser,
  markOnboardingComplete,
  agreeTerms,
  isAuthenticated: () => isAuthenticated.value,
}
