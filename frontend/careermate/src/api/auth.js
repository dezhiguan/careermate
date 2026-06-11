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
