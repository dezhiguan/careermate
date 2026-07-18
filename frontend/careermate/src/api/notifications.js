import { request } from './http'

export function getNotificationPrefs() {
  return request('/user/notification-preferences')
}

export function saveNotificationPrefs(prefs) {
  return request('/user/notification-preferences', {
    method: 'PUT',
    body: JSON.stringify(prefs),
  })
}
