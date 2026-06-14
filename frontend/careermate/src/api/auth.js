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

export function mobileLogin(phone, verifyCode, challengeId, scene = 'mobile_login') {
  return request('/auth/mobile/login', {
    method: 'POST',
    body: JSON.stringify({ phone, verifyCode, challengeId, scene }),
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
