import { request } from './http'

export function register(payload) {
  return request('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function login(payload) {
  return request('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getCurrentUser() {
  return request('/auth/me', {
    method: 'GET',
  })
}

export function updateProfile(payload) {
  return request('/auth/me', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function sendSmsCode(phone, scene = 'mobile_login') {
  return request('/auth/sms/send', {
    method: 'POST',
    body: JSON.stringify({ phone, scene }),
  })
}

export function mobileLogin(phone, verifyCode, challengeId, scene = 'mobile_login', rememberMe = false) {
  return request('/auth/mobile/login', {
    method: 'POST',
    body: JSON.stringify({ phone, verifyCode, challengeId, scene, rememberMe }),
  })
}

export function sendPasswordResetSms(phone) {
  return request('/auth/password-reset/sms/send', {
    method: 'POST',
    body: JSON.stringify({ phone }),
  })
}

export function confirmPasswordReset({ phone, verifyCode, challengeId, newPassword }) {
  return request('/auth/password-reset/confirm', {
    method: 'POST',
    body: JSON.stringify({ phone, verifyCode, challengeId, newPassword }),
  })
}

export function logout() {
  return request('/auth/logout', { method: 'POST' })
}

export function logoutAll() {
  return request('/auth/logout-all', { method: 'POST' })
}

// ─── 账号设置 ────────────────────────────────────────────────────────────────

export function bindEmail(email) {
  return request('/user/settings/email/bind', {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function updateUsername({ username, verifyCode, challengeId }) {
  return request('/user/settings/username', {
    method: 'PUT',
    body: JSON.stringify({ username, verifyCode, challengeId }),
  })
}

export function setPassword({ newPassword, verifyCode, challengeId }) {
  return request('/user/settings/password', {
    method: 'POST',
    body: JSON.stringify({ newPassword, verifyCode, challengeId }),
  })
}

export function verifyOldPhone({ verifyCode, challengeId }) {
  return request('/user/settings/phone/verify-old', {
    method: 'POST',
    body: JSON.stringify({ verifyCode, challengeId }),
  })
}

export function bindNewPhone({ newPhone, verifyCode, challengeId, oldPhoneTicket }) {
  return request('/user/settings/phone/bind-new', {
    method: 'POST',
    body: JSON.stringify({ newPhone, verifyCode, challengeId, oldPhoneTicket }),
  })
}

// ─── 账号注销 ────────────────────────────────────────────────────────────────

export function cancelAccount({ verifyCode, challengeId, confirmText }) {
  return request('/user/account/cancel', {
    method: 'POST',
    body: JSON.stringify({ verifyCode, challengeId, confirmText }),
  })
}

export function revokeCancellation() {
  return request('/user/account/cancel/revoke', { method: 'POST' })
}

// ─── 会话管理 ────────────────────────────────────────────────────────────────

export function listSessions() {
  return request('/user/sessions', { method: 'GET' })
}

export function revokeSession(sessionId) {
  return request(`/user/sessions/${sessionId}`, { method: 'DELETE' })
}

export function revokeAllOtherSessions() {
  return request('/user/sessions', { method: 'DELETE' })
}

// ─── Onboarding ─────────────────────────────────────────────────────────────

export function completeOnboarding() {
  return request('/user/onboarding/complete', { method: 'POST' })
}

// ─── 协议同意 ────────────────────────────────────────────────────────────────

export function agreeTerms(version = 'v1') {
  return request(`/user/terms/agree?version=${version}`, { method: 'POST' })
}
