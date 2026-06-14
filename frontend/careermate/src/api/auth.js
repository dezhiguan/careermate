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

export function mobileLogin(phone, verifyCode, outId = null, scene = 'mobile_login') {
  return request('/auth/mobile/login', {
    method: 'POST',
    body: JSON.stringify({ phone, verifyCode, outId, scene }),
  })
}
